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

import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;

/**
 * Interceptor interface for handling text/plain data exchanges.
 * 
 * <p>StringInterceptor is a specialized interceptor that processes requests and responses
 * containing plain text data. It extends the generic {@link Interceptor} interface with
 * {@link StringRequest} and {@link StringResponse} type parameters.</p>
 * 
 * <p>This interceptor is ideal for:</p>
 * <ul>
 *   <li>Processing plain text documents and messages</li>
 *   <li>Implementing text transformations and filters</li>
 *   <li>Adding headers or metadata to text responses</li>
 *   <li>Validating text content format or structure</li>
 *   <li>Logging and monitoring text-based APIs</li>
 *   <li>Converting between text formats (e.g., CSV, TSV)</li>
 * </ul>
 * 
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "text-sanitizer",
 *     description = "Sanitizes text input",
 *     interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH
 * )
 * public class TextSanitizerInterceptor implements StringInterceptor {
 *     @Override
 *     public void handle(StringRequest request, StringResponse response) {
 *         String content = request.getContent();
 *         
 *         if (content != null) {
 *             // Remove potentially dangerous content
 *             String sanitized = content
 *                 .replaceAll("<script>.*?</script>", "")
 *                 .replaceAll("(?i)javascript:", "")
 *                 .trim();
 *             
 *             // Update the request with sanitized content
 *             request.setContent(sanitized);
 *         }
 *     }
 *     
 *     @Override
 *     public boolean resolve(StringRequest request, StringResponse response) {
 *         // Apply to all POST and PUT requests with text content
 *         return (request.isPost() || request.isPut()) 
 *             && "text/plain".equals(request.getContentType());
 *     }
 * }
 * }</pre>
 * 
 * <h2>Content Type</h2>
 * <p>StringInterceptor typically handles content with MIME type {@code text/plain},
 * but can process any text-based format. The actual content type should be checked
 * in the {@code resolve()} method if type-specific processing is needed.</p>
 * 
 * <h2>Character Encoding</h2>
 * <p>Text content is handled as Java strings (UTF-16 internally). The framework
 * handles character encoding conversion based on the Content-Type header's charset
 * parameter (defaults to UTF-8).</p>
 * 
 * @see Interceptor
 * @see StringRequest
 * @see StringResponse
 * @see InterceptPoint
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface StringInterceptor extends Interceptor<StringRequest, StringResponse> {

}
