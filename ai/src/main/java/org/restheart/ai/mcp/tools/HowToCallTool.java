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
package org.restheart.ai.mcp.tools;

import java.util.ArrayList;
import java.util.Map;

import org.restheart.ai.mcp.transport.DescriptorRenderer;
import org.restheart.ai.mcp.validation.BodyValidator;
import org.restheart.ai.mcp.validation.ParamValidator;
import org.restheart.security.BaseAccount;

/**
 * Handles the {@code how_to_call} tool. Locates the resource, validates {@code action}
 * and {@code args} against its declarations, then delegates to {@link DescriptorRenderer}
 * to compose the transport-specific request descriptor. Composes — never executes;
 * see restheart#615 design principles.
 */
public final class HowToCallTool {

    private final CachedResourceLookup lookup;

    public HowToCallTool(CachedResourceLookup lookup) {
        this.lookup = lookup;
    }

    /**
     * @throws UnknownResourceException if {@code resourceUri} matches no known resource
     * @throws UnknownActionException   if {@code actionName} is not declared by the resource
     * @throws ValidationFailedException if {@code args} fails param or body-schema validation
     */
    public Map<String, Object> call(BaseAccount principal, String baseUrl, String resourceUri, String actionName,
            Map<String, Object> args, String transportPreference, String token) {
        var resource = lookup.find(principal, baseUrl, resourceUri)
                .orElseThrow(() -> new UnknownResourceException(resourceUri));

        var action = resource.actions().get(actionName);
        if (action == null) {
            throw new UnknownActionException(resourceUri, actionName, resource.actions().keySet());
        }

        var errors = new ArrayList<>(ParamValidator.validate(action, args));
        errors.addAll(BodyValidator.validate(action.bodySchema(), args == null ? null : args.get("body")));
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        return DescriptorRenderer.render(resource, actionName, args, transportPreference, token);
    }
}
