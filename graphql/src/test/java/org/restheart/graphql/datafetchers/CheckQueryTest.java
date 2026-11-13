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
package org.restheart.graphql.datafetchers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.security.AggregationPipelineSecurityChecker;

/**
 * The find and sort of a query mapping, with their arguments bound, are checked against the
 * aggregationSecurity blacklists, as the aggregation mappings are: with check-operators off a
 * BsonDocument argument may carry operators, and $where must not reach MongoDB from a find.
 */
public class CheckQueryTest {
    /** The aggregationSecurity of the default configuration. */
    private static final AggregationPipelineSecurityChecker DEFAULT_CHECKER = new AggregationPipelineSecurityChecker(Map.of(
            "enabled", true,
            "stageBlacklist", List.of("$out", "$merge", "$lookup", "$graphLookup", "$unionWith"),
            "operatorBlacklist", List.of("$where", "$function", "$accumulator"),
            "allowCrossDatabaseOperations", false,
            "allowJavaScriptExecution", false));

    private static BsonDocument query(String find, String sort) {
        var q = new BsonDocument();
        if (find != null) {
            q.put("find", BsonDocument.parse(find));
        }
        if (sort != null) {
            q.put("sort", BsonDocument.parse(sort));
        }
        return q;
    }

    @Test
    public void whereInFindIsRefused() {
        var q = query("{ \"fsType\": \"Dataset\", \"$where\": \"true\" }", null);
        var e = assertThrows(RuntimeException.class, () -> GraphQLDataFetcher.checkQuery(DEFAULT_CHECKER, q, "db"));
        assertTrue(e.getMessage().contains("$where"), e.getMessage());
    }

    @Test
    public void whereNestedInFindIsRefused() {
        var q = query("{ \"$and\": [ { \"fsType\": \"Dataset\" }, { \"$or\": [ { \"$where\": \"true\" } ] } ] }", null);
        assertThrows(RuntimeException.class, () -> GraphQLDataFetcher.checkQuery(DEFAULT_CHECKER, q, "db"));
    }

    @Test
    public void functionInExprIsRefused() {
        var q = query("{ \"$expr\": { \"$function\": { \"body\": \"function() { return true }\", \"args\": [], \"lang\": \"js\" } } }", null);
        assertThrows(RuntimeException.class, () -> GraphQLDataFetcher.checkQuery(DEFAULT_CHECKER, q, "db"));
    }

    @Test
    public void functionInSortIsRefused() {
        var q = query(null, "{ \"score\": { \"$function\": { \"body\": \"function() { return 1 }\", \"args\": [], \"lang\": \"js\" } } }");
        assertThrows(RuntimeException.class, () -> GraphQLDataFetcher.checkQuery(DEFAULT_CHECKER, q, "db"));
    }

    @Test
    public void queryOperatorsPass() {
        var q = query("{ \"fsType\": \"Dataset\", \"locale.identifier\": { \"$in\": [\"EN\", \"DE\"] }, \"name\": { \"$regex\": \"^a\" } }", "{ \"name\": 1 }");
        assertDoesNotThrow(() -> GraphQLDataFetcher.checkQuery(DEFAULT_CHECKER, q, "db"));
    }

    @Test
    public void noFindNoSortPasses() {
        assertDoesNotThrow(() -> GraphQLDataFetcher.checkQuery(DEFAULT_CHECKER, new BsonDocument(), "db"));
    }

    @Test
    public void disabledCheckerChecksNothing() {
        var disabled = new AggregationPipelineSecurityChecker(Map.of("enabled", false));
        var q = query("{ \"$where\": \"true\" }", null);
        assertDoesNotThrow(() -> GraphQLDataFetcher.checkQuery(disabled, q, "db"));
        assertDoesNotThrow(() -> GraphQLDataFetcher.checkQuery(null, q, "db"));
    }
}
