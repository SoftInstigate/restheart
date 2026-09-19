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

import java.util.Map;

import org.restheart.plugins.mcp.McpResource;

import org.restheart.ai.mcp.transport.DescriptorRenderer;
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
     * Whether a caller may be told how to make one particular call.
     *
     * <p>A descriptor is disclosure — parameter names, body schema, the prose written to orient an
     * agent — so it is gated, unlike execution, which the pipeline authorizes on its own. The gate
     * sees the arguments, because the ACL may decide on them.
     */
    @FunctionalInterface
    public interface Gate {
        boolean allows(McpResource resource, String actionName, Map<String, Object> args);

        /** No request to derive an identity from, so nothing to withhold. */
        Gate OPEN = (resource, actionName, args) -> true;
    }

    /**
     * @throws UnknownResourceException if {@code resourceUri} matches no known resource, or the
     *                                  gate refuses this caller the descriptor for it
     * @throws UnknownActionException   if {@code actionName} is not declared by the resource
     * @throws ValidationFailedException if {@code args} fails param or body-schema validation
     */
    public Map<String, Object> call(BaseAccount principal, String baseUrl, String scope, String resourceUri, String actionName,
                                    Map<String, Object> args, String transportPreference, Gate gate) {
        var resolved = resolve(principal, baseUrl, scope, resourceUri, actionName, args);

        // Composing a request for a call the caller could not make would hand back, parameter by
        // parameter, exactly what leaving it out of list_apis was meant to withhold.
        if (!gate.allows(resolved.resource(), actionName, args)) {
            throw new UnknownResourceException(resourceUri);
        }

        return DescriptorRenderer.render(resolved.resource(), actionName, args, transportPreference);
    }

    /** a resource and one of its actions, found and validated for a call */
    public record Resolved(McpResource resource, McpResource.Action action) {
    }

    /**
     * Finds the resource and the action a call names, and validates the arguments against the
     * action's params and body schema. Shared by {@code how_to_call}, which then renders a
     * descriptor, and {@code call_api}, which then executes: the two can never disagree on what a
     * call is.
     *
     * <p>No authorization is decided here, deliberately. A caller who names a resource has not
     * enumerated anything, and what they may do with it is the pipeline's to answer: the request
     * is dispatched and the ACL refuses it with its own status and its own body. Deciding it twice
     * is how the two answers end up disagreeing — and the catalogue's copy of the question is the
     * weaker one, since a listing is composed without the arguments a rule may read.
     *
     * @throws UnknownResourceException if {@code resourceUri} matches no known resource
     * @throws UnknownActionException   if {@code actionName} is not declared by the resource
     * @throws ValidationFailedException if {@code args} fails param or body-schema validation
     */
    public Resolved resolve(BaseAccount principal, String baseUrl, String scope, String resourceUri, String actionName,
                            Map<String, Object> args) {
        var resource = lookup.find(principal, baseUrl, scope, resourceUri)
                .orElseThrow(() -> new UnknownResourceException(resourceUri));

        var action = resource.actions().get(actionName);
        if (action == null) {
            throw new UnknownActionException(resourceUri, actionName, resource.actions().keySet());
        }

        // Params only, deliberately. The body is not checked here, and must not be: a
        // {@code body_schema} describes the document the resource stores, and what a caller sends
        // is not that document. A deployment fills fields in on the way (RESTHeart's own
        // {@code mongo.mergeRequest} stamps the author of a write and the time of it), so a schema
        // that rightly requires them rejects a request that is right to omit them. That is a
        // refusal the REST endpoint does not make: its checker runs after those fields are set.
        // The body is the service's to judge, and it judges it — call_api carries the answer back
        // with its status and its message.
        var errors = ParamValidator.validate(action, args);

        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        return new Resolved(resource, action);
    }
}
