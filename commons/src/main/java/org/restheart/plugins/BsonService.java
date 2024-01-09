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
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;

/**
 * Specialized Service interface that uses BsonRequest and BsonResponse
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface BsonService extends Service<BsonRequest, BsonResponse> {
    @Override
    default Consumer<HttpServerExchange> requestInitializer() {
        return e -> BsonRequest.init(e);
    }

    @Override
    default Consumer<HttpServerExchange> responseInitializer() {
        return e -> BsonResponse.init(e);
    }

    @Override
    default Function<HttpServerExchange, BsonRequest> request() {
        return e -> BsonRequest.of(e);
    }

    @Override
    default Function<HttpServerExchange, BsonResponse> response() {
        return e -> BsonResponse.of(e);
    }
}
