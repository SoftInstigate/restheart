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
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ConfigurationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1685800316196830205L;
    private final boolean shoudlPrintStackTrace;

    public ConfigurationException() {
        super();
        this.shoudlPrintStackTrace = false;
    }

    public ConfigurationException(String message) {
        super(message);
        this.shoudlPrintStackTrace = false;
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
        this.shoudlPrintStackTrace = true;
    }

    public ConfigurationException(String message, Throwable cause, boolean shoudlPrintStackTrace) {
        super(message, cause);
        this.shoudlPrintStackTrace = shoudlPrintStackTrace;
    }

    public boolean shoudlPrintStackTrace() {
        return shoudlPrintStackTrace;
    }
}
