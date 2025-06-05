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
package org.restheart.plugins;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;

/**
 * Interceptor interface for handling binary data exchanges.
 * 
 * <p>ByteArrayInterceptor is a specialized interceptor that processes requests and responses
 * containing binary data (byte arrays). It extends the generic {@link Interceptor} interface
 * with {@link ByteArrayRequest} and {@link ByteArrayResponse} type parameters.</p>
 * 
 * <p>This interceptor is useful for:</p>
 * <ul>
 *   <li>Processing binary file uploads and downloads</li>
 *   <li>Implementing binary data transformations</li>
 *   <li>Adding binary data validation or compression</li>
 *   <li>Logging or auditing binary data exchanges</li>
 *   <li>Implementing security checks on binary content</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "binaryValidator",
 *     description = "Validates binary file uploads",
 *     interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH
 * )
 * public class BinaryValidatorInterceptor implements ByteArrayInterceptor {
 *     @Override
 *     public void handle(ByteArrayRequest request, ByteArrayResponse response) {
 *         byte[] content = request.getContent();
 *         if (content != null && content.length > MAX_SIZE) {
 *             response.setStatusCode(413); // Payload Too Large
 *         }
 *     }
 *     
 *     @Override
 *     public boolean resolve(ByteArrayRequest request, ByteArrayResponse response) {
 *         return request.isUpload();
 *     }
 * }
 * }</pre>
 * 
 * @see Interceptor
 * @see ByteArrayRequest
 * @see ByteArrayResponse
 * @see InterceptPoint
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface ByteArrayInterceptor extends Interceptor<ByteArrayRequest, ByteArrayResponse> {

}
