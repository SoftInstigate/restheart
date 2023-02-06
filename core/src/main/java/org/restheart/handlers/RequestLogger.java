/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
package org.restheart.handlers;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.LocaleUtils;
import io.undertow.util.QueryParameterUtils;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.ansi;
import org.restheart.Bootstrapper;
import org.restheart.configuration.Configuration;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.exchange.JsonProxyRequest;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_HEADER;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestLogger extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLogger.class);

    private final Configuration configuration = Bootstrapper.getConfiguration();

    /**
     * Creates a new instance of RequestLoggerHandler
     *
     */
    public RequestLogger() {
        super();
    }

    /**
     * Creates a new instance of RequestLoggerHandler
     *
     * @param next
     */
    public RequestLogger(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (configuration.logging().requestsLogMode() > 0 && LOGGER.isInfoEnabled()) {
            dumpExchange(exchange, configuration.logging().requestsLogMode());
        }

        next(exchange);
    }

    /**
     * dumpExchange
     *
     * Log a complete dump of the HttpServerExchange (both Request and Response)
     *
     * @param exchange the HttpServerExchange
     * @param logLevel it can be 0, 1 or 2
     */
    protected void dumpExchange(HttpServerExchange exchange, Integer logLevel) {
        if (logLevel < 1) {
            return;
        }

        var request = JsonProxyRequest.of(exchange);

        final StringBuilder sb = new StringBuilder();
        final long start = request != null && request.getStartTime() != null
                ? request.getStartTime()
                : System.currentTimeMillis();

        if (logLevel == 1) {
            sb.append(exchange.getRequestMethod()).append(" ").append(exchange.getRequestURL());

            if (exchange.getQueryString() != null
                    && !exchange.getQueryString().isEmpty()) {
                try {
                    sb.append("?").append(URLDecoder.decode(exchange.getQueryString(),
                            QueryParameterUtils
                                    .getQueryParamEncoding(exchange)));
                } catch (UnsupportedEncodingException uee) {
                    sb.append("?").append(exchange.getQueryString());
                }
            }

            sb.append(" from ").append(exchange.getSourceAddress());
        } else if (logLevel >= 2) {
            sb.append("\n----------------------------REQUEST---------------------------\n");

            sb.append("               URI=").append(exchange.getRequestURI()).append("\n");

            var pb = request == null ? null :request.getPipelineInfo();

            if (pb != null) {
                sb.append("          servedBy=")
                        .append(pb.getType().name().toLowerCase())
                        .append(" ");

                if (pb.getName() != null) {
                    sb
                            .append("'")
                            .append(pb.getName())
                            .append("' ");
                }

                sb
                        .append("bound to '")
                        .append(pb.getUri())
                        .append("'\n");
            }

            sb.append(" characterEncoding=").append(exchange.getRequestHeaders().get(Headers.CONTENT_ENCODING))
                    .append("\n");
            sb.append("     contentLength=").append(exchange.getRequestContentLength()).append("\n");
            sb.append("       contentType=").append(exchange.getRequestHeaders().get(Headers.CONTENT_TYPE))
                    .append("\n");

            @SuppressWarnings("removal")
            Map<String, Cookie> cookies = exchange.getRequestCookies();
            if (cookies != null) {
                cookies.entrySet().stream().map((entry) -> entry.getValue()).forEach((cookie) -> {
                    sb.append("            cookie=").append(cookie.getName()).append("=").append(cookie.getValue())
                            .append("\n");
                });
            }
            for (HeaderValues header : exchange.getRequestHeaders()) {
                header.stream().forEach((value) -> {
                    if (header.getHeaderName() != null
                            && "Authorization".equalsIgnoreCase(header
                                    .getHeaderName().toString())) {
                        value = "**********";
                    }

                    sb.append("            header=").append(header.getHeaderName()).append("=").append(value)
                            .append("\n");
                });
            }
            sb.append("            locale=")
                    .append(LocaleUtils.getLocalesFromHeader(exchange.getRequestHeaders().get(Headers.ACCEPT_LANGUAGE)))
                    .append("\n");
            sb.append("            method=").append(exchange.getRequestMethod()).append("\n");
            Map<String, Deque<String>> pnames = exchange.getQueryParameters();
            pnames.entrySet().stream().map((entry) -> {
                String pname = entry.getKey();
                Iterator<String> pvalues = entry.getValue().iterator();
                sb.append("         parameter=");
                sb.append(pname);
                sb.append('=');
                while (pvalues.hasNext()) {
                    sb.append(pvalues.next());
                    if (pvalues.hasNext()) {
                        sb.append(", ");
                    }
                }
                return entry;
            }).forEach((_item) -> {
                sb.append("\n");
            });

            sb.append("          protocol=").append(exchange.getProtocol()).append("\n");
            sb.append("       queryString=").append(exchange.getQueryString()).append("\n");
            sb.append("        remoteAddr=").append(exchange.getSourceAddress()).append("\n");
            sb.append("        remoteHost=").append(exchange.getSourceAddress().getHostName()).append("\n");
            sb.append("            scheme=").append(exchange.getRequestScheme()).append("\n");
            sb.append("              host=").append(exchange.getRequestHeaders().getFirst(Headers.HOST)).append("\n");
            sb.append("        serverPort=").append(exchange.getDestinationAddress().getPort()).append("\n");
        }

        addExchangeCompleteListener(exchange, logLevel, sb, start);
    }

    private void addExchangeCompleteListener(HttpServerExchange exchange, Integer logLevel, final StringBuilder sb,
            final long start) {
        exchange.addExchangeCompleteListener(
                (final HttpServerExchange exchange1, final ExchangeCompletionListener.NextListener nextListener) -> {
                    if (logLevel < 1) {
                        return;
                    }

                    // restore MDC context
                    // MDC context is put in the thread context
                    // A thread switch in the request handling pipeline loses the MDC context.
                    // TracingInstrumentationHandler adds it to the
                    // exchange as an Attachment
                    var mdcCtx = ByteArrayProxyResponse.of(exchange).getMDCContext();
                    if (mdcCtx != null) {
                        MDC.setContextMap(mdcCtx);
                    }

                    // note sc is always null if this handler is chained before
                    // SecurityHandlerDispacher
                    final SecurityContext sc = exchange1.getSecurityContext();

                    if (logLevel == 1) {
                        sb.append(" =>").append(" status=");

                        if (exchange.getStatusCode() >= 300 && exchange.getStatusCode() != 304) {
                            sb.append(ansi().fg(RED).bold().a(exchange.getStatusCode()).reset().toString());
                        } else {
                            sb.append(ansi().fg(GREEN).bold().a(exchange.getStatusCode()).reset().toString());
                        }

                        sb.append(" elapsed=").append(System.currentTimeMillis() - start).append("ms")
                                .append(" contentLength=").append(exchange1.getResponseContentLength());

                        if (sc != null && sc.getAuthenticatedAccount() != null) {
                            sb.append(" ").append(sc.getAuthenticatedAccount().toString());
                        }
                    } else if (logLevel >= 2) {
                        sb.append("--------------------------RESPONSE--------------------------\n");
                        if (sc != null) {
                            if (sc.isAuthenticated()) {
                                sb.append("          authType=").append(sc.getMechanismName()).append("\n");
                                sb.append("          account=").append(sc.getAuthenticatedAccount().toString())
                                        .append("\n");
                            } else {
                                sb.append("          authType=none" + "\n");
                            }
                        }

                        sb.append("     contentLength=").append(exchange1.getResponseContentLength()).append("\n");
                        sb.append("       contentType=")
                                .append(exchange1.getResponseHeaders().getFirst(Headers.CONTENT_TYPE)).append("\n");

                        @SuppressWarnings("removal")
                        Map<String, Cookie> cookies1 = exchange1.getResponseCookies();
                        if (cookies1 != null) {
                            cookies1.values().stream().forEach((cookie) -> {
                                sb.append("            cookie=").append(cookie.getName()).append("=")
                                        .append(cookie.getValue()).append("; domain=").append(cookie.getDomain())
                                        .append("; path=").append(cookie.getPath()).append("\n");
                            });
                        }
                        for (HeaderValues header : exchange1.getResponseHeaders()) {
                            header.stream().forEach((value) -> {
                                if (header.getHeaderName() != null
                                        && AUTH_TOKEN_HEADER.toString().
                                                equalsIgnoreCase(header
                                                        .getHeaderName().toString())) {
                                    value = "**********";
                                }

                                sb.append("            header=").append(header.getHeaderName()).append("=")
                                        .append(value).append("\n");
                            });
                        }
                        sb.append("            status=");

                        if (exchange.getStatusCode() >= 300) {
                            sb.append(ansi().fg(RED).bold().a(exchange1.getStatusCode()).reset().toString());
                        } else {
                            sb.append(ansi().fg(GREEN).bold().a(exchange1.getStatusCode()).reset().toString());
                        }

                        sb.append("\n");

                        sb.append("           elapsed=").append(System.currentTimeMillis() - start).append("ms\n");
                        sb.append("==============================================================");
                    }

                    nextListener.proceed();
                    LOGGER.info(sb.toString());
                });
    }
}
