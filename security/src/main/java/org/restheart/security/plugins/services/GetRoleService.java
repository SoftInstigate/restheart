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
package org.restheart.security.plugins.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.util.Map;
import java.util.Set;
import org.restheart.ConfigurationException;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.ConfigurablePlugin;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_VALID_HEADER;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "roles",
        description = "returns the roles of the authenticated client",
        enabledByDefault = true,
        defaultURI = "/roles")
public class GetRoleService implements JsonService {
    Map<String, Object> confArgs = null;
    
    /**
     * init the service
     *
     * @param confArgs
     */
    @InjectConfiguration
    public void init (Map<String, Object> confArgs) {
        this.confArgs = confArgs;
    }

    /**
     * Handles the request.
     *
     * @throws Exception
     */
    @Override
    public void handle(JsonRequest request, JsonResponse response) throws Exception {
        var exchange = request.getExchange();
        
        if (request.isOptions()) {
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET");
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Headers"),
                    "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge, "
                    + AUTH_TOKEN_HEADER
                    + ", " + AUTH_TOKEN_VALID_HEADER
                    + ", " + AUTH_TOKEN_LOCATION_HEADER);
            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.endExchange();
        } else if (request.isGet()) {
            exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");

            if ((exchange.getSecurityContext() == null
                    || exchange.getSecurityContext().getAuthenticatedAccount() == null
                    || exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal() == null)
                    || !(exchange.getRequestURI().equals(URLUtils.removeTrailingSlashes(getUri()) + "/"
                            + exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName()))) {
                {
                    exchange.setStatusCode(HttpStatus.SC_FORBIDDEN);

                    // REMOVE THE AUTH TOKEN HEADERS!!!!!!!!!!!
                    exchange.getResponseHeaders().remove(AUTH_TOKEN_HEADER);
                    exchange.getResponseHeaders().remove(AUTH_TOKEN_VALID_HEADER);
                    exchange.getResponseHeaders().remove(AUTH_TOKEN_LOCATION_HEADER);

                    exchange.endExchange();
                    return;
                }

            } else {
                JsonObject root = new JsonObject();

                Set<String> _roles = exchange.getSecurityContext().getAuthenticatedAccount().getRoles();

                JsonArray roles = new JsonArray();

                _roles.forEach((role) -> {
                    roles.add(new JsonPrimitive(role));
                });

                root.add("authenticated", new JsonPrimitive(true));
                root.add("roles", roles);

                exchange.getResponseSender().send(root.toString());
            }

            exchange.endExchange();
        } else {
            exchange.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }

    private String getUri() {
        if (confArgs == null) {
            return "/roles";
        }
        
        try {
            return ConfigurablePlugin.argValue(confArgs, "uri");
        }
        catch (ConfigurationException ex) {
            return "/roles";
        }
    }
}
