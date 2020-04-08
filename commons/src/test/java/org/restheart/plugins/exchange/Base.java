/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.plugins.exchange;

import com.google.common.reflect.TypeToken;
import io.undertow.server.HttpServerExchange;
import java.lang.reflect.Type;
import java.util.function.Consumer;
import java.util.function.Function;
import org.restheart.handlers.exchange.BufferedRequest;
import org.restheart.handlers.exchange.BufferedResponse;
import org.restheart.handlers.exchange.Request;
import org.restheart.handlers.exchange.Response;
import org.restheart.plugins.HandlingPlugin;
import org.restheart.plugins.Service;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
interface Plugin {
}

interface Proxy<R extends BufferedRequest<?>, S extends BufferedResponse<?>> extends HandlingPlugin<R, S> {
}

interface PluginTypeResolver<P extends HandlingPlugin<?, ?>> {
    default Type serviceType() {
        var typeToken = new TypeToken<P>(getClass()) {
        };

        return typeToken.getType();
    }
}

class MyService implements Service<DummyRequest, DummyResponse> {
    @Override
    public void handle(DummyRequest request, DummyResponse response) throws Exception {
        System.out.println("*** MyService.handle(" + request.getPath() + ")");
    }

    @Override
    public Function<HttpServerExchange, DummyRequest> request() {
        return e -> new DummyRequest(e);
    }

    @Override
    public Function<HttpServerExchange, DummyResponse> response() {
        return e -> new DummyResponse(e);
    }

    @Override
    public Consumer<HttpServerExchange> requestInitializer() {
        return e -> new DummyRequest(e);
    }
    
    @Override
    public Consumer<HttpServerExchange> responseInitializer() {
        return e -> new DummyResponse(e);
    }
}

class DummyResponse extends Response<String> {
    public DummyResponse(HttpServerExchange e) {
        super(e);
    }

    @Override
    public String readContent() {
        return "dummy!";
    }

    @Override
    public void setInError(int code, String message, Throwable t) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}

class DummyRequest extends Request<String> {
    public DummyRequest(HttpServerExchange e) {
        super(e);
    }
}
