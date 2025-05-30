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

/**
 * Core MongoDB integration utilities for RESTHeart.
 * 
 * <p>This package provides fundamental MongoDB-related functionality used throughout RESTHeart,
 * including connection management, replica set configuration, operation results handling,
 * session management, and dynamic query/aggregation variable interpolation.</p>
 * 
 * <h2>Key Components</h2>
 * 
 * <h3>Connection Management</h3>
 * <ul>
 *   <li>{@link org.restheart.mongodb.ConnectionChecker} - Utilities for checking MongoDB
 *       connection status and replica set configuration with caching support</li>
 *   <li>{@link org.restheart.mongodb.RSOps} - Replica Set Options management for configuring
 *       read preferences, read concerns, and write concerns</li>
 * </ul>
 * 
 * <h3>Operation Results</h3>
 * <p>The {@code db} subpackage contains classes for representing operation outcomes:</p>
 * <ul>
 *   <li>{@link org.restheart.mongodb.db.OperationResult} - Base class for database operation
 *       results with HTTP status codes, ETags, and document state tracking</li>
 *   <li>{@link org.restheart.mongodb.db.BulkOperationResult} - Specialized result for bulk
 *       write operations with detailed operation counts</li>
 * </ul>
 * 
 * <h3>Session Management</h3>
 * <p>The {@code db.sessions} subpackage provides MongoDB session handling:</p>
 * <ul>
 *   <li>{@link org.restheart.mongodb.db.sessions.ClientSessionImpl} - Custom implementation
 *       of MongoDB ClientSession supporting RESTHeart's session management requirements</li>
 * </ul>
 * 
 * <h3>Variable Interpolation</h3>
 * <p>The {@code utils} subpackage contains powerful utilities for dynamic query and aggregation
 * pipeline construction:</p>
 * <ul>
 *   <li>{@link org.restheart.mongodb.utils.VarsInterpolator} - Core variable interpolation
 *       engine supporting {@code $var} and {@code $arg} operators with optional default values</li>
 *   <li>{@link org.restheart.mongodb.utils.StagesInterpolator} - Advanced aggregation pipeline
 *       interpolation with conditional stage support using {@code $ifvar} and {@code $ifarg}</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Connection Checking</h3>
 * <pre>{@code
 * MongoClient client = // ... obtain client
 * if (ConnectionChecker.connected(client)) {
 *     if (ConnectionChecker.replicaSet(client)) {
 *         // Connected to a replica set
 *     }
 * }
 * }</pre>
 * 
 * <h3>Configuring Replica Set Options</h3>
 * <pre>{@code
 * RSOps rsOps = new RSOps()
 *     .withReadPreference(ReadPreference.secondaryPreferred())
 *     .withReadConcern(ReadConcern.MAJORITY)
 *     .withWriteConcern(WriteConcern.MAJORITY);
 * 
 * MongoDatabase configuredDb = rsOps.apply(database);
 * }</pre>
 * 
 * <h3>Variable Interpolation in Queries</h3>
 * <pre>{@code
 * // Query template with variables
 * BsonDocument queryTemplate = BsonDocument.parse(
 *     "{ 'status': { '$var': 'status' }, 'age': { '$var': ['minAge', 18] } }"
 * );
 * 
 * // Variable values
 * BsonDocument values = new BsonDocument("status", new BsonString("active"));
 * 
 * // Interpolate
 * BsonValue query = VarsInterpolator.interpolate(
 *     VAR_OPERATOR.$var, queryTemplate, values
 * );
 * // Result: { "status": "active", "age": 18 }
 * }</pre>
 * 
 * <h3>Conditional Aggregation Stages</h3>
 * <pre>{@code
 * // Pipeline with conditional stages
 * BsonArray pipeline = BsonArray.parse("[" +
 *     "{ '$match': { 'type': { '$var': 'type' } } }," +
 *     "{ '$ifvar': ['includeStats', " +
 *     "  { '$group': { '_id': '$category', 'count': { '$sum': 1 } } }" +
 *     "]}" +
 * "]");
 * 
 * // Interpolate with conditions
 * List<BsonDocument> stages = StagesInterpolator.interpolate(
 *     VAR_OPERATOR.$var,
 *     STAGE_OPERATOR.$ifvar,
 *     pipeline,
 *     values
 * );
 * }</pre>
 * 
 * <h2>Security Considerations</h2>
 * 
 * <p>The variable interpolation system includes security measures to prevent injection attacks:</p>
 * <ul>
 *   <li>{@link org.restheart.mongodb.utils.StagesInterpolator#shouldNotContainOperators(BsonValue)}
 *       prevents clients from injecting MongoDB operators through variables</li>
 *   <li>All variable values are properly escaped and validated</li>
 *   <li>Integration with RESTHeart's permission system ensures access control is maintained</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * 
 * <p>Most classes in this package are thread-safe:</p>
 * <ul>
 *   <li>{@code ConnectionChecker} uses thread-safe caching</li>
 *   <li>{@code RSOps} is immutable (record class)</li>
 *   <li>Interpolator utilities use stateless methods</li>
 *   <li>{@code ClientSessionImpl} requires external synchronization for mutable state</li>
 * </ul>
 * 
 * @since 4.0
 */
package org.restheart.mongodb;