/*
 * RESTHeart Security
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
package org.restheart.security.handlers.exchange;

import org.restheart.security.Bootstrapper;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import java.io.IOException;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class AbstractExchange<T> {

    protected static Logger LOGGER;

    protected static final AttachmentKey<Boolean> IN_ERROR_KEY
            = AttachmentKey.create(Boolean.class);

    private static final AttachmentKey<Boolean> RESPONSE_INTERCEPTOR_EXECUTED
            = AttachmentKey.create(Boolean.class);

    public static final int MAX_CONTENT_SIZE = 16 * 1024 * 1024; // 16byte

    public static final int MAX_BUFFERS;

    static {
        MAX_BUFFERS = 1 + (MAX_CONTENT_SIZE / (Bootstrapper
                .getConfiguration() != null
                        ? Bootstrapper.getConfiguration().getBufferSize()
                        : 1024));
    }

    public enum METHOD {
        GET, POST, PUT, DELETE, PATCH, OPTIONS, OTHER
    }

    protected final HttpServerExchange wrapped;

    public AbstractExchange(HttpServerExchange exchange) {
        this.wrapped = exchange;
    }

    /**
     * @return the wrapped
     */
    protected HttpServerExchange getWrapped() {
        return wrapped;
    }

    public static boolean isInError(HttpServerExchange exchange) {
        return exchange.getAttachment(IN_ERROR_KEY) != null
                && exchange.getAttachment(IN_ERROR_KEY);
    }

    public static boolean isAuthenticated(HttpServerExchange exchange) {
        return exchange.getSecurityContext() != null
                && exchange.getSecurityContext().getAuthenticatedAccount() != null;
    }

    public static void setInError(HttpServerExchange exchange) {
        exchange
                .putAttachment(IN_ERROR_KEY, true);
    }

    public static boolean responseInterceptorsExecuted(HttpServerExchange exchange) {
        return exchange.getAttachment(RESPONSE_INTERCEPTOR_EXECUTED) != null
                && exchange.getAttachment(RESPONSE_INTERCEPTOR_EXECUTED);
    }

    public static void setResponseInterceptorsExecuted(HttpServerExchange exchange) {
        exchange
                .putAttachment(RESPONSE_INTERCEPTOR_EXECUTED, true);
    }

    public abstract T readContent() throws IOException;

    public abstract void writeContent(T content) throws IOException;

    protected abstract void setContentLength(int length);

    protected abstract AttachmentKey<PooledByteBuffer[]> getRawContentKey();

    public PooledByteBuffer[] getRawContent() {
        if (!isContentAvailable()) {
            throw new IllegalStateException("Response content is not available. "
                    + "Add a Response Inteceptor overriding requiresResponseContent() "
                    + "to return true in order to make the content available.");
        }

        return getWrapped().getAttachment(getRawContentKey());
    }

    public void setRawContent(PooledByteBuffer[] raw) {
        getWrapped().putAttachment(getRawContentKey(), raw);
    }

//    protected abstract AttachmentKey<T> getContentKey();
    public abstract String getContentType();

    public boolean isContentAvailable() {
        return null != getWrapped().getAttachment(getRawContentKey());

    }

    /**
     * helper method to check if the request content is Json
     *
     * @return true if Content-Type request header is application/json
     */
    public boolean isContentTypeJson() {
        return "application/json".equals(getContentType())
                || (getContentType() != null
                && getContentType().startsWith("application/json;"));
    }

    /**
     * helper method to check if the request content is Xm
     *
     * @return true if Content-Type request header is application/xml or
     * text/xml
     */
    public boolean isContentTypeXml() {
        return "text/xml".equals(getContentType())
                || (getContentType() != null
                && getContentType().startsWith("text/xml;"))
                || "application/xml".equals(getContentType())
                || (getContentType() != null
                && getContentType().startsWith("application/xml;"));
    }

    /**
     * helper method to check if the request content is text
     *
     * @return true if Content-Type request header starts with text/
     */
    public boolean isContentTypeText() {
        return getContentType() != null
                && getContentType().startsWith("text/");
    }

    /**
     * helper method to check if request is authenticated
     *
     * @return true if request is authenticated
     */
    public boolean isAuthenticated() {
        return getWrapped().getSecurityContext() != null
                && getWrapped().getSecurityContext().getAuthenticatedAccount() != null;
    }

    /**
     * helper method to check if authenticated account is in the specified role
     *
     * @return
     */
    public boolean isAccountInRole(String role) {
        if (!isAuthenticated()) {
            return false;
        } else if (getWrapped().getSecurityContext()
                .getAuthenticatedAccount()
                .getRoles() == null) {
            return false;
        } else {
            return getWrapped().getSecurityContext()
                    .getAuthenticatedAccount()
                    .getRoles().contains(role);
        }
    }
}
