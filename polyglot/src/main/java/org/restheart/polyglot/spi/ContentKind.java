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

import java.util.Locale;
import java.util.Optional;

/**
 * The type of the request and response content a polyglot plugin works with.
 *
 * <p>It replaces the Java-flavoured {@code pluginClass} of JavaScript interceptors
 * ({@code StringInterceptor}, {@code MongoInterceptor}, ...) with a name that means the same
 * in every guest language, and it is what a service variant is chosen from: {@code string},
 * {@code json}, {@code bson} and {@code bytes} exist for services and interceptors,
 * {@code mongo} and {@code csv} for interceptors only, since the requests they see are
 * handled by the MongoDB service and the CSV loader.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public enum ContentKind {
    STRING("string", true),
    JSON("json", true),
    BSON("bson", true),
    BYTES("bytes", true),
    MONGO("mongo", false),
    CSV("csv", false);

    private final String key;
    private final boolean forServices;

    ContentKind(String key, boolean forServices) {
        this.key = key;
        this.forServices = forServices;
    }

    /**
     * @return the name used in {@code contentKind}
     */
    public String key() {
        return key;
    }

    /**
     * @return whether a service can be declared with this kind
     */
    public boolean forServices() {
        return forServices;
    }

    /**
     * The kind named by {@code contentKind}.
     *
     * @param key the value of {@code contentKind}, case-insensitive
     * @return the kind, empty when the name is unknown
     */
    public static Optional<ContentKind> of(String key) {
        if (key == null) {
            return Optional.empty();
        }

        var k = key.strip().toLowerCase(Locale.ROOT);

        for (var ck : values()) {
            if (ck.key.equals(k)) {
                return Optional.of(ck);
            }
        }

        return Optional.empty();
    }

    /**
     * The kind matching a legacy {@code pluginClass}, the simple or the fully qualified name.
     *
     * @param pluginClass the value of {@code pluginClass}
     * @return the kind, empty when the class name is unknown
     */
    public static Optional<ContentKind> ofPluginClass(String pluginClass) {
        if (pluginClass == null) {
            return Optional.empty();
        }

        var simple = pluginClass.contains(".") ? pluginClass.substring(pluginClass.lastIndexOf('.') + 1) : pluginClass;

        return switch (simple) {
            case "StringInterceptor", "StringService", "WildCardJSInterceptor", "WildcardInterceptor" -> Optional.of(STRING);
            case "JsonInterceptor", "JsonService" -> Optional.of(JSON);
            case "BsonInterceptor", "BsonService" -> Optional.of(BSON);
            case "ByteArrayInterceptor", "ByteArrayService", "ByteArrayProxyInterceptor" -> Optional.of(BYTES);
            case "MongoInterceptor" -> Optional.of(MONGO);
            case "CsvInterceptor" -> Optional.of(CSV);
            default -> Optional.empty();
        };
    }
}
