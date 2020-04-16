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
import org.restheart.exchange.AbstractRequest;
import org.restheart.exchange.AbstractResponse;
import org.restheart.exchange.BufferedJsonRequest;
import org.restheart.exchange.BufferedJsonResponse;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.ConfigurablePlugin;
import org.restheart.plugins.ExchangeTypeResolver;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class NewExchangeApproach1Test {

    public NewExchangeApproach1Test() {
    }

    @Test
    public void testGetRequestType() {
        var i = new OtherInterceptor();

        var type = i.requestType();

        Assert.assertEquals(type, BufferedJsonRequest.class);
    }

    /**
     * approach where the interceptor is applied to services that have the same
     * types of Request and Reponse
     */
    @Test
    public void approach1() {
        System.out.println("**** interceptor is applied only to services that have the same types of Request and Reponse");

        var srv = new MyService();
        Set<Interceptor> interceptors = new HashSet<>();

        interceptors.add(new MyInterceptor());
        interceptors.add(new OtherInterceptor());

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
                .filter(i -> i.requestType().equals(srv.requestType()) && i.responseType().equals(srv.responseType()))
                .filter(i -> i.resolve(srv.request().apply(e), srv.response().apply(e)))
                .forEach(i -> {
                    try {
                        i.handle(srv.request().apply(e), srv.response().apply(e));
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

interface Interceptor<R extends AbstractRequest<?>, S extends AbstractResponse<?>>
        extends ConfigurablePlugin, ExchangeTypeResolver<R, S> {
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

class MyInterceptor implements Interceptor<JsonRequest, JsonResponse> {
    @Override
    public void handle(JsonRequest request, JsonResponse response) throws Exception {
        System.out.println("MyInterceptor.handle() path " + request.getPath());
    }

    @Override
    public boolean resolve(JsonRequest request, JsonResponse response) {
        return "/foo".equalsIgnoreCase(request.getPath());
    }
}

class OtherInterceptor implements Interceptor<BufferedJsonRequest, BufferedJsonResponse> {
    @Override
    public void handle(BufferedJsonRequest request, BufferedJsonResponse response) throws Exception {
        System.out.println("OtherInterceptor.handle() path " + request.getPath());
    }

    @Override
    public boolean resolve(BufferedJsonRequest request, BufferedJsonResponse response) {
        return "/foo".equalsIgnoreCase(request.getPath());
    }
}
