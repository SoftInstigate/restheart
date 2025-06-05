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
/**
 * Security framework for authentication, authorization, and access control in RESTHeart.
 * 
 * <p>This package provides comprehensive security infrastructure for RESTHeart applications,
 * including authentication mechanisms, authorization policies, access control lists (ACLs),
 * and various account implementations for different authentication providers.</p>
 * 
 * <h2>Core Components</h2>
 * 
 * <h3>Account Management</h3>
 * <ul>
 *   <li>{@link org.restheart.security.BaseAccount} - Abstract base class for all account types</li>
 *   <li>{@link org.restheart.security.PwdCredentialAccount} - Account with password credentials</li>
 *   <li>{@link org.restheart.security.FileRealmAccount} - Account from file-based realm</li>
 *   <li>{@link org.restheart.security.MongoRealmAccount} - Account from MongoDB-based realm</li>
 *   <li>{@link org.restheart.security.JwtAccount} - Account from JWT tokens</li>
 * </ul>
 * 
 * <h3>Access Control</h3>
 * <ul>
 *   <li>{@link org.restheart.security.ACLRegistry} - Central registry for access control lists</li>
 *   <li>{@link org.restheart.security.BaseAclPermission} - Base class for ACL permissions</li>
 *   <li>{@link org.restheart.security.BaseAclPermissionTransformer} - Transform and evaluate permissions</li>
 *   <li>{@link org.restheart.security.MongoPermissions} - MongoDB-specific permissions</li>
 * </ul>
 * 
 * <h3>Principal and Properties</h3>
 * <ul>
 *   <li>{@link org.restheart.security.BasePrincipal} - Base implementation of security principal</li>
 *   <li>{@link org.restheart.security.WithProperties} - Interface for objects with properties</li>
 * </ul>
 * 
 * <h3>Variable Interpolation</h3>
 * <ul>
 *   <li>{@link org.restheart.security.AclVarsInterpolator} - Interpolate variables in ACL expressions</li>
 * </ul>
 * 
 * <h2>Authentication Flow</h2>
 * <p>The security framework supports multiple authentication mechanisms:</p>
 * <ol>
 *   <li>Basic Authentication with file or database realms</li>
 *   <li>JWT token-based authentication</li>
 *   <li>Custom authentication mechanisms via plugins</li>
 *   <li>Certificate-based authentication</li>
 * </ol>
 * 
 * <h2>Authorization Model</h2>
 * <p>RESTHeart uses a flexible authorization model based on:</p>
 * <ul>
 *   <li>Role-based access control (RBAC)</li>
 *   <li>Predicate-based permissions</li>
 *   <li>Resource-level access control</li>
 *   <li>MongoDB-specific permissions (read, write, manage)</li>
 * </ul>
 * 
 * <h2>ACL System</h2>
 * <p>The Access Control List system provides:</p>
 * <ul>
 *   <li>Fine-grained permission control</li>
 *   <li>Dynamic permission evaluation</li>
 *   <li>Variable interpolation in permission expressions</li>
 *   <li>Caching for performance optimization</li>
 * </ul>
 * 
 * <h2>Security Best Practices</h2>
 * <p>When using this security framework:</p>
 * <ul>
 *   <li>Always use HTTPS in production</li>
 *   <li>Implement proper password policies</li>
 *   <li>Use JWT tokens with appropriate expiration</li>
 *   <li>Follow principle of least privilege</li>
 *   <li>Regularly audit access permissions</li>
 *   <li>Use environment variables for sensitive configuration</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Creating an account
 * var account = new PwdCredentialAccount(
 *     "username",
 *     hashedPassword,
 *     Set.of("admin", "user")
 * );
 * 
 * // Checking permissions
 * var permission = new BaseAclPermission(
 *     "GET",
 *     "/api/collection/*"
 * );
 * 
 * if (aclRegistry.authorize(account, permission)) {
 *     // Access granted
 * }
 * }</pre>
 * 
 * <h2>Integration with Plugins</h2>
 * <p>Security components can be extended through the plugin system to add:</p>
 * <ul>
 *   <li>Custom authentication mechanisms</li>
 *   <li>Additional authorization strategies</li>
 *   <li>External identity providers</li>
 *   <li>Custom token managers</li>
 * </ul>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
package org.restheart.security;