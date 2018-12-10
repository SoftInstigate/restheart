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

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uiam.Bootstrapper;
import io.uiam.Configuration;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.LocaleUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestLoggerHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggerHandler.class);

    private final Configuration configuration = Bootstrapper.getConfiguration();

    private final HttpHandler handler;

    /**
     * Creates a new instance of RequestLoggerHandler
     *
     * @param next
     */
    public RequestLoggerHandler(PipedHttpHandler next) {
        super(next);
        handler = null;
    }

    /**
     * Creates a new instance of RequestLoggerHandler
     *
     * @param handler
     */
    public RequestLoggerHandler(HttpHandler handler) {
        super(null);
        this.handler = handler;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (configuration.logExchangeDump() > 0) {
            dumpExchange(exchange, context, configuration.logExchangeDump());
        }

        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }

        if (handler != null) {
            handler.handleRequest(exchange);
        }
    }

    /**
     * dumpExchange
     *
     * Log a complete dump of the HttpServerExchange (both Request and Response)
     *
     * @param exchange the HttpServerExchange
     * @param logLevel it can be 0, 1 or 2
     */
    protected void dumpExchange(HttpServerExchange exchange, RequestContext context,  Integer logLevel) {
        if (logLevel < 1) {
            return;
        }

        final StringBuilder sb = new StringBuilder();
        final long start = context != null ? context.getRequestStartTime() : System.currentTimeMillis();

        if (logLevel == 1) {
            sb.append(exchange.getRequestMethod()).append(" ")
                    .append(exchange.getRequestURL());

            if (exchange.getQueryString() != null && !exchange.getQueryString().isEmpty()) {
                sb.append("?").append(exchange.getQueryString());
            }

            sb.append(" from ").append(exchange.getSourceAddress());
        } else if (logLevel >= 2) {
            sb.append("\n----------------------------REQUEST---------------------------\n");
            sb.append("               URI=").append(exchange.getRequestURI()).append("\n");
            sb.append(" characterEncoding=").append(exchange.getRequestHeaders().get(Headers.CONTENT_ENCODING)).append("\n");
            sb.append("     contentLength=").append(exchange.getRequestContentLength()).append("\n");
            sb.append("       contentType=").append(exchange.getRequestHeaders().get(Headers.CONTENT_TYPE)).append("\n");

            Map<String, Cookie> cookies = exchange.getRequestCookies();
            if (cookies != null) {
                cookies.entrySet().stream().map((entry) -> entry.getValue()).forEach((cookie) -> {
                    sb.append("            cookie=").append(cookie.getName()).append("=").append(cookie.getValue()).append("\n");
                });
            }
            for (HeaderValues header : exchange.getRequestHeaders()) {
                header.stream().forEach((value) -> {
                    sb.append("            header=").append(header.getHeaderName()).append("=").append(value).append("\n");
                });
            }
            sb.append("            locale=").append(LocaleUtils.getLocalesFromHeader(exchange.getRequestHeaders().get(Headers.ACCEPT_LANGUAGE))).append("\n");
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

    private void addExchangeCompleteListener(HttpServerExchange exchange, Integer logLevel, final StringBuilder sb, final long start) {
        exchange.addExchangeCompleteListener((final HttpServerExchange exchange1, final ExchangeCompletionListener.NextListener nextListener) -> {
            if (logLevel < 1) {
                return;
            }

            // note sc is always null if this handler is chained before SecurityHandlerDispacher
            final SecurityContext sc = exchange1.getSecurityContext();

            if (logLevel == 1) {
                sb.append(" =>").append(" status=");

                if (exchange.getStatusCode() >= 300
                        && exchange.getStatusCode() != 304) {
                    sb.append(ansi().fg(RED).bold().a(exchange.getStatusCode()).reset().toString());
                } else {
                    sb.append(ansi().fg(GREEN).bold().a(exchange.getStatusCode()).reset().toString());
                }

                sb.append(" elapsed=")
                        .append(System.currentTimeMillis() - start)
                        .append("ms")
                        .append(" contentLength=").append(exchange1.getResponseContentLength());

                if (sc != null && sc.getAuthenticatedAccount() != null) {
                    sb.append(" username=").append(sc.getAuthenticatedAccount().getPrincipal().getName())
                            .append(" roles=").append(sc.getAuthenticatedAccount().getRoles());
                }
            } else if (logLevel >= 2) {
                sb.append("--------------------------RESPONSE--------------------------\n");
                if (sc != null) {
                    if (sc.isAuthenticated()) {
                        sb.append("          authType=").append(sc.getMechanismName()).append("\n");
                        sb.append("          username=").append(sc.getAuthenticatedAccount().getPrincipal().getName()).append("\n");
                        sb.append("             roles=").append(sc.getAuthenticatedAccount().getRoles()).append("\n");
                    } else {
                        sb.append("          authType=none" + "\n");
                    }
                }

                sb.append("     contentLength=").append(exchange1.getResponseContentLength()).append("\n");
                sb.append("       contentType=").append(exchange1.getResponseHeaders().getFirst(Headers.CONTENT_TYPE)).append("\n");
                Map<String, Cookie> cookies1 = exchange1.getResponseCookies();
                if (cookies1 != null) {
                    cookies1.values().stream().forEach((cookie) -> {
                        sb.append("            cookie=").append(cookie.getName()).append("=").append(cookie.getValue()).append("; domain=").append(cookie.getDomain()).append("; path=").append(cookie.getPath()).append("\n");
                    });
                }
                for (HeaderValues header : exchange1.getResponseHeaders()) {
                    header.stream().forEach((value) -> {
                        sb.append("            header=").append(header.getHeaderName()).append("=").append(value).append("\n");
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
