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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class BodyValidatorTest {

    @Test
    public void noSchema_alwaysValid() {
        assertTrue(BodyValidator.validate(null, Map.of("anything", "goes")).isEmpty());
        assertTrue(BodyValidator.validate(Map.of(), Map.of("anything", "goes")).isEmpty());
    }

    @Test
    public void validAgainstSchema_noErrors() {
        var schema = Map.<String, Object>of(
                "type", "object",
                "properties", Map.of("message", Map.of("type", "string")),
                "required", List.of("message"));

        var errors = BodyValidator.validate(schema, Map.of("message", "hello"));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void missingRequiredProperty_reportsError() {
        var schema = Map.<String, Object>of(
                "type", "object",
                "properties", Map.of("message", Map.of("type", "string")),
                "required", List.of("message"));

        var errors = BodyValidator.validate(schema, Map.of("other", "value"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void wrongPropertyType_reportsError() {
        var schema = Map.<String, Object>of(
                "type", "object",
                "properties", Map.of("quantity", Map.of("type", "integer")));

        var errors = BodyValidator.validate(schema, Map.of("quantity", "not a number"));
        assertFalse(errors.isEmpty());
    }

    @Test
    public void nullBody_validatedAgainstSchema() {
        var schema = Map.<String, Object>of("type", "object");
        var errors = BodyValidator.validate(schema, null);
        assertFalse(errors.isEmpty());
    }
}
