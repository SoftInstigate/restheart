package org.restheart.exchange;

import java.io.IOException;
import java.util.function.Consumer;

import org.restheart.utils.ChannelReader;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

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
public class UninitializedRequest extends ServiceRequest<Object> {
    static final AttachmentKey<Consumer<HttpServerExchange>> CUSTOM_REQUEST_INITIALIZER_KEY = AttachmentKey.create(Consumer.class);

    private UninitializedRequest(HttpServerExchange exchange) {
        super(exchange, true);
    }

    public static UninitializedRequest of(HttpServerExchange exchange) {
        return new UninitializedRequest(exchange);
    }

    /**
     * throws IllegalStateException
     *
     * the content can only be retrieved in raw format, use getRawContent()
     */
    @Override
    public Object getContent() {
        throw new IllegalStateException("the request is not initialized");
    }

    /**
     * throws IllegalStateException
     *
     * the content can only be set in raw format, use setRawContent()
     */
    @Override
    public void setContent(Object content) {
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

    /**
     * If a customRequestInitializer is set (not null), the ServiceExchangeInitializer will
     * delegate to customRequestInitializer.accept(exchange) the responsability to initialize the request
     *
     * @param customSender
     */
    public void setCustomRequestInitializer(Consumer<HttpServerExchange> customRequestInitializer) {
        this.wrapped.putAttachment(CUSTOM_REQUEST_INITIALIZER_KEY, customRequestInitializer);
    }

    /**
     *
     * @return the custom Consumer used to initialize the request in place of the Service default one
     */
    public Consumer<HttpServerExchange> customRequestInitializer() {
        return this.wrapped.getAttachment(CUSTOM_REQUEST_INITIALIZER_KEY);
    }
}
