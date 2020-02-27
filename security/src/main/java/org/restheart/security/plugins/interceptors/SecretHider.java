/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.security.plugins.interceptors;

import com.google.gson.JsonElement;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import org.restheart.handlers.exchange.ByteArrayRequest;
import org.restheart.handlers.exchange.ByteArrayResponse;
import org.restheart.handlers.exchange.JsonRequest;
import org.restheart.security.plugins.RegisterPlugin;
import org.restheart.security.plugins.RequestInterceptor;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "secretHider",
        description = "forbis write requests "
        + "on '/coll' "
        + "containing the property 'secret' "
        + "to users does not have the role 'admin'",
        enabledByDefault = false)
public class SecretHider implements RequestInterceptor {
    static final Logger LOGGER = LoggerFactory.getLogger(SecretHider.class);
    
    @Override
    public RequestInterceptor.IPOINT interceptPoint() {
        return RequestInterceptor.IPOINT.AFTER_AUTH;
    }

    @Override
    public void handleRequest(HttpServerExchange hse) throws Exception {
        var content = JsonRequest.wrap(hse).readContent();

        if (keys(content).stream()
                .anyMatch(k -> "secret".equals(k)
                || k.endsWith(".secret"))) {
            var response = ByteArrayResponse.wrap(hse);

            response.endExchangeWithMessage(HttpStatus.SC_FORBIDDEN, "cannot write secret");
        }
    }

    @Override
    public boolean resolve(HttpServerExchange hse) {
        var req = ByteArrayRequest.wrap(hse);

        return ByteArrayRequest.isContentTypeJson(hse)
                && !req.isAccountInRole("admin")
                && hse.getRequestPath().startsWith("/coll")
                && (req.isPost() || req.isPatch() || req.isPut());
    }

    /**
     * @return the keys of the JSON
     */
    private ArrayList<String> keys(JsonElement val) {
        var keys = new ArrayList<String>();

        if (val == null) {
            return keys;
        } else if (val.isJsonObject()) {
            val.getAsJsonObject().keySet().forEach(k -> {
                keys.add(k);
                keys.addAll(keys(val.getAsJsonObject().get(k)));
            });
        } else if (val.isJsonArray()) {
            val.getAsJsonArray().forEach(v -> keys.addAll(keys(v)));
        }

        return keys;
    }
}
