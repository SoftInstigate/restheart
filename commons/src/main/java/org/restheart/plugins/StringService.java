/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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
 *
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
