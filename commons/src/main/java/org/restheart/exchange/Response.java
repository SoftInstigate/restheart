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
package org.restheart.exchange;

import java.util.Map;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 *
 * The root class for implementing a Response providing the implementation for
 * common methods
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> generic type
 */
public abstract class Response<T> extends Exchange<T> {
    private static final AttachmentKey<Integer> STATUS_CODE = AttachmentKey.create(Integer.class);

    private static final AttachmentKey<Map<String, String>> MDC_CONTEXT_KEY = AttachmentKey.create(Map.class);

    protected Response(HttpServerExchange exchange) {
        super(exchange);
    }

    @SuppressWarnings("rawtypes")
    public static Response of(HttpServerExchange exchange) {
        var pi = Request.pipelineInfo(exchange);;

        if (pi.getType() == PipelineInfo.PIPELINE_TYPE.SERVICE) {
            return ServiceResponse.of(exchange);
        } else {
            return ByteArrayProxyResponse.of(exchange);
        }
    }

    public static String getContentType(HttpServerExchange exchange) {
        return exchange.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    /**
     * @return the responseContentType
     */
    @Override
    public String getContentType() {
        if (getHeaders().get(Headers.CONTENT_TYPE) != null) {
            return getHeaders().get(Headers.CONTENT_TYPE).getFirst();
        } else {
            return null;
        }
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setContentType(String responseContentType) {
        setHeader(Headers.CONTENT_TYPE, responseContentType);
    }

    /**
     * sets Content-Type=application/json
     */
    public void setContentTypeAsJson() {
        setContentType(Exchange.JSON_MEDIA_TYPE);
    }

    /**
     * @return the responseStatusCode of -1 if not set
     */
    public int getStatusCode() {
        var wrappedExchange = getWrappedExchange();

        if (wrappedExchange == null || wrappedExchange.getAttachment(STATUS_CODE) == null) {
            return -1;
        } else {
            return wrappedExchange.getAttachment(STATUS_CODE);
        }
    }

    /**
     * @param responseStatusCode the responseStatusCode to set
     */
    public void setStatusCode(int responseStatusCode) {
        getWrappedExchange().putAttachment(STATUS_CODE, responseStatusCode);
    }

    /**
     *
     * @return the response headers
     */
    public HeaderMap getHeaders() {
        return wrapped.getResponseHeaders();
    }

    /**
     * note: an header can have multiple values. This only returns the first one.
     * use getHeaders() to get all the header's values
     *
     * @param name the name of the header to return
     * @return the first value of the response header
     */
    public String getHeader(HttpString name) {
        return getHeaders().getFirst(name);
    }

    /**
     * note: an header can have multiple values. This only returns the first one.
     * use getHeaders() to get all the header's values
     *
     * @param name the name of the header to return
     * @return the first value of the response header
     */
    public String getHeader(String name) {
        return getHeaders().getFirst(HttpString.tryFromString(name));
    }

    /**
     * note: an header can have multiple values. This sets the given value clearing
     * existing ones. use getHeaders().add(value) to add the value without clearing.
     *
     * @param name the name of the header to return
     */
    public void setHeader(HttpString name, String value) {
        if (getHeaders().get(name) == null) {
            getHeaders().put(name, value);
        } else {
            getHeaders().get(name).clear();
            getHeaders().get(name).add(value);
        }
    }

    /**
     * note: an header can have multiple values. This sets the given value clearing
     * existing ones. use getHeaders().add(value) to add the value without clearing.
     *
     * @param name the name of the header to return
     */
    public void setHeader(String name, String value) {
        if (getHeaders().get(name) == null) {
            getHeaders().put(HttpString.tryFromString(name), value);
        } else {
            getHeaders().get(HttpString.tryFromString(name)).clear();
            getHeaders().get(HttpString.tryFromString(name)).add(value);
        }
    }

    /**
     * Logging MDC Context is bound to the thread. In case of a thread switch
     * it must be restored from response.getMDCContext() with MDC.setContextMap()
     *
     * @return the MDC Context
     */
    public Map<String, String> getMDCContext() {
        return getWrappedExchange().getAttachment(MDC_CONTEXT_KEY);
    }

    public void setMDCContext(Map<String, String> mdcCtx) {
        getWrappedExchange().putAttachment(MDC_CONTEXT_KEY, mdcCtx);
    }

    /**
     *
     * @param code
     * @param message
     * @param t       can be null
     */
    public abstract void setInError(int code, String message, Throwable t);

    /**
     *
     * @param code
     * @param message
     */
    public void setInError(int code, String message) {
        setInError(code, message, null);
    }
}
