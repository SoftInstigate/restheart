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

import java.util.Set;

/** The action named in a {@code how_to_call} request is not declared by the resource. */
public class UnknownActionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient Set<String> validActions;

    public UnknownActionException(String resourceUri, String action, Set<String> validActions) {
        super("unknown action '" + action + "' for resource " + resourceUri + "; valid actions: " + validActions);
        this.validActions = validActions;
    }

    public Set<String> validActions() {
        return validActions;
    }
}
