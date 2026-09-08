/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.graphql.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

/**
 * The catalog gives an agent each query's arguments and the <em>name</em> of the type it returns,
 * never that type's fields — so on its own it is not enough to write a selection set, and GraphQL
 * has no {@code SELECT *}. What closes the gap is telling the agent to introspect, next to the
 * type names that prompt the question and as examples it can run.
 */
public class GraphqlAppIntrospectionHintTest {

    private static final BsonDocument MCP = BsonDocument.parse("{ \"description\": \"Library.\" }");

    private static final String SDL = "type Query { booksByAuthor(author: String!): [Book] } type Book { title: String }";

    private static org.restheart.plugins.mcp.McpResource app() {
        return GraphqlAppMcpResourceBuilder.build("https://host/graphql/library", MCP, SDL).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> operations() {
        return (Map<String, Object>) app().extra().get("operations");
    }

    @Test
    public void theCatalogStillOnlyNamesTheReturnType() {
        // the reason the hint has to exist: this is all an agent gets about what comes back
        @SuppressWarnings("unchecked")
        var queries = (List<Map<String, Object>>) operations().get("queries");

        assertEquals("[Book]", queries.get(0).get("return_type"));
        assertTrue(!queries.get(0).containsKey("fields"), "the catalog does not carry the type's fields");
    }

    @Test
    public void theHintSitsBesideTheTypeNamesItExplains() {
        var hint = String.valueOf(operations().get("resolve_types_with"));

        assertTrue(hint.contains("__type"), hint);
        // it must say which request to make, not merely that introspection exists
        assertTrue(hint.contains("fields"), hint);
    }

    @Test
    public void theHintSaysTheIntrospectedSchemaIsTheCallersOwn() {
        // with @visible a hidden field is absent from the schema that caller introspects, so what
        // introspection lists is exactly what they may select — an agent needs to know it can rely
        // on that rather than second-guessing
        assertTrue(String.valueOf(operations().get("resolve_types_with")).contains("roles"));
    }

    @Test
    public void twoRunnableIntrospectionExamplesComeFirst() {
        var examples = app().examples();

        assertTrue(examples.size() >= 2, "expected the two introspection examples");
        assertEquals("execute", examples.get(0).action());
        assertTrue(String.valueOf(examples.get(0).args()).contains("__schema"), examples.get(0).args().toString());
        assertTrue(String.valueOf(examples.get(1).args()).contains("__type"), examples.get(1).args().toString());
    }

    @Test
    public void theExamplesNestTheDocumentUnderBody() {
        // how_to_call reads a body-taking action's body from args.body; an example that skipped
        // the wrapper would fail body-schema validation, which is a mistake operators have made
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) app().examples().get(0).args().get("body");

        assertTrue(body.get("query").toString().startsWith("{ __schema"), body.toString());
    }

    @Test
    public void theShippedExampleDocumentsAreValidGraphQL() {
        // an agent copies these verbatim; a typo here is a request that fails for everyone
        var parser = new graphql.parser.Parser();

        app().examples().stream()
                .limit(2)
                .map(ex -> ((Map<?, ?>) ex.args().get("body")).get("query").toString())
                .forEach(parser::parseDocument);
    }

    @Test
    public void theHintsOwnExampleQueryIsValidGraphQL() {
        // the hint carries a query shape too, with a TypeName placeholder that must still parse
        var hint = String.valueOf(operations().get("resolve_types_with"));
        var query = hint.substring(hint.indexOf("{ __type"), hint.lastIndexOf("}") + 1);

        new graphql.parser.Parser().parseDocument(query);
    }

    @Test
    public void anOperatorsOwnExamplesSurviveAlongsideThem() {
        var mcp = BsonDocument.parse("""
                { "description": "Library.",
                  "examples": [ { "description": "mine", "args": { "body": { "query": "{ booksByAuthor(author: \\"x\\") { title } }" } } } ] }
                """);

        var examples = GraphqlAppMcpResourceBuilder.build("https://host/graphql/library", mcp, SDL).orElseThrow().examples();

        assertEquals(3, examples.size());
        assertEquals("mine", examples.get(2).description());
    }
}
