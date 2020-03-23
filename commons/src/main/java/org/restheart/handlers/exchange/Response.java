/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.handlers.exchange;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import java.util.Map;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T>
 */
public abstract class Response<T> extends AbstractExchange<T> {

    private static final AttachmentKey<Integer> STATUS_CODE
            = AttachmentKey.create(Integer.class);

    private static final AttachmentKey<Map<String, String>> MDC_CONTEXT_KEY
            = AttachmentKey.create(Map.class);

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
        if (getWrappedExchange().getResponseHeaders().get(Headers.CONTENT_TYPE) != null) {
            return getWrappedExchange().getResponseHeaders().get(Headers.CONTENT_TYPE)
                    .getFirst();
        } else {
            return null;
        }
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setContentType(String responseContentType) {
        getWrappedExchange().getResponseHeaders().put(Headers.CONTENT_TYPE,
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
        var wrappedExchange = getWrappedExchange();

        if (wrappedExchange == null
                || wrappedExchange.getAttachment(STATUS_CODE) == null) {
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
     * @return the inError
     */
    public boolean isInError() {
        return getWrappedExchange().getAttachment(IN_ERROR_KEY) != null
                && (boolean) getWrappedExchange().getAttachment(IN_ERROR_KEY);

    }

    /**
     * Logging MDC Context is bind to the thread context. In case of a thread
     * switch it must be restored from this exchange attachment using
     * MDC.setContextMap()
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
     * @param inError the inError to set
     */
    public void setInError(boolean inError) {
        getWrappedExchange().putAttachment(IN_ERROR_KEY, inError);
    }
}
