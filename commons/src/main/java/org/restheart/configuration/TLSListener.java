/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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

/**
 * Configuration for HTTPS listeners with TLS/SSL support.
 * 
 * <p>This record represents the configuration for HTTPS listeners that handle
 * secure HTTP connections using TLS/SSL. The listener requires a keystore
 * containing the server certificate and private key.</p>
 * 
 * <h2>Configuration Structure</h2>
 * <p>In the configuration file, the HTTPS listener is configured as:</p>
 * <pre>{@code
 * https-listener:
 *   enabled: true
 *   host: "0.0.0.0"
 *   port: 4443
 *   keystore-path: "/path/to/keystore.jks"
 *   keystore-password: "secret"
 *   certificate-password: "secret"
 * }</pre>
 * 
 * <h2>Keystore Requirements</h2>
 * <p>The keystore must be in JKS (Java KeyStore) format and contain:</p>
 * <ul>
 *   <li>The server's private key</li>
 *   <li>The server's certificate</li>
 *   <li>Any intermediate CA certificates (if applicable)</li>
 * </ul>
 * 
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li>Store keystore passwords securely (consider using environment variables)</li>
 *   <li>Use strong passwords for both keystore and certificate</li>
 *   <li>Ensure proper file permissions on the keystore file</li>
 *   <li>Regularly update certificates before expiration</li>
 * </ul>
 * 
 * @param enabled whether this HTTPS listener should be activated
 * @param host the hostname or IP address to bind to
 * @param port the port number to listen on (typically 443 or 4443)
 * @param keystorePath path to the JKS keystore file
 * @param keystorePwd password for the keystore
 * @param certificatePwd password for the certificate private key
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 1.0
 */
public record TLSListener(boolean enabled, String host, int port, String keystorePath, String keystorePwd, String certificatePwd) {
    /**
     * Configuration key for the HTTPS listener section.
     */
    public static final String HTTPS_LISTENER_KEY = "https-listener";
    
    /**
     * Configuration key for enabling/disabling the listener.
     */
    public static final String ENABLED_KEY = "enabled";
    
    /**
     * Configuration key for the host/IP binding.
     */
    public static final String HOST_KEY = "host";
    
    /**
     * Configuration key for the port number.
     */
    public static final String PORT_KEY = "port";
    
    /**
     * Configuration key for the keystore file path.
     */
    public static final String KEYSTORE_PATH_KEY = "keystore-path";
    
    /**
     * Configuration key for the keystore password.
     */
    public static final String KEYSTOPRE_PWD_KEY = "keystore-password";
    
    /**
     * Configuration key for the certificate password.
     */
    public static final String CERT_PWD_KEY = "certificate-password";

    /**
     * Creates a TLSListener from a configuration map.
     * 
     * <p>This constructor extracts HTTPS listener configuration values from the
     * provided map under the specified listener key. If any values are missing,
     * the corresponding values from the defaultValue parameter are used.</p>
     * 
     * @param conf the main configuration map
     * @param listenerKey the key for this listener section (typically "https-listener")
     * @param defaultValue default values to use for missing configuration
     * @param silent if true, suppresses warning messages for missing properties
     */
    public TLSListener(Map<String, Object> conf, String listenerKey, TLSListener defaultValue, boolean silent) {
        this(findOrDefault(conf, "/" + listenerKey + "/" + ENABLED_KEY, defaultValue.enabled(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" +  HOST_KEY, defaultValue.host(), silent),
            findOrDefault(conf,"/" + listenerKey + "/" +  PORT_KEY, defaultValue.port(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" + KEYSTORE_PATH_KEY, defaultValue.keystorePath(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" + KEYSTOPRE_PWD_KEY, defaultValue.keystorePwd(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" + CERT_PWD_KEY, defaultValue.certificatePwd(), silent));
    }

    /**
     * Returns a string representation of this TLS listener configuration.
     * 
     * <p>For security reasons, passwords are masked and shown as "******" in the
     * output. This prevents accidental exposure of sensitive credentials in logs
     * or debug output.</p>
     * 
     * @return a string representation with masked passwords
     */
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
