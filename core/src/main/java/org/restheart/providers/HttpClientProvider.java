/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.providers;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides one shared, configured {@link HttpClient} to the plugins that call other services.
 *
 * <p>A client built inside a request handler, one per call, throws away connection pooling and
 * repeats the TLS handshake every time. This one is built once, from the {@code http-client}
 * section of the configuration: connect timeout, HTTP version, redirect policy and proxy. The
 * default request timeout is read by the callers that need one, the {@code http} capability of
 * the JavaScript SDK among them.</p>
 *
 * <p>Inject it with {@code @Inject("http-client")}. Requests run on the shared virtual threads
 * executor, so a blocking {@code send()} on a request thread parks a virtual thread and costs
 * nothing.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "http-client", description = "provides the shared, configured java.net.http.HttpClient")
public class HttpClientProvider implements Provider<HttpClient> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientProvider.class);

    /** The configuration key, {@code http-client}. */
    public static final String NAME = "http-client";

    @Inject("config")
    private Map<String, Object> config;

    private HttpClient client;

    @OnInit
    public void init() throws ConfigurationException {
        var builder = HttpClient.newBuilder()
                .executor(ThreadsUtils.virtualThreadsExecutor())
                .connectTimeout(Duration.ofSeconds(positive("connect-timeout", 10)));

        builder.version(switch (string("version", "http-2").toLowerCase(Locale.ROOT)) {
            case "http-1.1", "http/1.1", "1.1" -> HttpClient.Version.HTTP_1_1;
            case "http-2", "http/2", "2" -> HttpClient.Version.HTTP_2;
            default -> throw new ConfigurationException("http-client: version must be http-1.1 or http-2, got " + config.get("version"));
        });

        builder.followRedirects(switch (string("follow-redirects", "normal").toLowerCase(Locale.ROOT)) {
            case "never" -> HttpClient.Redirect.NEVER;
            case "normal" -> HttpClient.Redirect.NORMAL;
            case "always" -> HttpClient.Redirect.ALWAYS;
            default -> throw new ConfigurationException("http-client: follow-redirects must be never, normal or always, got " + config.get("follow-redirects"));
        });

        if (config != null && config.get("proxy") instanceof Map<?, ?> proxy) {
            var host = proxy.get("host");
            var port = proxy.get("port");
            if (host instanceof String h && !h.isBlank() && port instanceof Number p) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(h, p.intValue())));
                LOGGER.info("http-client: requests go through the proxy {}:{}", h, p.intValue());
            } else {
                throw new ConfigurationException("http-client: proxy needs host and port");
            }
        }

        this.client = builder.build();

        LOGGER.debug("http-client: connect-timeout {}s, request-timeout {}s, version {}, follow-redirects {}",
                positive("connect-timeout", 10), positive("request-timeout", 30), client.version(), client.followRedirects());
    }

    private String string(String key, String defaultValue) {
        if (config == null || !(config.get(key) instanceof String s) || s.isBlank()) {
            return defaultValue;
        }
        return s;
    }

    private long positive(String key, long defaultValue) throws ConfigurationException {
        if (config == null || !(config.get(key) instanceof Number n)) {
            return defaultValue;
        }
        if (n.longValue() <= 0) {
            throw new ConfigurationException("http-client: " + key + " must be positive, got " + n);
        }
        return n.longValue();
    }

    @Override
    public HttpClient get(PluginRecord<?> caller) {
        return client;
    }
}
