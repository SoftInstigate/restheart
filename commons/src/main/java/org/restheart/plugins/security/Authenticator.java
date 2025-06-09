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
package org.restheart.plugins.security;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import org.restheart.plugins.ConfigurablePlugin;

/**
 * Interface for authenticating users and verifying credentials.
 * 
 * <p>Authenticator is a core security component that verifies user credentials and creates
 * account objects representing authenticated users. It extends Undertow's {@link IdentityManager}
 * interface to integrate with the web server's security infrastructure.</p>
 * 
 * <h2>Purpose</h2>
 * <p>Authenticators are responsible for:</p>
 * <ul>
 *   <li>Verifying user credentials (username/password, tokens, certificates, etc.)</li>
 *   <li>Creating {@link Account} objects for authenticated users</li>
 *   <li>Loading user roles and permissions</li>
 *   <li>Integrating with various identity stores (files, databases, LDAP, etc.)</li>
 * </ul>
 * 
 * <h2>Authentication Flow</h2>
 * <ol>
 *   <li>An {@link AuthMechanism} extracts credentials from the HTTP request</li>
 *   <li>The Authenticator verifies these credentials</li>
 *   <li>If valid, an Account object is created with user details and roles</li>
 *   <li>The Account is used by {@link Authorizer} for access control decisions</li>
 * </ol>
 * 
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "fileAuthenticator",
 *     description = "Authenticates users from a properties file"
 * )
 * public class FileAuthenticator implements Authenticator {
 *     private Map<String, UserData> users;
 *     
 *     @Inject("config")
 *     private Map<String, Object> config;
 *     
 *     @OnInit
 *     public void init() {
 *         String filePath = arg(config, "users-file");
 *         users = loadUsersFromFile(filePath);
 *     }
 *     
 *     @Override
 *     public Account verify(String id, Credential credential) {
 *         if (credential instanceof PasswordCredential) {
 *             PasswordCredential pwd = (PasswordCredential) credential;
 *             UserData user = users.get(id);
 *             
 *             if (user != null && verifyPassword(pwd.getPassword(), user.hash)) {
 *                 return new FileRealmAccount(id, user.roles);
 *             }
 *         }
 *         return null;
 *     }
 *     
 *     @Override
 *     public Account verify(Account account) {
 *         // Verify existing account is still valid
 *         return users.containsKey(account.getPrincipal().getName()) ? account : null;
 *     }
 *     
 *     @Override
 *     public Account verify(Credential credential) {
 *         // Not used in basic authentication
 *         return null;
 *     }
 * }
 * }</pre>
 * 
 * <h2>Multiple Authenticators</h2>
 * <p>RESTHeart supports multiple authenticators working together. They are tried in order
 * until one successfully authenticates the user or all fail. Configure the order using
 * the priority attribute in {@link org.restheart.plugins.RegisterPlugin}.</p>
 * 
 * <h2>Built-in Implementations</h2>
 * <p>RESTHeart provides several authenticator implementations:</p>
 * <ul>
 *   <li>FileAuthenticator - Users stored in properties files</li>
 *   <li>MongoAuthenticator - Users stored in MongoDB collections</li>
 *   <li>JwtAuthenticator - Validates JWT tokens</li>
 *   <li>LdapAuthenticator - Integrates with LDAP/Active Directory</li>
 * </ul>
 * 
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li>Always hash passwords using secure algorithms (bcrypt, scrypt, argon2)</li>
 *   <li>Implement rate limiting to prevent brute force attacks</li>
 *   <li>Log authentication failures for security monitoring</li>
 *   <li>Consider caching authenticated accounts to improve performance</li>
 *   <li>Validate account status (not disabled, not expired)</li>
 * </ul>
 * 
 * @see AuthMechanism
 * @see Authorizer
 * @see Account
 * @see <a href="https://restheart.org/docs/plugins/security-plugins/#authenticators">Authenticator Documentation</a>
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Authenticator extends IdentityManager, ConfigurablePlugin {
    @Override
    public Account verify(final Account account);

    @Override
    public Account verify(final String id, final Credential credential);

    @Override
    public Account verify(final Credential credential);
}
