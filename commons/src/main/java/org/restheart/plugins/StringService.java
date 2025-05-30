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
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;

/**
 * Specialized Service interface that uses StringRequest and StringResponse.
 * 
 * This interface provides a convenient abstraction for services that work with
 * string-based data. It extends the generic Service interface with specific
 * parameterization for StringRequest and StringResponse types, providing default
 * implementations for request and response initialization and factory methods.
 * 
 * Services implementing this interface automatically get proper string request/response
 * handling without needing to manually implement the initialization logic. This is
 * particularly useful for services that process text content, HTML, XML, or any
 * other string-based data formats.
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Service
 * @see StringRequest
 * @see StringResponse
 */
public interface StringService extends Service<StringRequest, StringResponse> {
    /**
     * Provides the default request initializer for string requests.
     * 
     * This method returns a Consumer that initializes a StringRequest from the
     * HttpServerExchange. The initialization prepares the exchange for string
     * request processing.
     * 
     * @return a Consumer that initializes StringRequest from HttpServerExchange
     */
    @Override
    default Consumer<HttpServerExchange> requestInitializer() {
        return e -> StringRequest.init(e);
    }

    /**
     * Provides the default response initializer for string responses.
     * 
     * This method returns a Consumer that initializes a StringResponse from the
     * HttpServerExchange. The initialization prepares the exchange for string
     * response processing.
     * 
     * @return a Consumer that initializes StringResponse from HttpServerExchange
     */
    @Override
    default Consumer<HttpServerExchange> responseInitializer() {
        return e -> StringResponse.init(e);
    }

    /**
     * Provides the default request factory for string requests.
     * 
     * This method returns a Function that creates a StringRequest instance from
     * an HttpServerExchange. The returned StringRequest can be used to access
     * and manipulate string request data.
     * 
     * @return a Function that creates StringRequest from HttpServerExchange
     */
    @Override
    default Function<HttpServerExchange, StringRequest> request() {
        return e -> StringRequest.of(e);
    }

    /**
     * Provides the default response factory for string responses.
     * 
     * This method returns a Function that creates a StringResponse instance from
     * an HttpServerExchange. The returned StringResponse can be used to set
     * and manipulate string response data.
     * 
     * @return a Function that creates StringResponse from HttpServerExchange
     */
    @Override
    default Function<HttpServerExchange, StringResponse> response() {
        return e -> StringResponse.of(e);
    }
}
