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
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;

/**
 * Service interface for handling binary data operations.
 * 
 * <p>ByteArrayService is a specialized service that processes HTTP requests and responses
 * containing binary data (byte arrays). It extends the generic {@link Service} interface
 * with {@link ByteArrayRequest} and {@link ByteArrayResponse} type parameters.</p>
 * 
 * <p>This service type is designed for:</p>
 * <ul>
 *   <li>File upload and download endpoints</li>
 *   <li>Image processing services</li>
 *   <li>Binary data transformation APIs</li>
 *   <li>Document management systems</li>
 *   <li>Media streaming services</li>
 *   <li>Binary protocol implementations</li>
 * </ul>
 * 
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "files",
 *     description = "Binary file storage service"
 * )
 * public class FileService implements ByteArrayService {
 *     @Override
 *     public void handle(ByteArrayRequest request, ByteArrayResponse response) {
 *         if (request.isGet()) {
 *             // Retrieve and return file content
 *             byte[] fileContent = loadFile(request.getPath());
 *             response.setContent(fileContent);
 *             response.setContentType("application/octet-stream");
 *         } else if (request.isPost()) {
 *             // Save uploaded file
 *             byte[] content = request.getContent();
 *             saveFile(request.getPath(), content);
 *             response.setStatusCode(201); // Created
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h2>Default Methods</h2>
 * <p>This interface provides default implementations for:</p>
 * <ul>
 *   <li>{@link #requestInitializer()} - Initializes {@link ByteArrayRequest} from the exchange</li>
 *   <li>{@link #responseInitializer()} - Initializes {@link ByteArrayResponse} from the exchange</li>
 *   <li>{@link #request()} - Creates ByteArrayRequest instances</li>
 *   <li>{@link #response()} - Creates ByteArrayResponse instances</li>
 * </ul>
 * 
 * <h2>Content Type Handling</h2>
 * <p>ByteArrayService is content-type agnostic and can handle any binary format.
 * Common content types include:</p>
 * <ul>
 *   <li>application/octet-stream</li>
 *   <li>image/jpeg, image/png, image/gif</li>
 *   <li>application/pdf</li>
 *   <li>application/zip</li>
 *   <li>video/mp4</li>
 * </ul>
 * 
 * @see Service
 * @see ByteArrayRequest
 * @see ByteArrayResponse
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface ByteArrayService extends Service<ByteArrayRequest, ByteArrayResponse> {
    @Override
    default Consumer<HttpServerExchange> requestInitializer() {
        return e -> ByteArrayRequest.init(e);
    }

    @Override
    default Consumer<HttpServerExchange> responseInitializer() {
        return e -> ByteArrayResponse.init(e);
    }

    @Override
    default Function<HttpServerExchange, ByteArrayRequest> request() {
        return e -> ByteArrayRequest.of(e);
    }

    @Override
    default Function<HttpServerExchange, ByteArrayResponse> response() {
        return e -> ByteArrayResponse.of(e);
    }
}
