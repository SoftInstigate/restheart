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
 * Utilities for dynamic MongoDB query and aggregation pipeline construction.
 * 
 * <p>This package provides powerful utilities for creating dynamic, parameterized MongoDB queries
 * and aggregation pipelines. It enables RESTHeart to support flexible, secure API endpoints where
 * clients can provide parameters that are safely interpolated into pre-defined query templates.</p>
 * 
 * <h2>Core Components</h2>
 * 
 * <h3>{@link org.restheart.mongodb.utils.VarsInterpolator}</h3>
 * <p>The fundamental variable interpolation engine that replaces variable placeholders in BSON
 * documents with actual values:</p>
 * <ul>
 *   <li>Supports {@code $var} operator for queries and aggregations</li>
 *   <li>Supports {@code $arg} operator for GraphQL mappings</li>
 *   <li>Handles optional default values for missing variables</li>
 *   <li>Recursively processes nested documents and arrays</li>
 * </ul>
 * 
 * <h3>{@link org.restheart.mongodb.utils.StagesInterpolator}</h3>
 * <p>Advanced interpolation for aggregation pipelines with conditional logic:</p>
 * <ul>
 *   <li>Extends VarsInterpolator functionality for aggregation stages</li>
 *   <li>Supports conditional stages with {@code $ifvar} and {@code $ifarg} operators</li>
 *   <li>Provides security checks against operator injection</li>
 *   <li>Injects system variables like user info and permissions</li>
 * </ul>
 * 
 * <h2>Variable Interpolation Syntax</h2>
 * 
 * <h3>Simple Variables</h3>
 * <pre>{@code
 * // Template
 * { "name": { "$var": "userName" } }
 * 
 * // With values: { "userName": "John" }
 * // Result: { "name": "John" }
 * }</pre>
 * 
 * <h3>Variables with Defaults</h3>
 * <pre>{@code
 * // Template
 * { 
 *   "status": { "$var": ["status", "active"] },
 *   "limit": { "$var": ["limit", 10] }
 * }
 * 
 * // With values: { "status": "pending" }
 * // Result: { "status": "pending", "limit": 10 }
 * }</pre>
 * 
 * <h3>Nested Variable Access</h3>
 * <pre>{@code
 * // Values can use dot notation
 * { "user.id": { "$var": "userId" } }
 * 
 * // Variable values can access nested fields
 * // With values: { "user.name": "John" }
 * { "name": { "$var": "user.name" } }
 * }</pre>
 * 
 * <h2>Conditional Aggregation Stages</h2>
 * 
 * <h3>Basic Conditional Stage</h3>
 * <pre>{@code
 * // Include stage only if variable exists
 * { "$ifvar": ["includeDetails", 
 *   { "$lookup": { 
 *     "from": "details",
 *     "localField": "_id",
 *     "foreignField": "parentId",
 *     "as": "details"
 *   }}
 * ]}
 * }</pre>
 * 
 * <h3>Conditional Stage with Else</h3>
 * <pre>{@code
 * // Different stages based on variable presence
 * { "$ifvar": ["sortBy",
 *   { "$sort": { "$var": "sortBy" } },      // if sortBy exists
 *   { "$sort": { "_id": 1 } }               // else default sort
 * ]}
 * }</pre>
 * 
 * <h3>Multiple Variable Conditions</h3>
 * <pre>{@code
 * // Stage included only if ALL variables exist
 * { "$ifvar": [["startDate", "endDate"],
 *   { "$match": { 
 *     "date": {
 *       "$gte": { "$var": "startDate" },
 *       "$lte": { "$var": "endDate" }
 *     }
 *   }}
 * ]}
 * }</pre>
 * 
 * <h2>System Variables</h2>
 * 
 * <p>StagesInterpolator automatically injects system variables:</p>
 * <ul>
 *   <li>{@code @page} - Current page number</li>
 *   <li>{@code @pagesize} - Items per page</li>
 *   <li>{@code @limit} - Same as pagesize</li>
 *   <li>{@code @skip} - Calculated skip value</li>
 *   <li>{@code @user} - Authenticated user information</li>
 *   <li>{@code @user.*} - Individual user properties</li>
 *   <li>{@code @mongoPermissions} - User's MongoDB permissions</li>
 *   <li>{@code @mongoPermissions.readFilter} - Read access filter</li>
 *   <li>{@code @mongoPermissions.writeFilter} - Write access filter</li>
 * </ul>
 * 
 * <h2>Security Features</h2>
 * 
 * <h3>Operator Injection Prevention</h3>
 * <pre>{@code
 * // This will throw SecurityException
 * BsonDocument values = new BsonDocument("filter", 
 *   new BsonDocument("$where", "malicious code"));
 * 
 * StagesInterpolator.shouldNotContainOperators(values);
 * }</pre>
 * 
 * <h3>Safe Variable Substitution</h3>
 * <p>All variable values are safely substituted without risk of:</p>
 * <ul>
 *   <li>NoSQL injection attacks</li>
 *   <li>Operator manipulation</li>
 *   <li>Query structure modification</li>
 * </ul>
 * 
 * <h2>Complete Example</h2>
 * 
 * <pre>{@code
 * // Aggregation pipeline template
 * BsonArray pipeline = BsonArray.parse("""
 * [
 *   { "$match": { "type": { "$var": "type" } } },
 *   { "$ifvar": ["dateRange",
 *     { "$match": { 
 *       "date": {
 *         "$gte": { "$var": "startDate" },
 *         "$lte": { "$var": "endDate" }
 *       }
 *     }}
 *   ]},
 *   { "$ifvar": ["includeStats",
 *     { "$group": {
 *       "_id": "$category",
 *       "count": { "$sum": 1 },
 *       "total": { "$sum": "$amount" }
 *     }},
 *     { "$project": {
 *       "category": 1,
 *       "amount": 1
 *     }}
 *   ]},
 *   { "$skip": { "$var": "@skip" } },
 *   { "$limit": { "$var": "@pagesize" } }
 * ]
 * """);
 * 
 * // Client-provided values
 * BsonDocument values = new BsonDocument()
 *   .append("type", new BsonString("order"))
 *   .append("includeStats", new BsonBoolean(true))
 *   .append("startDate", new BsonDateTime(startMillis))
 *   .append("endDate", new BsonDateTime(endMillis));
 * 
 * // System variables are injected automatically
 * StagesInterpolator.injectAvars(request, values);
 * 
 * // Interpolate pipeline
 * List<BsonDocument> stages = StagesInterpolator.interpolate(
 *   VAR_OPERATOR.$var,
 *   STAGE_OPERATOR.$ifvar,
 *   pipeline,
 *   values
 * );
 * }</pre>
 * 
 * <h2>Best Practices</h2>
 * 
 * <ul>
 *   <li>Always validate variable values before interpolation</li>
 *   <li>Use default values for optional parameters</li>
 *   <li>Leverage conditional stages for flexible pipelines</li>
 *   <li>Utilize system variables instead of hardcoding values</li>
 *   <li>Keep pipeline templates in configuration for maintainability</li>
 * </ul>
 * 
 * @since 4.0
 */
package org.restheart.mongodb.utils;