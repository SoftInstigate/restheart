/*
 * RESTHeart Common
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.handlers.exchange;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import java.io.IOException;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class Response<T> extends AbstractExchange<T> {

    private static final AttachmentKey<Integer> STATUS_CODE
            = AttachmentKey.create(Integer.class);

    protected Response(HttpServerExchange exchange) {
        super(exchange);
    }

    public static String getContentType(HttpServerExchange exchange) {
        return exchange.getResponseHeaders()
                .getFirst(Headers.CONTENT_TYPE);
    }

    /**
     * @return the responseContentType
     */
    @Override
    public String getContentType() {
        if (getWrapped().getResponseHeaders().get(Headers.CONTENT_TYPE) != null) {
            return getWrapped().getResponseHeaders().get(Headers.CONTENT_TYPE)
                    .getFirst();
        } else {
            return null;
        }
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setContentType(String responseContentType) {
        getWrapped().getResponseHeaders().put(Headers.CONTENT_TYPE,
                responseContentType);
    }

    /**
     * sets Content-Type=application/json
     */
    public void setContentTypeAsJson() {
        setContentType("application/json");
    }

    /**
     * @return the responseStatusCode of -1 if not set
     */
    public int getStatusCode() {
        return getWrapped() == null
                && getWrapped().getAttachment(STATUS_CODE) == null
                ? -1
                : getWrapped().getAttachment(STATUS_CODE);
    }

    /**
     * @param responseStatusCode the responseStatusCode to set
     */
    public void setStatusCode(int responseStatusCode) {
        getWrapped().putAttachment(STATUS_CODE, responseStatusCode);
    }

    /**
     * @return the inError
     */
    public boolean isInError() {
        return getWrapped().getAttachment(IN_ERROR_KEY) != null
                && getWrapped().getAttachment(IN_ERROR_KEY);

    }

    /**
     * @param inError the inError to set
     */
    public void setInError(boolean inError) {
        getWrapped().putAttachment(IN_ERROR_KEY, inError);
    }
}
