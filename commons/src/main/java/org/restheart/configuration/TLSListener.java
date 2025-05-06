/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

public record TLSListener(boolean enabled, String host, int port, String keystorePath, String keystorePwd, String certificatePwd) {
    public static final String HTTPS_LISTENER_KEY = "https-listener";
    public static final String ENABLED_KEY = "enabled";
    public static final String HOST_KEY = "host";
    public static final String PORT_KEY = "port";
    public static final String KEYSTORE_PATH_KEY = "keystore-path";
    public static final String KEYSTOPRE_PWD_KEY = "keystore-password";
    public static final String CERT_PWD_KEY = "certificate-password";

    public TLSListener(Map<String, Object> conf, String listenerKey, TLSListener defaultValue, boolean silent) {
        this(findOrDefault(conf, "/" + listenerKey + "/" + ENABLED_KEY, defaultValue.enabled(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" +  HOST_KEY, defaultValue.host(), silent),
            findOrDefault(conf,"/" + listenerKey + "/" +  PORT_KEY, defaultValue.port(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" + KEYSTORE_PATH_KEY, defaultValue.keystorePath(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" + KEYSTOPRE_PWD_KEY, defaultValue.keystorePwd(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" + CERT_PWD_KEY, defaultValue.certificatePwd(), silent));
    }

    @Override
    public String toString() {
        return "{enabled: " + enabled + ", " +
                "host: " + host + ", " +
                "port: " + port + ", " +
                "keystorePath: " + keystorePath + ", " +
                "keystorePwd: "  + (keystorePwd == null ? "null" : "******") + ", " +
                "certificatePwd:"  + (certificatePwd == null ? "null" : "******") + "}";
    }
}
