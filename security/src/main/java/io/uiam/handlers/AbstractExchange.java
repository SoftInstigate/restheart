/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.uiam.Bootstrapper;
import io.uiam.utils.BuffersUtils;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.slf4j.Logger;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class AbstractExchange {

    protected static Logger LOGGER;
    
    public static final int MAX_CONTENT_SIZE = 16 * 1024 * 1024; // 16byte

    public static final int MAX_BUFFERS;

    static {
        MAX_BUFFERS = 1 + (MAX_CONTENT_SIZE / (Bootstrapper.getConfiguration() != null
                ? Bootstrapper.getConfiguration().getBufferSize()
                : 1024));
    }

    protected static final JsonParser PARSER = new JsonParser();
    protected final HttpServerExchange wrapped;


    public AbstractExchange(HttpServerExchange exchange) {
        this.wrapped = exchange;
    }

    /**
     * @return the wrapped
     */
    public HttpServerExchange getWrapped() {
        return wrapped;
    }

    /**
     * the Json is saved in the exchange. if modified syncBufferedContent() must
     * be invoked
     *
     * @return the request body as Json
     * @throws java.io.IOException
     * @throws com.google.gson.JsonSyntaxException
     */
    public JsonElement getContentAsJson()
            throws IOException, JsonSyntaxException {
        if (!isContentAvailable()) {
            return null;
        }

        if (getWrapped().getAttachment(getBufferedJsonKey()) == null) {
            getWrapped().putAttachment(
                    getBufferedJsonKey(),
                    PARSER.parse(
                            BuffersUtils.toString(getContent(),
                                    Charset.forName("utf-8")))
            );
        }
        return getWrapped().getAttachment(getBufferedJsonKey());
    }

    /**
     * If request content is modified in a proxied request, synch modification
     * to BUFFERED_REQUEST_DATA. This is not required for PluggableService.
     *
     * @param exchange
     * @throws IOException
     */
    public void syncBufferedContent()
            throws IOException {
        if (wrapped.getAttachment(getBufferedJsonKey()) == null) {
            return;
        }

        ByteBuffer src = ByteBuffer.wrap(getContentAsJson().toString().getBytes());

        if (!isContentAvailable()) {
            wrapped.putAttachment(getBufferedDataKey(), 
                    new PooledByteBuffer[MAX_BUFFERS]);
        }

        PooledByteBuffer[] dest = getContent();

        int copied = BuffersUtils.transfer(src, dest, wrapped);

        setContentLength(copied);
    }

    protected abstract void setContentLength(int length);
    
    protected abstract AttachmentKey<PooledByteBuffer[]> getBufferedDataKey();
    
    protected abstract AttachmentKey<JsonElement> getBufferedJsonKey();

    public abstract String getContentType();

    public PooledByteBuffer[] getContent() {
        if (!isContentAvailable()) {
            throw new IllegalStateException("Response content is not available. "
                    + "Add a Response Inteceptor overriding requiresResponseContent() "
                    + "to return true in order to make the content available.");
        }

        return getWrapped().getAttachment(getBufferedDataKey());
    }

    public boolean isContentAvailable() {
        return null != getWrapped().getAttachment(getBufferedDataKey());

    }
    
    public boolean isJsonContentAvailable() {
        return null != getWrapped().getAttachment(getBufferedJsonKey());

    }

    /**
     * helper method to check if the request content is Json
     *
     * @return true if Content-Type request header is application/json
     */
    public boolean isContentTypeJson() {
        return "application/json".equals(getContentType());
    }

    /**
     * helper method to check if the request content is Xm
     *
     * @return true if Content-Type request header is application/xml or
     * text/xml
     */
    public boolean isContentTypeXml() {
        return "text/xml".equals(getContentType())
                || "application/xml".equals(getContentType());
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
}
