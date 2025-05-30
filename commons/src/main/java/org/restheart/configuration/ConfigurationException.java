/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
package org.restheart.configuration;

/**
 * Exception thrown when configuration loading or validation fails.
 * 
 * <p>This exception is thrown in various scenarios:</p>
 * <ul>
 *   <li>Configuration file is not found or cannot be read</li>
 *   <li>Configuration file has invalid syntax (YAML/JSON parse errors)</li>
 *   <li>Required configuration properties are missing</li>
 *   <li>Configuration values are invalid or out of acceptable range</li>
 *   <li>Override files have unsupported extensions or invalid format</li>
 * </ul>
 * 
 * <p>The exception includes a flag {@code shouldPrintStackTrace} that indicates
 * whether the full stack trace should be printed. This is typically set to false
 * for user-friendly errors (like missing files) and true for unexpected errors
 * (like parse exceptions).</p>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 1.0
 */
public class ConfigurationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1685800316196830205L;
    private final boolean shoudlPrintStackTrace;

    /**
     * Constructs a new ConfigurationException with no message.
     * 
     * <p>Stack trace printing is disabled by default for this constructor.</p>
     */
    public ConfigurationException() {
        super();
        this.shoudlPrintStackTrace = false;
    }

    /**
     * Constructs a new ConfigurationException with the specified message.
     * 
     * <p>Stack trace printing is disabled by default for this constructor.
     * This is typically used for user-friendly error messages.</p>
     * 
     * @param message the detail message explaining the configuration error
     */
    public ConfigurationException(String message) {
        super(message);
        this.shoudlPrintStackTrace = false;
    }

    /**
     * Constructs a new ConfigurationException with message and cause.
     * 
     * <p>Stack trace printing is enabled by default for this constructor.
     * This is typically used for unexpected errors that require debugging.</p>
     * 
     * @param message the detail message explaining the configuration error
     * @param cause the underlying cause of the configuration error
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
        this.shoudlPrintStackTrace = true;
    }

    /**
     * Constructs a new ConfigurationException with full control over stack trace printing.
     * 
     * <p>This constructor allows explicit control over whether the stack trace
     * should be printed when the exception is logged. Use this when you want
     * to provide a cause but control the verbosity of error output.</p>
     * 
     * @param message the detail message explaining the configuration error
     * @param cause the underlying cause of the configuration error
     * @param shoudlPrintStackTrace true if stack trace should be printed when logged
     */
    public ConfigurationException(String message, Throwable cause, boolean shoudlPrintStackTrace) {
        super(message, cause);
        this.shoudlPrintStackTrace = shoudlPrintStackTrace;
    }

    /**
     * Indicates whether the stack trace should be printed when this exception is logged.
     * 
     * <p>This is used by error handlers to determine the appropriate level of
     * detail to show to users. User-friendly errors (like missing files) typically
     * return false, while unexpected errors return true.</p>
     * 
     * @return true if the stack trace should be printed, false otherwise
     */
    public boolean shoudlPrintStackTrace() {
        return shoudlPrintStackTrace;
    }
}
