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

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.restheart.handlers.exchange.AbstractRequest;
import org.restheart.handlers.exchange.AbstractResponse;
import org.restheart.handlers.exchange.BufferedJsonRequest;
import org.restheart.handlers.exchange.JsonRequest;
import org.restheart.handlers.exchange.JsonResponse;
import org.restheart.plugins.ConfigurablePlugin;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class NewExchangeApproach2Test {

    public NewExchangeApproach2Test() {
    }

    @Test
    public void testGetRequestType() {
        var i = new OtherInterceptor();

        var type = i.requestType();

        Assert.assertEquals(type, BufferedJsonRequest.class);
    }

    /**
     * approach where the interceptor is applied only to the Service decalered
     * in its signature
     *
     */
    @Test
    public void approach2() {
        System.out.println("**** interceptor is applied only to the Service decalered in its signature");
        var srv = new MyService();
        Set<AltInterceptor> interceptors = new HashSet<>();

        interceptors.add(new MyAltInterceptor());

        HttpServerExchange e = mock(HttpServerExchange.class);

        when(e.getStatusCode()).thenReturn(200);
        when(e.getRequestMethod()).thenReturn(Methods.GET);
        when(e.getRequestPath()).thenReturn("/foo");

        System.out.println("Initializing request and response");
        srv.requestInitializer().accept(e);
        srv.responseInitializer().accept(e);

        System.out.println("Executing interceptors");

        interceptors
                .stream()
                .filter(i -> i.serviceType().equals(srv.getClass()))
                .filter(i -> i.resolve(srv.request().apply(e), srv.response().apply(e)))
                .forEach(i -> {
                    try {
                        i.handle(srv.request().apply(e), 
                                srv.response().apply(e));
                    } catch (Exception ex) {
                        System.out.println("Error executing interceptor: " + ex);
                    }
                });

        System.out.println("Handling request");

        try {
            srv.handle(srv.request().apply(e), srv.response().apply(e));
        } catch (Exception ex) {
            System.out.println("error executing service: " + ex);
        }
    }
}

interface AltInterceptor<P extends HandlingPlugin<R, S>, 
        R extends AbstractRequest<?>, 
        S extends AbstractResponse<?>>
        extends ConfigurablePlugin, PluginTypeResolver<P> {
    /**
     * handle the request
     *
     * @param request
     * @param response
     * @throws Exception
     */
    public void handle(final R request, final S response) throws Exception;

    /**
     *
     * @param request
     * @param response
     * @return true if the plugin must handle the request
     */
    public boolean resolve(final R request, final S response);
}

/**
 * This makes clear that the interceptor will intercept only request handled by
 * MyService
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
class MyAltInterceptor implements AltInterceptor<MyService, DummyRequest, DummyResponse> {
    @Override
    public void handle(DummyRequest request, DummyResponse response) throws Exception {
        System.out.println("MyAltInterceptor.handle() path " + request.getPath());
    }

    @Override
    public boolean resolve(DummyRequest request, DummyResponse response) {
        return "/foo".equalsIgnoreCase(request.getPath());
    }
}