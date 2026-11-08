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
package org.restheart.plugins;

import io.undertow.server.HttpServerExchange;
import java.util.function.Consumer;
import java.util.function.Function;
import org.restheart.exchange.JsonResponse;
import org.restheart.exchange.MultipartRequest;

/**
 * Service that receives a {@code multipart/form-data} request, parsed into
 * Undertow's {@link io.undertow.server.handlers.form.FormData}, and replies with JSON.
 *
 * <p>Register it with {@code blocking = true}: request bodies larger than
 * {@code blocking-content-prebuffer-limit} are read from the blocking input stream.</p>
 *
 * <pre>{@code
 * @RegisterPlugin(name = "upload", description = "receives a file", blocking = true)
 * public class Upload implements MultipartService {
 *     public void handle(MultipartRequest request, JsonResponse response) throws Exception {
 *         var file = request.getContent().getFirst("file").getFileItem();
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.15.0
 */
public interface MultipartService extends Service<MultipartRequest, JsonResponse> {
    @Override
    default Consumer<HttpServerExchange> requestInitializer() {
        return e -> MultipartRequest.init(e);
    }

    @Override
    default Consumer<HttpServerExchange> responseInitializer() {
        return e -> JsonResponse.init(e);
    }

    @Override
    default Function<HttpServerExchange, MultipartRequest> request() {
        return e -> MultipartRequest.of(e);
    }

    @Override
    default Function<HttpServerExchange, JsonResponse> response() {
        return e -> JsonResponse.of(e);
    }
}
