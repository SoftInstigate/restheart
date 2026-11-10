/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.MongoRequest;

/**
 * {@code $ifvar} and {@code $var} with a default in a permission's filters, resolved over the ACL
 * variables (since 9.16).
 */
public class AclVarsConditionalsTest {

    private static MongoRequest requestWith(Map<String, String> qparams) {
        var request = mock(MongoRequest.class);
        var exchange = mock(io.undertow.server.HttpServerExchange.class);
        var queryParams = new HashMap<String, Deque<String>>();
        qparams.forEach((k, v) -> {
            var d = new ArrayDeque<String>();
            d.add(v);
            queryParams.put(k, d);
        });
        when(request.getExchange()).thenReturn(exchange);
        when(exchange.getQueryParameters()).thenReturn(queryParams);
        return request;
    }

    private static final BsonDocument BY_CATEGORY = BsonDocument.parse("""
            {"$and": [
              {"published": true},
              {"$ifvar": ["@qparams['category']", {"category": "@qparams['category']"}]}
            ]}""");

    @Test
    public void anIfvarWithItsVariableBoundAddsItsCondition() {
        var filter = AclVarsInterpolator.interpolateFilter(requestWith(Map.of("category", "desk")), BY_CATEGORY);

        assertEquals(BsonDocument.parse("{\"$and\": [{\"published\": true}, {\"category\": \"desk\"}]}"), filter);
    }

    @Test
    public void anIfvarWithItsVariableUnboundIsRemoved() {
        var filter = AclVarsInterpolator.interpolateFilter(requestWith(Map.of()), BY_CATEGORY);

        assertEquals(BsonDocument.parse("{\"$and\": [{\"published\": true}]}"), filter);
    }

    @Test
    public void anIfvarWithAnElseAppliesIt() {
        var clause = BsonDocument.parse("""
                {"$ifvar": ["@qparams['status']", {"status": "@qparams['status']"}, {"status": "open"}]}""");

        assertEquals(BsonDocument.parse("{\"status\": \"open\"}"),
                AclVarsInterpolator.interpolateFilter(requestWith(Map.of()), clause));
        assertEquals(BsonDocument.parse("{\"status\": \"closed\"}"),
                AclVarsInterpolator.interpolateFilter(requestWith(Map.of("status", "closed")), clause));
    }

    @Test
    public void aWholeFilterThatIsAnUnboundIfvarSetsNoCondition() {
        var clause = BsonDocument.parse("{\"$ifvar\": [\"@qparams['category']\", {\"category\": \"@qparams['category']\"}]}");

        assertEquals(new BsonDocument(), AclVarsInterpolator.interpolateFilter(requestWith(Map.of()), clause));
    }

    @Test
    public void aVarWithADefaultTakesTheDefaultWhenUnbound() {
        var clause = BsonDocument.parse("{\"lang\": {\"$var\": [\"@qparams['lang']\", \"en\"]}}");

        assertEquals(BsonDocument.parse("{\"lang\": \"en\"}"), AclVarsInterpolator.interpolateFilter(requestWith(Map.of()), clause));
        assertEquals(BsonDocument.parse("{\"lang\": \"it\"}"),
                AclVarsInterpolator.interpolateFilter(requestWith(Map.of("lang", "it")), clause));
    }

    @Test
    public void aValueFromTheRequestStaysAValue() {
        var clause = BsonDocument.parse("{\"category\": {\"$var\": [\"@qparams['category']\", \"desk\"]}}");
        var filter = AclVarsInterpolator.interpolateFilter(requestWith(Map.of("category", "{\"$ne\": null}")), clause);

        assertEquals("{\"$ne\": null}", filter.asDocument().getString("category").getValue());
    }

    @Test
    public void aMalformedIfvarIsRefusedWhenThePermissionIsLoaded() {
        assertThrows(IllegalArgumentException.class,
                () -> AclVarsInterpolator.checkConditionals(BsonDocument.parse("{\"$ifvar\": [\"\", {}]}")));
        assertThrows(IllegalArgumentException.class,
                () -> AclVarsInterpolator.checkConditionals(BsonDocument.parse("{\"$ifvar\": [\"@qparams['c']\"]}")));
        assertThrows(IllegalArgumentException.class,
                () -> AclVarsInterpolator.checkConditionals(BsonDocument.parse("{\"a\": {\"$var\": [\"@qparams['c']\", 1, 2]}}")));

        var permission = BsonDocument.parse("{\"mongo\": {\"readFilter\": {\"$ifvar\": [1, {}]}}}");
        assertThrows(ConfigurationException.class, () -> MongoPermissions.from(permission));
    }

    @Test
    public void aSortIsReadWithThePermission() {
        var permission = BsonDocument.parse("{\"mongo\": {\"sort\": {\"name\": 1}}}");

        assertEquals(BsonDocument.parse("{\"name\": 1}"), MongoPermissions.from(permission).getSort());

        var notAnObject = BsonDocument.parse("{\"mongo\": {\"sort\": \"name\"}}");
        assertThrows(ConfigurationException.class, () -> MongoPermissions.from(notAnObject));
    }

    @Test
    public void aPlainNameIsAQueryParameterReadAsJson() {
        var clause = BsonDocument.parse("{\"n\": {\"$var\": \"a\"}}");

        assertEquals(BsonDocument.parse("{\"n\": 1}"), AclVarsInterpolator.interpolateFilter(requestWith(Map.of("a", "1")), clause));
        assertEquals(BsonDocument.parse("{\"n\": \"desk\"}"), AclVarsInterpolator.interpolateFilter(requestWith(Map.of("a", "desk")), clause));

        var withDefault = BsonDocument.parse("{\"n\": {\"$var\": [\"a\", 7]}}");
        assertEquals(BsonDocument.parse("{\"n\": 7}"), AclVarsInterpolator.interpolateFilter(requestWith(Map.of()), withDefault));
    }

    @Test
    public void aQueryParameterWithAnOperatorIsRefused() {
        var clause = BsonDocument.parse("{\"n\": {\"$var\": \"a\"}}");

        assertThrows(IllegalArgumentException.class,
                () -> AclVarsInterpolator.interpolateFilter(requestWith(Map.of("a", "{\"$ne\": null}")), clause));
        assertThrows(IllegalArgumentException.class,
                () -> AclVarsInterpolator.interpolateFilter(requestWith(Map.of("a", "[{\"$gt\": 1}]")), clause));
    }

    @Test
    public void anIfvarOnAPlainName() {
        var clause = BsonDocument.parse("{\"$and\": [{\"published\": true}, {\"$ifvar\": [\"category\", {\"category\": {\"$var\": \"category\"}}]}]}");

        assertEquals(BsonDocument.parse("{\"$and\": [{\"published\": true}, {\"category\": \"desk\"}]}"),
                AclVarsInterpolator.interpolateFilter(requestWith(Map.of("category", "desk")), clause));
        assertEquals(BsonDocument.parse("{\"$and\": [{\"published\": true}]}"),
                AclVarsInterpolator.interpolateFilter(requestWith(Map.of()), clause));
    }

    @Test
    public void anUnboundPlainNameInAFilterMatchesNoDocument() {
        var filter = AclVarsInterpolator.interpolateFilter(requestWith(Map.of()), BsonDocument.parse("{\"owner\": {\"$var\": \"a\"}}"));

        // a random value, never null, which would match the documents without an owner
        assertEquals(true, filter.asDocument().get("owner").isString());
    }
}
