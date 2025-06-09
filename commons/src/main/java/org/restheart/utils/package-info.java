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
 * Utility classes providing common functionality for RESTHeart operations.
 * 
 * <p>This package contains a comprehensive collection of utility classes that support
 * various aspects of RESTHeart functionality, including:</p>
 * 
 * <ul>
 * <li><strong>BSON/JSON Operations:</strong> {@link org.restheart.utils.BsonUtils}, 
 *     {@link org.restheart.utils.GsonUtils}, {@link org.restheart.utils.JsonUtils}</li>
 * <li><strong>Network and URL Handling:</strong> {@link org.restheart.utils.NetUtils}, 
 *     {@link org.restheart.utils.URLUtils}</li>
 * <li><strong>Buffer and Channel Operations:</strong> {@link org.restheart.utils.BuffersUtils}, 
 *     {@link org.restheart.utils.ChannelReader}</li>
 * <li><strong>Plugin Management:</strong> {@link org.restheart.utils.PluginUtils}</li>
 * <li><strong>Request Validation:</strong> {@link org.restheart.utils.CheckersUtils}</li>
 * <li><strong>Logging and Output:</strong> {@link org.restheart.utils.LogUtils}</li>
 * <li><strong>Thread Management:</strong> {@link org.restheart.utils.ThreadsUtils}</li>
 * <li><strong>File System Monitoring:</strong> {@link org.restheart.utils.DirectoryWatcher}</li>
 * <li><strong>Data Structures:</strong> {@link org.restheart.utils.Pair}</li>
 * <li><strong>Lambda Utilities:</strong> {@link org.restheart.utils.LambdaUtils}</li>
 * <li><strong>Resource Management:</strong> {@link org.restheart.utils.CleanerUtils}</li>
 * <li><strong>Content Minification:</strong> {@link org.restheart.utils.Minify}</li>
 * <li><strong>HTTP Status Codes:</strong> {@link org.restheart.utils.HttpStatus}</li>
 * <li><strong>MongoDB Attachments:</strong> {@link org.restheart.utils.MongoServiceAttachments}</li>
 * </ul>
 * 
 * <p>These utilities are designed to be thread-safe and efficient, providing
 * reliable building blocks for RESTHeart's core functionality and plugin ecosystem.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
package org.restheart.utils;
