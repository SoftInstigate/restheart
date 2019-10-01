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
package org.restheart.security.handlers;

import org.restheart.security.handlers.exchange.JsonRequest;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.restheart.security.Bootstrapper;
import org.restheart.security.Configuration;
import static org.restheart.security.plugins.TokenManager.AUTH_TOKEN_HEADER;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.LocaleUtils;
import io.undertow.util.QueryParameterUtils;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestLogger extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLogger.class);

    private final Configuration configuration = Bootstrapper.getConfiguration();

    private final HttpHandler handler;

    /**
     * Creates a new instance of RequestLoggerHandler
     *
     * @param next
     */
    public RequestLogger(PipedHttpHandler next) {
        super(next);
        handler = null;
    }

    /**
     * Creates a new instance of RequestLoggerHandler
     *
     * @param handler
     */
    public RequestLogger(HttpHandler handler) {
        super(null);
        this.handler = handler;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (configuration.logExchangeDump() > 0) {
            dumpExchange(exchange, configuration.logExchangeDump());
        }

        next(exchange);

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
    protected void dumpExchange(HttpServerExchange exchange, Integer logLevel) {
        if (logLevel < 1) {
            return;
        }

        var request = JsonRequest.wrap(exchange);

        final StringBuilder sb = new StringBuilder();
        final long start = request.getStartTime() != null 
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
            sb.append(" characterEncoding=").append(exchange.getRequestHeaders().get(Headers.CONTENT_ENCODING))
                    .append("\n");
            sb.append("     contentLength=").append(exchange.getRequestContentLength()).append("\n");
            sb.append("       contentType=").append(exchange.getRequestHeaders().get(Headers.CONTENT_TYPE))
                    .append("\n");

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
