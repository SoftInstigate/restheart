package org.restheart.accounts.util;

import com.google.gson.JsonObject;
import org.restheart.exchange.JsonResponse;

/**
 * Helper that produces consistent JSON error responses for all
 * restheart-accounts services.
 *
 * <p>Every error body follows one of two shapes:
 * <pre>
 * { "message": "...", "code": N }   // with numeric error code
 * { "message": "..." }              // without code
 * </pre>
 */
public final class Errors {

    private Errors() {
        // utility class — no instances
    }

    /**
     * Sets the HTTP status code and writes a JSON error body that includes
     * both a human-readable message and a numeric application error code.
     *
     * @param res        the outgoing {@link JsonResponse}
     * @param statusCode HTTP status code, e.g. {@code 400}
     * @param code       application-level error code
     * @param message    human-readable error description
     */
    public static void error(JsonResponse res, int statusCode, int code, String message) {
        res.setStatusCode(statusCode);
        var body = new JsonObject();
        body.addProperty("message", message);
        body.addProperty("code", code);
        res.setContent(body);
    }

    /**
     * Sets the HTTP status code and writes a JSON error body that contains
     * only a human-readable message (no numeric code).
     *
     * @param res        the outgoing {@link JsonResponse}
     * @param statusCode HTTP status code, e.g. {@code 404}
     * @param message    human-readable error description
     */
    public static void error(JsonResponse res, int statusCode, String message) {
        res.setStatusCode(statusCode);
        var body = new JsonObject();
        body.addProperty("message", message);
        res.setContent(body);
    }
}
