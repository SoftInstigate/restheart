/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.plugins.schema;

import java.util.List;

/**
 * Thrown when a document fails JSON Schema validation.
 * <p>
 * Carries the list of violation messages so callers can surface them to clients.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SchemaValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final List<String> violations;

    /**
     * @param message   summary message
     * @param violations the list of individual violation messages
     */
    public SchemaValidationException(String message, List<String> violations) {
        super(message);
        this.violations = violations;
    }

    /**
     * @param message   summary message
     * @param violations the list of individual violation messages
     * @param cause     the underlying validation exception
     */
    public SchemaValidationException(String message, List<String> violations, Throwable cause) {
        super(message, cause);
        this.violations = violations;
    }

    /**
     * @return the list of individual violation messages
     */
    public List<String> getViolations() {
        return violations;
    }
}
