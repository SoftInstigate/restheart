/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.spi;

import java.util.Optional;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.Response;
import org.slf4j.Logger;

/**
 * What an exception out of a plugin's {@code handle} becomes.
 *
 * <p>A typed error of the SDK ({@code BadRequestError}, {@code UnauthorizedError},
 * {@code ForbiddenError}, {@code NotFoundError}, {@code ConflictError}, or any error with a
 * numeric {@code status}) becomes a response with its status and its message. A
 * {@link BadRequestException} from the exchange, a body that does not parse, becomes 400. Any
 * other exception becomes 500, logged with the plugin name and the guest stack trace, and
 * nothing of it reaches the client.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public final class GuestErrors {
    /** The property the SDK error classes carry their status in. */
    static final String STATUS = "status";

    private GuestErrors() {
    }

    /**
     * Answers the request for an exception thrown by {@code handle}.
     *
     * @param t the exception
     * @param response the response to set
     * @param pluginName the plugin, for the log
     * @param logger where to log
     */
    public static void handle(Throwable t, Response<?> response, String pluginName, Logger logger) {
        var typed = typed(t);

        if (typed.isPresent()) {
            var e = typed.get();
            logger.debug("plugin {} answered {}: {}", pluginName, e.status(), e.message());
            response.setInError(e.status(), e.message());
            return;
        }

        if (t instanceof PolyglotException pe && pe.isGuestException()) {
            logger.error("Uncaught error in plugin {}: {}\n{}", pluginName, pe.getMessage(), guestStack(pe));
        } else {
            logger.error("Error in plugin {}", pluginName, t);
        }

        response.setInError(500, "Internal Server Error");
    }

    /**
     * The status and message of an exception that maps to a client-facing response.
     *
     * @param t the exception
     * @return the typed error, empty when the exception is an internal error
     */
    public static Optional<TypedError> typed(Throwable t) {
        if (t instanceof PolyglotException pe) {
            if (pe.isHostException()) {
                return typed(pe.asHostException());
            }

            var guest = pe.getGuestObject();

            if (guest != null && guest.hasMembers() && guest.hasMember(STATUS)) {
                var status = guest.getMember(STATUS);
                if (status != null && status.isNumber() && status.fitsInInt()) {
                    var code = status.asInt();
                    if (code >= 400 && code < 600) {
                        return Optional.of(new TypedError(code, message(guest, pe)));
                    }
                }
            }

            return Optional.empty();
        }

        if (t instanceof BadRequestException bre) {
            return Optional.of(new TypedError(bre.getStatusCode() > 0 ? bre.getStatusCode() : 400, bre.getMessage()));
        }

        if (t instanceof IllegalArgumentException iae && iae.getCause() instanceof BadRequestException bre) {
            return Optional.of(new TypedError(bre.getStatusCode() > 0 ? bre.getStatusCode() : 400, bre.getMessage()));
        }

        return Optional.empty();
    }

    private static String message(Value guest, PolyglotException pe) {
        if (guest.hasMember("message")) {
            var m = guest.getMember("message");
            if (m != null && m.isString()) {
                return m.asString();
            }
        }
        return pe.getMessage();
    }

    private static String guestStack(PolyglotException pe) {
        var sb = new StringBuilder();
        for (var frame : pe.getPolyglotStackTrace()) {
            if (frame.isGuestFrame()) {
                sb.append("    at ").append(frame).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * An error that maps to a response.
     *
     * @param status the status code
     * @param message the message for the client
     */
    public record TypedError(int status, String message) {
    }
}
