/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
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

import static org.restheart.configuration.Utils.findOrDefault;
import java.util.Map;

public record Listener(boolean enabled, String host, int port) {
    public static final String HTTP_LISTENER_KEY = "http-listener";
    public static final String AJP_LISTENER_KEY = "ajp-listener";
    public static final String ENABLED_KEY = "enabled";
    public static final String HOST_KEY = "host";
    public static final String PORT_KEY = "port";

    public Listener(Map<String, Object> conf, String listenerKey, Listener defaultValue, boolean silent) {
        this(findOrDefault(conf, "/" + listenerKey + "/" + ENABLED_KEY, defaultValue.enabled(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" + HOST_KEY, defaultValue.host(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" + PORT_KEY, defaultValue.port(), silent));
    }
}
