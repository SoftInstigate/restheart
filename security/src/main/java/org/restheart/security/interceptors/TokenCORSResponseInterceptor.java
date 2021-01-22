/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.security.interceptors;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import static org.restheart.plugins.InterceptPoint.RESPONSE;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import static org.restheart.plugins.security.TokenManager.ACCESS_CONTROL_EXPOSE_HEADERS;

/**
 * helper interceptor to add token headers to Access-Control-Expose-Headers to
 * handle CORS request
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name="tokenCORSResponseInterceptor",
        description = "helper interceptor to add token headers to "
                + "Access-Control-Expose-Headers to handle CORS request",
        interceptPoint = RESPONSE,
        enabledByDefault = true)
public class TokenCORSResponseInterceptor implements MongoInterceptor {

    private String[] headers;

    public TokenCORSResponseInterceptor() {
        this.headers = new String[0];
    }

    public TokenCORSResponseInterceptor(String... headers) {
        this.headers = headers;
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var hs = response.getHeaders().get(ACCESS_CONTROL_EXPOSE_HEADERS);

        if (hs == null || hs.isEmpty()) {
            response.getHeaders()
                .put(ACCESS_CONTROL_EXPOSE_HEADERS, headers());
        } else {
            var v0 = hs.getFirst();

            for (var h : this.headers) {
                if (!v0.contains(h)) {
                    v0 = v0.concat(", ").concat(h);
                }
            }

            response.getHeaders().put(ACCESS_CONTROL_EXPOSE_HEADERS, v0);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return true;
    }

    private String headers() {
        var ret = "";
        var first = true;

        for (var h : this.headers) {
            if (first) {
                ret = ret.concat(h);
                first = false;
            } else {
                ret = ret.concat(", ").concat(h);
            }
        }

        return ret;
    }

    /**
     * @param headers the headers to set
     */
    public void setHeaders(String[] headers) {
        this.headers = headers;
    }
}
