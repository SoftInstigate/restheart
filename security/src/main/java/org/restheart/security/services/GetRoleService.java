/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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
package org.restheart.security.services;

import java.util.Map;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_VALID_HEADER;

import static org.restheart.utils.GsonUtils.object;
import static org.restheart.utils.GsonUtils.array;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "roles",
        description = "returns the roles of the authenticated client",
        secure = false,
        enabledByDefault = true,
        defaultURI = "/roles")
public class GetRoleService implements JsonService {
    private String myURI = null;

    @Inject("config")
    private Map<String, Object> config;

    /**
     * init the service
     */
    @OnInit
    public void init() {
        if (config == null) {
            this.myURI = "/roles";
        }

        this.myURI = URLUtils.removeTrailingSlashes(argOrDefault(config, "uri", "/roles"));
    }

    /**
     * Handles the request.
     *
     * @throws Exception
     */
    @Override
    public void handle(JsonRequest request, JsonResponse response) throws Exception {
        switch(request.getMethod()){
            case GET -> {
                if (!request.isAuthenticated()) {
                    response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
                } else if (checkRequestPath(request)) {
                    var roles = array();
                    request.getAuthenticatedAccount().getRoles().forEach(roles::add);
                    response.setContent(object().put("authenticated", true).put("roles", roles));
                } else {
                    response.setStatusCode(HttpStatus.SC_FORBIDDEN);

                    // REMOVE THE AUTH TOKEN HEADERS!!!!!!!!!!!
                    response.getHeaders().remove(AUTH_TOKEN_HEADER);
                    response.getHeaders().remove(AUTH_TOKEN_VALID_HEADER);
                    response.getHeaders().remove(AUTH_TOKEN_LOCATION_HEADER);
                }
            }

            case OPTIONS -> handleOptions(request);

            default -> {
                response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        }
    }

    /**
     *
     * @param request
     * @return true if the request is authenticated and the request path is /roles/{username}
     */
    private boolean checkRequestPath(JsonRequest request){
        return request.getAuthenticatedAccount() != null
                && request.getAuthenticatedAccount().getPrincipal() != null
                && request.getAuthenticatedAccount().getPrincipal().getName() != null
                && request.getAuthenticatedAccount().getRoles() != null
                && request.getPath().equals(myURI + "/" + request.getAuthenticatedAccount().getPrincipal().getName());
    }
}
