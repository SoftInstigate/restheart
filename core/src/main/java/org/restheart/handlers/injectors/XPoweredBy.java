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

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

import com.google.common.net.HttpHeaders;

import io.undertow.util.HttpString;

/**
 * Sets the X-Powered-By: restheart.org response header
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 */
@RegisterPlugin(name="xPoweredBy", description="Sets the X-Powered-By: restheart.org response header", enabledByDefault=true)
public class XPoweredBy implements WildcardInterceptor {
    private static final HttpString X_POWERED_BY = HttpString.tryFromString(HttpHeaders.X_POWERED_BY);
    private static final String RESTHEART_ORG = "restheart.org";

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        response.getHeaders().add(X_POWERED_BY, RESTHEART_ORG);
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        return true;
    }
}
