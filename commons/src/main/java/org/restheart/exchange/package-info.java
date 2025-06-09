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
 * HTTP exchange abstraction layer for RESTHeart request and response handling.
 * 
 * <p>This package provides a comprehensive set of classes for handling HTTP exchanges
 * in RESTHeart. It abstracts the underlying HTTP server implementation and provides
 * type-safe request and response objects for different content types and use cases.</p>
 * 
 * <h2>Core Concepts</h2>
 * <ul>
 *   <li>{@link org.restheart.exchange.Exchange} - Base abstraction for HTTP exchanges</li>
 *   <li>{@link org.restheart.exchange.Request} - Generic request interface</li>
 *   <li>{@link org.restheart.exchange.Response} - Generic response interface</li>
 *   <li>{@link org.restheart.exchange.ServiceRequest} - Base class for service requests</li>
 *   <li>{@link org.restheart.exchange.ServiceResponse} - Base class for service responses</li>
 * </ul>
 * 
 * <h2>Content Type Specific Implementations</h2>
 * <ul>
 *   <li><strong>JSON:</strong> {@link org.restheart.exchange.JsonRequest}, 
 *       {@link org.restheart.exchange.JsonResponse}</li>
 *   <li><strong>BSON:</strong> {@link org.restheart.exchange.BsonRequest}, 
 *       {@link org.restheart.exchange.BsonResponse}</li>
 *   <li><strong>String:</strong> {@link org.restheart.exchange.StringRequest}, 
 *       {@link org.restheart.exchange.StringResponse}</li>
 *   <li><strong>ByteArray:</strong> {@link org.restheart.exchange.ByteArrayRequest}, 
 *       {@link org.restheart.exchange.ByteArrayResponse}</li>
 *   <li><strong>GraphQL:</strong> {@link org.restheart.exchange.GraphQLRequest}, 
 *       {@link org.restheart.exchange.GraphQLResponse}</li>
 * </ul>
 * 
 * <h2>MongoDB-Specific Exchanges</h2>
 * <ul>
 *   <li>{@link org.restheart.exchange.MongoRequest} - MongoDB operation requests</li>
 *   <li>{@link org.restheart.exchange.MongoResponse} - MongoDB operation responses</li>
 *   <li>{@link org.restheart.exchange.BsonFromCsvRequest} - CSV to BSON conversion requests</li>
 * </ul>
 * 
 * <h2>Proxy Support</h2>
 * <ul>
 *   <li>{@link org.restheart.exchange.ProxyRequest} - Base proxy request interface</li>
 *   <li>{@link org.restheart.exchange.ProxyResponse} - Base proxy response interface</li>
 *   <li>{@link org.restheart.exchange.JsonProxyRequest}, 
 *       {@link org.restheart.exchange.JsonProxyResponse} - JSON proxy exchanges</li>
 *   <li>{@link org.restheart.exchange.ByteArrayProxyRequest}, 
 *       {@link org.restheart.exchange.ByteArrayProxyResponse} - Binary proxy exchanges</li>
 * </ul>
 * 
 * <h2>Utility Classes</h2>
 * <ul>
 *   <li>{@link org.restheart.exchange.ExchangeKeys} - Common exchange attribute keys</li>
 *   <li>{@link org.restheart.exchange.PipelineInfo} - Request pipeline metadata</li>
 *   <li>{@link org.restheart.exchange.CORSHeaders} - CORS header management</li>
 *   <li>{@link org.restheart.exchange.BufferedExchange} - Buffered exchange handling</li>
 * </ul>
 * 
 * <h2>Exception Classes</h2>
 * <ul>
 *   <li>{@link org.restheart.exchange.BadRequestException} - 400 Bad Request errors</li>
 *   <li>{@link org.restheart.exchange.IllegalQueryParameterException} - Invalid query parameters</li>
 *   <li>{@link org.restheart.exchange.InvalidMetadataException} - Invalid metadata errors</li>
 *   <li>{@link org.restheart.exchange.QueryNotFoundException} - Query not found errors</li>
 *   <li>{@link org.restheart.exchange.QueryVariableNotBoundException} - Unbound query variables</li>
 *   <li>{@link org.restheart.exchange.UnsupportedDocumentIdException} - Invalid document ID errors</li>
 * </ul>
 * 
 * <h2>Special Purpose Classes</h2>
 * <ul>
 *   <li>{@link org.restheart.exchange.UninitializedRequest} - Placeholder for uninitialized requests</li>
 *   <li>{@link org.restheart.exchange.UninitializedResponse} - Placeholder for uninitialized responses</li>
 *   <li>{@link org.restheart.exchange.RawBodyAccessor} - Raw request body access</li>
 *   <li>{@link org.restheart.exchange.MongoRequestContentInjector} - MongoDB request content injection</li>
 * </ul>
 * 
 * <p>The exchange abstraction enables RESTHeart to handle different content types uniformly
 * while providing type-safe access to request and response data. This design allows plugins
 * and services to work with strongly-typed data without dealing with low-level HTTP details.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
package org.restheart.exchange;