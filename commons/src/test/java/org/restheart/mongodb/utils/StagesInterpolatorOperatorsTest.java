/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.mongodb.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bson.BsonDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link StagesInterpolator#shouldNotContainOperators}: a variable value is data, never an
 * operator. Strings starting with '$' are values: in an expression they are bound in
 * {@code $literal}, see {@link VarsInterpolatorLiteralTest}.
 */
public class StagesInterpolatorOperatorsTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"v\": \"A\"}",
        "{\"v\": 5}",
        "{\"v\": \"$\"}",
        "{\"v\": \"$ 5\"}",
        "{\"v\": \"costs $5\"}",
        "{\"v\": {\"a\": [1, \"x\", {\"b\": \"$ \"}]}}",
        "{\"v\": {\"$oid\": \"5f0c2b1e9d3b2a1c4e8f7a6b\"}}",
        "{\"v\": {\"$date\": \"2026-11-09T00:00:00Z\"}}",
        "{\"v\": \"$5\"}",
        "{\"v\": \"$name\"}",
        "{\"v\": \"$$ROOT\"}",
        "{\"v\": [\"a\", \"$name\"]}"
    })
    public void allowed(String json) {
        assertDoesNotThrow(() -> StagesInterpolator.shouldNotContainOperators(BsonDocument.parse(json)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"v\": {\"$gt\": \"\"}}",
        "{\"v\": {\"a\": {\"$ne\": null}}}",
        "{\"v\": [{\"$gt\": 1}]}"
    })
    public void refused(String json) {
        assertThrows(SecurityException.class, () -> StagesInterpolator.shouldNotContainOperators(BsonDocument.parse(json)));
    }
}
