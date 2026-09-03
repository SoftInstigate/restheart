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
 * JSON error body shape as {@link JsonResponse}.
 *
 * <p>The instance is never attached to the exchange, so several can be created
 * for the same exchange without conflict.
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
     * {@code {"msg": "...", "exception": "..."}}.
     *
     * @param code the HTTP status code to set (e.g., 401, 403)
     * @param message the error message to include in the response, or null
     * @param t an optional throwable whose message will be included in the response
     */
    @Override
    public void setInError(int code, String message, Throwable t) {
        setInError(true);
        setStatusCode(code);

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
