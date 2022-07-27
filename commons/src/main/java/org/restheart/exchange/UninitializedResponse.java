package org.restheart.exchange;

import java.util.function.Consumer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * UninitializedResponse wraps the exchage and allows
 * setting a customResponseInitializer
 *
 * Interceptors at intercePoint REQUEST_BEFORE_EXCHANGE_INIT
 * receive UninitializedResponse as response argument
 *
 */
public class UninitializedResponse extends ServiceResponse<Object> {
    static final AttachmentKey<Consumer<HttpServerExchange>> CUSTOM_RESPONSE_INITIALIZER_KEY = AttachmentKey.create(Consumer.class);

    private UninitializedResponse(HttpServerExchange exchange) {
        super(exchange, true);
    }

    public static UninitializedResponse of(HttpServerExchange exchange) {
        return new UninitializedResponse(exchange);
    }

    /**
     * throws IllegalStateException
     *
     */
    @Override
    public Object getContent() {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * throws IllegalStateException
     *
     */
    @Override
    public void setContent(Object content) {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * throws IllegalStateException
     *
     */
    @Override
    public String readContent() {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * throws IllegalStateException
     *
     */
    @Override
    public void setInError(int code, String message, Throwable t) {
        throw new IllegalStateException("the response is not initialized");
    }

    /**
     * If a customResponseInitializer is set (not null), the ServiceExchangeInitializer will
     * delegate to customResponseInitializer.accept(exchange) the responsability to initialize the response
     *
     * @param customSender
     */
    public void setCustomResponseInitializer(Consumer<HttpServerExchange> customResponseInitializer) {
        this.wrapped.putAttachment(CUSTOM_RESPONSE_INITIALIZER_KEY, customResponseInitializer);
    }

    /**
     *
     * @return the custom Consumer used to initialize the response in place of the Service default one
     */
    public Consumer<HttpServerExchange> customResponseInitializer() {
        return this.wrapped.getAttachment(CUSTOM_RESPONSE_INITIALIZER_KEY);
    }
}
