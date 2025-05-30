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
package org.restheart.plugins;

import io.undertow.server.HttpServerExchange;
import java.util.function.Consumer;
import java.util.function.Function;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;

/**
 * Specialized Service interface for handling binary data through byte array requests and responses.
 * <p>
 * ByteArrayService is a convenience interface that extends the base {@link Service} interface
 * with pre-configured byte array request and response handling. It automatically provides the
 * correct initializers and factory methods for {@link ByteArrayRequest} and {@link ByteArrayResponse}
 * objects, making it ideal for services that need to process binary data, files, or any
 * non-text content.
 * </p>
 * <p>
 * This interface is ideal for services that:
 * <ul>
 *   <li><strong>Handle file uploads</strong> - Process binary file uploads and downloads</li>
 *   <li><strong>Process binary data</strong> - Work with images, documents, or media files</li>
 *   <li><strong>Implement file storage</strong> - Provide file storage and retrieval services</li>
 *   <li><strong>Handle raw data streams</strong> - Process unstructured binary content</li>
 *   <li><strong>Serve static content</strong> - Deliver binary assets like images or documents</li>
 * </ul>
 * </p>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "fileService",
 *     description = "Binary file upload and download service",
 *     defaultURI = "/api/files",
 *     secure = true
 * )
 * public class FileService implements ByteArrayService {
 *     &#64;Inject("config")
 *     private Map&lt;String, Object&gt; config;
 *     
 *     &#64;Override
 *     public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
 *         switch (request.getMethod()) {
 *             case GET:
 *                 handleFileDownload(request, response);
 *                 break;
 *             case POST:
 *                 handleFileUpload(request, response);
 *                 break;
 *             case DELETE:
 *                 handleFileDelete(request, response);
 *                 break;
 *             default:
 *                 response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
 *         }
 *     }
 *     
 *     private void handleFileUpload(ByteArrayRequest request, ByteArrayResponse response) {
 *         byte[] fileData = request.getContent();
 *         String fileName = request.getHeader("X-File-Name");
 *         String contentType = request.getContentType();
 *         
 *         // Save file to storage
 *         String fileId = saveFile(fileName, contentType, fileData);
 *         
 *         // Return file metadata
 *         response.setContentTypeAsJson();
 *         response.setContent(JsonObject.of("fileId", fileId, "size", fileData.length));
 *         response.setStatusCode(HttpStatus.SC_CREATED);
 *     }
 *     
 *     private void handleFileDownload(ByteArrayRequest request, ByteArrayResponse response) {
 *         String fileId = request.getPathParameter("fileId");
 *         FileMetadata metadata = getFileMetadata(fileId);
 *         byte[] fileData = loadFile(fileId);
 *         
 *         response.setContentType(metadata.getContentType());
 *         response.setHeader("Content-Disposition", "attachment; filename=\"" + metadata.getFileName() + "\"");
 *         response.setContent(fileData);
 *         response.setStatusCode(HttpStatus.SC_OK);
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Content Type Handling:</strong><br>
 * ByteArrayService is content-type agnostic and can handle any binary content:
 * <ul>
 *   <li>Images (image/jpeg, image/png, image/gif, etc.)</li>
 *   <li>Documents (application/pdf, application/msword, etc.)</li>
 *   <li>Media files (video/mp4, audio/mpeg, etc.)</li>
 *   <li>Archives (application/zip, application/gzip, etc.)</li>
 *   <li>Custom binary formats</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Request/Response Objects:</strong><br>
 * The interface provides automatic initialization of:
 * <ul>
 *   <li>{@link ByteArrayRequest} - Provides access to binary request content as byte arrays</li>
 *   <li>{@link ByteArrayResponse} - Enables setting binary response content and appropriate headers</li>
 * </ul>
 * These objects handle binary data efficiently without unnecessary conversions or encoding.
 * </p>
 * <p>
 * <strong>Performance Considerations:</strong><br>
 * When working with large binary data:
 * <ul>
 *   <li>Consider streaming for very large files</li>
 *   <li>Implement appropriate content length headers</li>
 *   <li>Use proper caching strategies for static content</li>
 *   <li>Consider compression for compressible binary formats</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Service
 * @see ByteArrayRequest
 * @see ByteArrayResponse
 * @see RegisterPlugin
 */
