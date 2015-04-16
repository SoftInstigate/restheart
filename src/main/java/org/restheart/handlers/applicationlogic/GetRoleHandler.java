/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.handlers.applicationlogic;

import com.mongodb.BasicDBObject;
import org.restheart.hal.Representation;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.util.Map;
import java.util.Set;
import static org.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_VALID_HEADER;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GetRoleHandler extends ApplicationLogicHandler {
    /**
     * the key for the url property.
     */
    public static final String urlKey = "url";

    private String url;

    /**
     * Creates a new instance of GetRoleHandler
     *
     * @param next
     * @param args
     * @throws Exception
     */
    public GetRoleHandler(PipedHttpHandler next, Map<String, Object> args) throws Exception {
        super(next, args);

        if (args == null) {
            throw new IllegalArgumentException("args cannot be null");
        }

        this.url = (String) ((Map<String, Object>) args).get(urlKey);
    }

    /**
     * Handles the request.
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        Representation rep;
        
        if (context.getMethod() == METHOD.OPTIONS) {
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET");
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge, " + AUTH_TOKEN_HEADER + ", " + AUTH_TOKEN_VALID_HEADER + ", " + AUTH_TOKEN_LOCATION_HEADER);
            exchange.setResponseCode(HttpStatus.SC_OK);
            exchange.endExchange();
        } else if (context.getMethod() == METHOD.GET) {
            if ((exchange.getSecurityContext() == null
                    || exchange.getSecurityContext().getAuthenticatedAccount() == null
                    || exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal() == null)
                    || !(context.getUnmappedRequestUri().equals(URLUtils.removeTrailingSlashes(url) + "/" + exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName()))) {

                {
                    exchange.setResponseCode(HttpStatus.SC_FORBIDDEN);

                    // REMOVE THE AUTH TOKEN HEADERS!!!!!!!!!!!
                    exchange.getResponseHeaders().remove(AUTH_TOKEN_HEADER);
                    exchange.getResponseHeaders().remove(AUTH_TOKEN_VALID_HEADER);
                    exchange.getResponseHeaders().remove(AUTH_TOKEN_LOCATION_HEADER);

                    exchange.endExchange();
                    return;
                }

            } else {
                rep = new Representation(URLUtils.removeTrailingSlashes(url) + "/" + exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName());
                BasicDBObject root = new BasicDBObject();

                Set<String> _roles = exchange.getSecurityContext().getAuthenticatedAccount().getRoles();

                root.append("authenticated", true);
                root.append("roles", _roles);

                rep.addProperties(root);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, HAL_JSON_MEDIA_TYPE);
            exchange.getResponseSender().send(rep.toString());
            exchange.endExchange();
        } else {
            exchange.setResponseCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }
}
