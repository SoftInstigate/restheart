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
package org.restheart.ai.mcp.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.plugins.mcp.McpResource;

public class ParamValidatorTest {

    private McpResource.Action action(McpResource.Param... params) {
        var action = new McpResource.Action();
        var i = 0;
        for (var p : params) {
            action.param("p" + (i++), p);
        }
        return action;
    }

    /**
     * The mirror of the test below, and the shape that matters since 9.9: a variable is declared
     * as a param of its own, because RESTHeart binds it from a bare query parameter. It still
     * binds it from `avars` as well, so a caller using the legacy form must not be refused here —
     * that would make this validator stricter than the endpoint the call is dispatched to.
     */
    @Test
    public void aFlatParamSuppliedInsideAvars_counts() {
        var action = McpResource.builder()
                .uri("https://host/x")
                .action("execute", a -> a.param("status", new McpResource.Param("string", null, true, null, null)))
                .build()
                .actions().get("execute");

        assertTrue(ParamValidator.validate(action, Map.of("status", "A")).isEmpty(),
                "the flat form is what the catalogue now declares");
        assertTrue(ParamValidator.validate(action, Map.of("avars", Map.of("status", "A"))).isEmpty(),
                "the legacy object a tool call may still send");
        assertTrue(ParamValidator.validate(action, Map.of("avars", "{\"status\": \"A\"}")).isEmpty(),
                "and the same thing as text, which is how it arrives from a resources/read URI");
        assertEquals(List.of("missing required param 'status'"),
                ParamValidator.validate(action, Map.of("avars", "{\"other\": \"A\"}")),
                "an avars that does not carry it is not carrying it");
        assertEquals(List.of("missing required param 'status'"), ParamValidator.validate(action, Map.of()));
    }

    @Test
    public void anObjectParamSuppliedOnePropertyAtATime_counts() {
        // the resource template advertises the flat shape and nothing else — an aggregation's is
        // .../byStatus{?status}, never {?avars} — so a caller that fills in exactly what it was
        // shown must not be told it is missing an object it was never offered
        var properties = Map.of("status", new McpResource.Param("string", null, true, null, null));
        var action = McpResource.builder()
                .uri("https://host/x")
                .action("execute", a -> a.param("avars", new McpResource.Param("object", null, true, null, null, properties)))
                .build()
                .actions().get("execute");

        assertTrue(ParamValidator.validate(action, Map.of("status", "A")).isEmpty(),
                "the flat form is the one the template asks for");
        assertTrue(ParamValidator.validate(action, Map.of("avars", Map.of("status", "A"))).isEmpty(),
                "and the nested form is still the one a tool call sends");
        assertEquals(List.of("missing required param 'avars'"), ParamValidator.validate(action, Map.of()),
                "neither shape supplied is the case worth reporting early");
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
