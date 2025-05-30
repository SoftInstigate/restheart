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
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;

/**
 * Specialized Service interface that uses BsonRequest and BsonResponse.
 * 
 * This interface provides a convenient abstraction for services that work with BSON
 * (Binary JSON) data format. It extends the generic Service interface with specific
 * parameterization for BsonRequest and BsonResponse types, providing default
 * implementations for request and response initialization and factory methods.
 * 
 * Services implementing this interface automatically get proper BSON request/response
 * handling without needing to manually implement the initialization logic.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Service
 * @see BsonRequest
 * @see BsonResponse
 */
public interface BsonService extends Service<BsonRequest, BsonResponse> {
    /**
     * Provides the default request initializer for BSON requests.
     * 
     * This method returns a Consumer that initializes a BsonRequest from the
     * HttpServerExchange. The initialization prepares the exchange for BSON
     * request processing.
     * 
     * @return a Consumer that initializes BsonRequest from HttpServerExchange
     */
    @Override
    default Consumer<HttpServerExchange> requestInitializer() {
        return e -> BsonRequest.init(e);
    }

    /**
     * Provides the default response initializer for BSON responses.
     * 
     * This method returns a Consumer that initializes a BsonResponse from the
     * HttpServerExchange. The initialization prepares the exchange for BSON
     * response processing.
     * 
     * @return a Consumer that initializes BsonResponse from HttpServerExchange
     */
    @Override
    default Consumer<HttpServerExchange> responseInitializer() {
        return e -> BsonResponse.init(e);
    }

    /**
     * Provides the default request factory for BSON requests.
     * 
     * This method returns a Function that creates a BsonRequest instance from
     * an HttpServerExchange. The returned BsonRequest can be used to access
     * and manipulate BSON request data.
     * 
     * @return a Function that creates BsonRequest from HttpServerExchange
     */
    @Override
    default Function<HttpServerExchange, BsonRequest> request() {
        return e -> BsonRequest.of(e);
    }

    /**
     * Provides the default response factory for BSON responses.
     * 
     * This method returns a Function that creates a BsonResponse instance from
     * an HttpServerExchange. The returned BsonResponse can be used to set
     * and manipulate BSON response data.
     * 
     * @return a Function that creates BsonResponse from HttpServerExchange
     */
    @Override
    default Function<HttpServerExchange, BsonResponse> response() {
        return e -> BsonResponse.of(e);
    }
}
