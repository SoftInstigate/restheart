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

/**
 * MongoDB client session management for RESTHeart.
 * 
 * <p>This package provides custom implementations and utilities for managing MongoDB client
 * sessions within RESTHeart's architecture. It includes session lifecycle management,
 * transaction support infrastructure, and session pooling mechanisms tailored to RESTHeart's
 * specific requirements for handling concurrent requests and maintaining session state.</p>
 * 
 * <h2>Core Components</h2>
 * 
 * <h3>{@link org.restheart.mongodb.db.sessions.ClientSessionImpl}</h3>
 * <p>The primary session implementation that extends MongoDB's BaseClientSessionImpl:</p>
 * <ul>
 *   <li>Manages session state and lifecycle within RESTHeart's request context</li>
 *   <li>Provides configurable causal consistency for read operations</li>
 *   <li>Tracks transaction state and message flow</li>
 *   <li>Integrates with RESTHeart's authentication and authorization system</li>
 * </ul>
 * 
 * <h2>Session Features</h2>
 * 
 * <h3>Causal Consistency</h3>
 * <p>Sessions support causal consistency, ensuring that:</p>
 * <ul>
 *   <li>Read operations see the results of preceding write operations</li>
 *   <li>Operations maintain a global partial order that preserves causality</li>
 *   <li>Consistency can be configured per session based on application needs</li>
 * </ul>
 * 
 * <h3>Transaction Support</h3>
 * <p>While the current implementation provides limited transaction support:</p>
 * <ul>
 *   <li>Transaction methods are present but throw UnsupportedOperationException</li>
 *   <li>Transaction state tracking is maintained for future enhancement</li>
 *   <li>RESTHeart handles transactions at a different architectural layer</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Creating a Session</h3>
 * <pre>{@code
 * ServerSessionPool sessionPool = // ... obtain pool
 * ClientSessionOptions options = ClientSessionOptions.builder()
 *     .causallyConsistent(true)
 *     .build();
 * 
 * ClientSessionImpl session = new ClientSessionImpl(
 *     sessionPool,
 *     mongoClient,
 *     options
 * );
 * }</pre>
 * 
 * <h3>Using Sessions with Operations</h3>
 * <pre>{@code
 * // Session automatically tracks operation order for causal consistency
 * collection.find(filter).session(session).first();
 * collection.updateOne(filter, update).session(session);
 * 
 * // Subsequent reads will see the update
 * collection.find(filter).session(session).first();
 * }</pre>
 * 
 * <h3>Session Lifecycle Management</h3>
 * <pre>{@code
 * try (ClientSessionImpl session = createSession()) {
 *     // Use session for operations
 *     performDatabaseOperations(session);
 * } // Session is automatically closed
 * }</pre>
 * 
 * <h2>Session Identification</h2>
 * 
 * <p>Sessions are uniquely identified by UUIDs extracted from the server session:</p>
 * <pre>{@code
 * UUID sessionId = session.getSid();
 * // or statically
 * UUID sessionId = ClientSessionImpl.getSid(clientSession);
 * }</pre>
 * 
 * <h2>Design Considerations</h2>
 * 
 * <h3>RESTHeart Integration</h3>
 * <p>The session implementation is designed to work within RESTHeart's architecture:</p>
 * <ul>
 *   <li>Sessions are managed per HTTP request lifecycle</li>
 *   <li>Session pooling is optimized for REST API usage patterns</li>
 *   <li>Integration with RESTHeart's authentication provides session-level security</li>
 * </ul>
 * 
 * <h3>Performance Optimization</h3>
 * <ul>
 *   <li>Custom close() implementation avoids unnecessary session pool operations</li>
 *   <li>Efficient session ID extraction and caching</li>
 *   <li>Minimal overhead for non-transactional operations</li>
 * </ul>
 * 
 * <h3>Future Enhancements</h3>
 * <p>The package is designed to support future enhancements:</p>
 * <ul>
 *   <li>Full transaction support when needed by RESTHeart</li>
 *   <li>Advanced session pooling strategies</li>
 *   <li>Session-level metrics and monitoring</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * 
 * <p>Session implementations require external synchronization:</p>
 * <ul>
 *   <li>Sessions are not thread-safe and should not be shared between threads</li>
 *   <li>Each request should use its own session instance</li>
 *   <li>Session pool access is thread-safe</li>
 * </ul>
 * 
 * @since 4.0
 */
package org.restheart.mongodb.db.sessions;