/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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
package org.restheart.handlers.injectors;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

import com.google.common.net.HttpHeaders;

import io.undertow.util.HttpString;

/**
 * Author: Andrea Di Cesare <andrea@softinstigate.com>
 *
 * According to the HTTP specification, the `Date` header should be included in all responses,
 * except when the server lacks an accurate clock.
 *
 * In Undertow, the `Date` header is added via {@code ThreadLocal<SimpleDateFormat>}.
 * However, this approach is not optimal for virtual threads.
 */
@RegisterPlugin(name="dateHeaderInjector", description="", enabledByDefault=true)
public class DateHeaderInjector implements WildcardInterceptor {
    private static final HttpString DATE = HttpString.tryFromString(HttpHeaders.DATE);
    private static final String RFC1123_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(RFC1123_PATTERN, Locale.US);
    private static final ZoneId GMT = ZoneId.of("GMT");

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        response.getHeaders().add(DATE, FORMATTER.format(ZonedDateTime.now(GMT)));
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        return true;
    }
}
