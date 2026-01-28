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

package org.restheart.utils;

/**
 * Deprecated utility class for JSON operations.
 * 
 * <p>This class has been deprecated in favor of {@link BsonUtils}, which provides
 * the same functionality with better BSON integration and performance.</p>
 * 
 * <p><strong>Migration:</strong> Replace all usages of JsonUtils with BsonUtils.
 * All method signatures and behavior remain the same.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @deprecated Use {@link BsonUtils} instead for all JSON and BSON operations
 */
@Deprecated
public class JsonUtils extends BsonUtils {}
