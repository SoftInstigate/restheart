package org.restheart.exchange;

import java.io.IOException;
import org.restheart.utils.ChannelReader;
import io.undertow.server.HttpServerExchange;

/**
 * UninitializedRequest wraps the exchage and provides access to
 * request attributes (such as getPath()).
 *
 * The request content can be only accessed in raw format
 * with getRawContent() and setRawContent()
 *
 * Interceptors at intercePoint REQUEST_BEFORE_EXCHANGE_INIT
 * receive UninitializedRequest as request argument
 *
 */
public class UninitializedRequest extends ServiceRequest<Boolean> {
    public UninitializedRequest(HttpServerExchange exchange) {
        super(exchange, true);
    }

    @Override
    public Boolean getContent() {
        throw new IllegalStateException("the request is not initialized");
    }

    /**
     *
     * @return the request body raw bytes
     */
    public byte[] getRawContent() {
        try {
            return ChannelReader.readBytes(wrapped);
        } catch (IOException e) {
            throw new IllegalStateException("error getting raw request content", e);
        }
    }

    /**
     * overwrite the request body raw bytes
     * make sense to invoke it before request initialization
     *
     * @param data
     * @throws IOException
     */
    public void setRawContent(byte[] data) throws IOException {
        ByteArrayProxyRequest.of(wrapped).writeContent(data);
    }
}
