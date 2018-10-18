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
package io.uiam;

import com.google.common.collect.Sets;
import io.undertow.Undertow.Builder;
import io.undertow.UndertowOptions;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Option;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ConfigurationHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private static final Set<Option> UNDERTOW_OPTIONS;

    private static final Set<Option> LONG_UNDERTOW_OPTIONS;

    static {
        UNDERTOW_OPTIONS = Sets.newHashSet();
        UNDERTOW_OPTIONS.add(UndertowOptions.ALLOW_ENCODED_SLASH);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALLOW_EQUALS_IN_COOKIE_VALUE);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALLOW_UNKNOWN_PROTOCOLS);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALWAYS_SET_DATE);
        UNDERTOW_OPTIONS.add(UndertowOptions.ALWAYS_SET_KEEP_ALIVE);
        UNDERTOW_OPTIONS.add(UndertowOptions.BUFFER_PIPELINED_DATA);
        UNDERTOW_OPTIONS.add(UndertowOptions.DECODE_URL);
        UNDERTOW_OPTIONS.add(UndertowOptions.ENABLE_HTTP2);
        UNDERTOW_OPTIONS.add(UndertowOptions.ENABLE_SPDY);
        UNDERTOW_OPTIONS.add(UndertowOptions.ENABLE_STATISTICS);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_HUFFMAN_CACHE_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_HEADER_TABLE_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_INITIAL_WINDOW_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_MAX_FRAME_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.HTTP2_SETTINGS_MAX_HEADER_LIST_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.IDLE_TIMEOUT);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_BUFFERED_REQUEST_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_CONCURRENT_REQUESTS_PER_CONNECTION);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_COOKIES);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_HEADERS);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_HEADER_SIZE);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_PARAMETERS);
        UNDERTOW_OPTIONS.add(UndertowOptions.MAX_QUEUED_READ_BUFFERS);
        UNDERTOW_OPTIONS.add(UndertowOptions.NO_REQUEST_TIMEOUT);
        UNDERTOW_OPTIONS.add(UndertowOptions.RECORD_REQUEST_START_TIME);
        UNDERTOW_OPTIONS.add(UndertowOptions.REQUEST_PARSE_TIMEOUT);
        UNDERTOW_OPTIONS.add(UndertowOptions.URL_CHARSET);

        LONG_UNDERTOW_OPTIONS = Sets.newHashSet();
        LONG_UNDERTOW_OPTIONS.add(UndertowOptions.MAX_ENTITY_SIZE);
        LONG_UNDERTOW_OPTIONS.add(UndertowOptions.MULTIPART_MAX_ENTITY_SIZE);
    }

    @SuppressWarnings("unchecked")
    public static void setConnectionOptions(
            Builder builder,
            Configuration configuration) {

        Map<String, Object> options = configuration.getConnectionOptions();

        UNDERTOW_OPTIONS.stream().forEach(option -> {
            if (options.containsKey(option.getName())) {
                Object value = options.get(option.getName());

                if (value != null) {
                    builder.setServerOption(option, value);
                    LOGGER.trace("Connection option {}={}", option.getName(), value);
                }
            }
        });

        LONG_UNDERTOW_OPTIONS.stream().forEach(option -> {
            if (options.containsKey(option.getName())) {
                Object value = options.get(option.getName());

                if (value != null) {
                    Long lvalue = 0l + (Integer) value;
                    builder.setServerOption(option, lvalue);
                    LOGGER.trace("Connection option {}={}", option.getName(), lvalue);
                }
            }
        });
    }

    private ConfigurationHelper() {
    }
}
