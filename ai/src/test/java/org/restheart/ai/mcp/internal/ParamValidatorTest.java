/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.mcp.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.ai.mcp.api.McpResource;

public class ParamValidatorTest {

    private McpResource.Action action(McpResource.Param... params) {
        var action = new McpResource.Action();
        var i = 0;
        for (var p : params) {
            action.param("p" + (i++), p);
        }
        return action;
    }

    @Test
    public void noParamsDeclared_anyArgsValid() {
        var errors = ParamValidator.validate(new McpResource.Action(), Map.of("whatever", "value"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void requiredParamMissing_reportsError() {
        var action = action(new McpResource.Param("string", null, true, null, null));
        var errors = ParamValidator.validate(action, Map.of());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("p0"));
    }

    @Test
    public void requiredParamMissingButHasDefault_noError() {
        var action = action(new McpResource.Param("string", null, true, null, "fallback"));
        var errors = ParamValidator.validate(action, Map.of());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void optionalParamMissing_noError() {
        var action = action(new McpResource.Param("string", null, false, null, null));
        var errors = ParamValidator.validate(action, Map.of());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void wrongType_reportsError() {
        var action = action(new McpResource.Param("integer", null, false, null, null));
        var errors = ParamValidator.validate(action, Map.of("p0", "not a number"));
        assertEquals(1, errors.size());
    }

    @Test
    public void correctType_noError() {
        var action = action(new McpResource.Param("integer", null, false, null, null));
        var errors = ParamValidator.validate(action, Map.of("p0", 42));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void enumViolation_reportsError() {
        var action = action(new McpResource.Param("string", null, false, List.of("A1", "A2"), null));
        var errors = ParamValidator.validate(action, Map.of("p0", "B1"));
        assertEquals(1, errors.size());
    }

    @Test
    public void enumSatisfied_noError() {
        var action = action(new McpResource.Param("string", null, false, List.of("A1", "A2"), null));
        var errors = ParamValidator.validate(action, Map.of("p0", "A1"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void nullArgsMap_treatedAsEmpty() {
        var action = action(new McpResource.Param("string", null, true, null, null));
        var errors = ParamValidator.validate(action, null);
        assertEquals(1, errors.size());
    }
}
