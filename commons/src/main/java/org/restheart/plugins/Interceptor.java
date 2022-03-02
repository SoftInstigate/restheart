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
package org.restheart.plugins;

import org.restheart.exchange.Request;
import org.restheart.exchange.Response;

/**
 * Interceptors allow to snoop and modify requests and responses at different
 * stages of the request lifecycle as defined by the interceptPoint parameter of
 * the annotation RegisterPlugin. Seeorg.restheart.plugins.InterceptPoint
 *
 * An interceptor can intercept either proxied requests or requests handled by
 * Services.
 *
 * An interceptor can intercept requests handled by a Service when its request
 * and response types are equal to the ones declared by the Service.
 *
 * An interceptor can intercept a proxied request, when its request and response
 * types extends BufferedRequest and BufferedResponse.
 *
 * See https://restheart.org/docs/plugins/core-plugins/#interceptors
 *
 * @param <R> Request the request type
 * @param <S> Response the response type
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Interceptor<R extends Request<?>, S extends Response<?>> extends ConfigurablePlugin, ExchangeTypeResolver<R, S> {
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
