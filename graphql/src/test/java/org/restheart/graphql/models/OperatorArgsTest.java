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
package org.restheart.graphql.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;

/**
 * {"$arg": "name", "operators": true} declares an argument that may carry operators with
 * check-operators on: the mapping keeps the argument's name and drops the key, so the
 * interpolator finds a plain $arg.
 */
public class OperatorArgsTest {
    private static AggregationMapping mapping(String stages) {
        return new AggregationMapping("f", new BsonString("db"), new BsonString("coll"),
                BsonArray.parse(stages), new BsonBoolean(false), null);
    }

    @Test
    public void declaredArgumentIsReadAndTheKeyDropped() {
        var m = mapping("[ { \"$match\": { \"fsType\": \"Dataset\" } }, { \"$ifarg\": [ \"filter\", { \"$match\": { \"$arg\": \"filter\", \"operators\": true } } ] } ]");

        assertEquals(Set.of("filter"), m.getOperatorArgs());
        assertEquals(BsonArray.parse("[ { \"$match\": { \"fsType\": \"Dataset\" } }, { \"$ifarg\": [ \"filter\", { \"$match\": { \"$arg\": \"filter\" } } ] } ]"), m.getStages());
    }

    @Test
    public void declaredArgumentWithDefault() {
        var m = mapping("[ { \"$match\": { \"$arg\": [ \"filter\", {} ], \"operators\": true } } ]");

        assertEquals(Set.of("filter"), m.getOperatorArgs());
        assertEquals(BsonArray.parse("[ { \"$match\": { \"$arg\": [ \"filter\", {} ] } } ]"), m.getStages());
    }

    @Test
    public void plainArgumentsAreNotDeclared() {
        var m = mapping("[ { \"$match\": { \"owner\": { \"$arg\": \"owner\" } } } ]");

        assertEquals(Set.of(), m.getOperatorArgs());
    }

    @Test
    public void operatorsFalseIsAPlainArgument() {
        var m = mapping("[ { \"$match\": { \"$arg\": \"filter\", \"operators\": false } } ]");

        assertEquals(Set.of(), m.getOperatorArgs());
        assertEquals(BsonArray.parse("[ { \"$match\": { \"$arg\": \"filter\" } } ]"), m.getStages());
    }

    @Test
    public void anArgumentDeclaredInOnePlaceOnlyIsInvalid() {
        assertThrows(GraphQLIllegalAppDefinitionException.class, () -> mapping(
                "[ { \"$match\": { \"$arg\": \"filter\", \"operators\": true } }, { \"$match\": { \"owner\": { \"$arg\": \"filter\" } } } ]"));
    }

    @Test
    public void operatorsThatIsNotABooleanIsInvalid() {
        assertThrows(GraphQLIllegalAppDefinitionException.class, () -> mapping(
                "[ { \"$match\": { \"$arg\": \"filter\", \"operators\": \"yes\" } } ]"));
    }
}
