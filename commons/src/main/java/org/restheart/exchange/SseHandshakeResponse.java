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

package org.restheart.exchange;

import com.google.gson.JsonObject;

import io.undertow.server.HttpServerExchange;

/**
 * {@link ServiceResponse} implementation used to run {@code WildcardInterceptor}s
 * on the SSE handshake pipeline (see {@code PluginsRegistryImpl#plugSseService}).
 *
 * <p>Unlike {@link UninitializedRequest}/{@link UninitializedResponse} (used at
 * {@code REQUEST_BEFORE_EXCHANGE_INIT}, a stage where denying the request is
 * out of scope), this class gives {@link #setInError(int, String, Throwable)} a
 * real, working implementation: the SSE pipeline honors it at
 * {@code REQUEST_AFTER_AUTH} to reject a handshake (for instance, a
 * topic-authorization interceptor denying a subscription), following the same
 * JSON error body shape as {@link JsonResponse}, and setting the same
 * {@code application/json} content type.
 *
 * <p>The instance is never attached to the exchange under {@code ServiceResponse}'s own
 * {@code RESPONSE_KEY}, so several can be created for the same exchange without conflicting
 * with other handlers that look the response up that way. {@code SseWildcardInterceptorsExecutor}
 * keeps its two invocations (one at {@code REQUEST_BEFORE_AUTH}, one at
 * {@code REQUEST_AFTER_AUTH}) on the same instance for a given exchange, under its own
 * dedicated attachment key, so a denial raised before auth is still sent, with its status code
 * and body, after auth.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 * @see org.restheart.plugins.WildcardInterceptor
 * @see SseHandshakeRequest
 */
public class SseHandshakeResponse extends ServiceResponse<Object> {

    private SseHandshakeResponse(HttpServerExchange exchange) {
        super(exchange, true);
    }

    /**
     * Factory method to create an {@code SseHandshakeResponse} wrapping the
     * given HTTP exchange.
     *
     * @param exchange the HTTP server exchange to wrap
     * @return a new {@code SseHandshakeResponse} instance
     */
    public static SseHandshakeResponse of(HttpServerExchange exchange) {
        return new SseHandshakeResponse(exchange);
    }

    /**
     * Returns the string representation of the content set via
     * {@link #setInError(int, String, Throwable)}, or {@code null} if no
     * content has been set.
     *
     * @return the content as a string, or {@code null}
     */
    @Override
    public String readContent() {
        return content == null ? null : content.toString();
    }

    /**
     * Sets the response in an error state, following the same JSON error body
     * shape as {@link JsonResponse#setInError(int, String, Throwable)}:
     * {@code {"msg": "...", "exception": "..."}}, and setting the
     * {@code Content-Type} header to {@code application/json}.
     *
     * @param code the HTTP status code to set (e.g., 401, 403)
     * @param message the error message to include in the response, or null
     * @param t an optional throwable whose message will be included in the response
     */
    @Override
    public void setInError(int code, String message, Throwable t) {
        setInError(true);
        setStatusCode(code);
        setContentTypeAsJson();

        var resp = new JsonObject();

        if (message != null) {
            resp.addProperty("msg", message);
        }

        if (t != null) {
            resp.addProperty("exception", t.getMessage());
        }

        setContent(resp);
    }
}