public interface ByteArrayService extends Service<ByteArrayRequest, ByteArrayResponse> {
    /**
     * Returns the initializer function for ByteArrayRequest objects.
     * <p>
     * This method provides the Consumer function that initializes ByteArrayRequest
     * objects for incoming HTTP exchanges. The initializer sets up the request
     * object with binary data handling capabilities and attaches it to the exchange
     * for later retrieval by the service implementation.
     * </p>
     * <p>
     * The ByteArrayRequest initializer:
     * <ul>
     *   <li>Creates a new ByteArrayRequest instance for the exchange</li>
     *   <li>Configures binary content reading and buffering</li>
     *   <li>Sets up content length validation</li>
     *   <li>Attaches the request object to the exchange context</li>
     * </ul>
     * </p>
     *
     * @return Consumer function that initializes ByteArrayRequest objects
     */
    @Override
    default Consumer<HttpServerExchange> requestInitializer() {
        return e -> ByteArrayRequest.init(e);
    }

    /**
     * Returns the initializer function for ByteArrayResponse objects.
     * <p>
     * This method provides the Consumer function that initializes ByteArrayResponse
     * objects for outgoing HTTP exchanges. The initializer sets up the response
     * object with binary data handling capabilities and attaches it to the exchange
     * for later use by the service implementation.
     * </p>
     * <p>
     * The ByteArrayResponse initializer:
     * <ul>
     *   <li>Creates a new ByteArrayResponse instance for the exchange</li>
     *   <li>Configures binary content writing capabilities</li>
     *   <li>Sets up appropriate content headers for binary data</li>
     *   <li>Attaches the response object to the exchange context</li>
     * </ul>
     * </p>
     *
     * @return Consumer function that initializes ByteArrayResponse objects
     */
    @Override
    default Consumer<HttpServerExchange> responseInitializer() {
        return e -> ByteArrayResponse.init(e);
    }

    /**
     * Returns the factory function for retrieving ByteArrayRequest objects from exchanges.
     * <p>
     * This method provides the Function that retrieves the ByteArrayRequest object
     * that was previously attached to an HTTP exchange during the initialization
     * phase. The service implementation uses this function to access the binary
     * request data.
     * </p>
     * <p>
     * The ByteArrayRequest factory:
     * <ul>
     *   <li>Retrieves the ByteArrayRequest instance from the exchange context</li>
     *   <li>Provides access to binary request content as byte arrays</li>
     *   <li>Enables efficient handling of binary data without encoding conversions</li>
     *   <li>Supports lazy loading for optimal memory usage</li>
     * </ul>
     * </p>
     *
     * @return Function that retrieves ByteArrayRequest objects from exchanges
     */
    @Override
    default Function<HttpServerExchange, ByteArrayRequest> request() {
        return e -> ByteArrayRequest.of(e);
    }

    /**
     * Returns the factory function for retrieving ByteArrayResponse objects from exchanges.
     * <p>
     * This method provides the Function that retrieves the ByteArrayResponse object
     * that was previously attached to an HTTP exchange during the initialization
     * phase. The service implementation uses this function to set binary response
     * content and configure response headers.
     * </p>
     * <p>
     * The ByteArrayResponse factory:
     * <ul>
     *   <li>Retrieves the ByteArrayResponse instance from the exchange context</li>
     *   <li>Provides methods for setting binary response content</li>
     *   <li>Enables response header configuration for binary data</li>
     *   <li>Supports efficient binary data transmission</li>
     * </ul>
     * </p>
     *
     * @return Function that retrieves ByteArrayResponse objects from exchanges
     */
    @Override
    default Function<HttpServerExchange, ByteArrayResponse> response() {
        return e -> ByteArrayResponse.of(e);
    }
}
