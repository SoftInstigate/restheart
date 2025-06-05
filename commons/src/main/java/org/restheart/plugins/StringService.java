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

import io.undertow.server.HttpServerExchange;
import java.util.function.Consumer;
import java.util.function.Function;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;

/**
 * Service interface for handling plain text operations.
 * 
 * <p>StringService is a specialized service that processes HTTP requests and responses
 * containing plain text data. It extends the generic {@link Service} interface with
 * {@link StringRequest} and {@link StringResponse} type parameters.</p>
 * 
 * <p>This service type is designed for:</p>
 * <ul>
 *   <li>Plain text APIs and endpoints</li>
 *   <li>Log file viewers and processors</li>
 *   <li>Configuration file servers</li>
 *   <li>Simple message or notification services</li>
 *   <li>Text transformation services</li>
 *   <li>CSV/TSV data endpoints</li>
 * </ul>
 * 
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "echo",
 *     description = "Simple echo service"
 * )
 * public class EchoService implements StringService {
 *     @Override
 *     public void handle(StringRequest request, StringResponse response) {
 *         if (request.isGet()) {
 *             response.setContent("Echo service is running");
 *         } else if (request.isPost()) {
 *             String input = request.getContent();
 *             response.setContent("Echo: " + input);
 *         }
 *         response.setContentType("text/plain; charset=utf-8");
 *     }
 * }
 * 
 * @RegisterPlugin(
 *     name = "logs",
 *     description = "Log viewer service"
 * )
 * public class LogViewerService implements StringService {
 *     @Override
 *     public void handle(StringRequest request, StringResponse response) {
 *         String logName = request.getPathParam("name");
 *         String logContent = readLogFile(logName);
 *         response.setContent(logContent);
 *         response.setContentTypeAsPlainText();
 *     }
 * }
 * }</pre>
 * 
 * <h2>Default Methods</h2>
 * <p>This interface provides default implementations for:</p>
 * <ul>
 *   <li>{@link #requestInitializer()} - Initializes {@link StringRequest} from the exchange</li>
 *   <li>{@link #responseInitializer()} - Initializes {@link StringResponse} from the exchange</li>
 *   <li>{@link #request()} - Creates StringRequest instances</li>
 *   <li>{@link #response()} - Creates StringResponse instances</li>
 * </ul>
 * 
 * <h2>Content Type</h2>
 * <p>StringService typically handles {@code text/plain} content, but can work with any
 * text-based MIME type. Common content types include:</p>
 * <ul>
 *   <li>text/plain - Plain text (default)</li>
 *   <li>text/csv - Comma-separated values</li>
 *   <li>text/tab-separated-values - Tab-separated values</li>
 *   <li>text/markdown - Markdown formatted text</li>
 *   <li>application/x-ndjson - Newline delimited JSON</li>
 * </ul>
 * 
 * <h2>Character Encoding</h2>
 * <p>The service handles text using UTF-8 encoding by default. Different encodings
 * can be specified via the Content-Type header's charset parameter.</p>
 * 
 * @see Service
 * @see StringRequest
 * @see StringResponse
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface StringService extends Service<StringRequest, StringResponse> {
    @Override
    default Consumer<HttpServerExchange> requestInitializer() {
        return e -> StringRequest.init(e);
    }

    @Override
    default Consumer<HttpServerExchange> responseInitializer() {
        return e -> StringResponse.init(e);
    }

    @Override
    default Function<HttpServerExchange, StringRequest> request() {
        return e -> StringRequest.of(e);
    }

    @Override
    default Function<HttpServerExchange, StringResponse> response() {
        return e -> StringResponse.of(e);
    }
}
