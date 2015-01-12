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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.restheart.hal.Representation;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import static org.restheart.security.RestheartIdentityManager.RESTHEART_REALM;
import org.restheart.utils.HttpStatus;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import io.undertow.util.HttpString;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.DatatypeConverter;

/**
 *
 * @author Andrea Di Cesare
 */
public class GetRoleHandler extends ApplicationLogicHandler {

    /**
     * the key for the idm-implementation-class property.
     */
    public static final String idmClazzKey = "idm-implementation-class";

    /**
     * the key for the dm-conf-file property.
     */
    public static final String idmConfFileKey = "idm-conf-file";

    /**
     * the key for the url property.
     */
    public static final String urlKey = "url";

    /**
     * the key for the send-challenge property.
     */
    public static final String sendChallengeKey = "send-challenge";

    private static final String BASIC_PREFIX = BASIC + " ";
    private static final String challenge = BASIC_PREFIX + "realm=\"" + RESTHEART_REALM + "\"";

    private IdentityManager idm = null;
    private String url;
    private boolean sendChallenge;

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

        String idmClazz = (String) ((Map<String, Object>) args).get(idmClazzKey);
        String idmConfFile = (String) ((Map<String, Object>) args).get(idmConfFileKey);

        Map<String, Object> idmArgs = new HashMap<>();
        idmArgs.put("conf-file", idmConfFile);

        Object _idm = Class.forName(idmClazz).getConstructor(Map.class).newInstance(idmArgs);
        this.idm = (IdentityManager) _idm;

        this.url = (String) ((Map<String, Object>) args).get(urlKey);
        this.sendChallenge = (boolean) ((Map<String, Object>) args).get(sendChallengeKey);
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
        if (context.getMethod() == METHOD.OPTIONS) {
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET");
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
            exchange.setResponseCode(HttpStatus.SC_OK);
            exchange.endExchange();
        } else if (context.getMethod() == METHOD.GET) {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

            if (authHeader != null && authHeader.startsWith("Basic ")) {
                authHeader = authHeader.replaceAll("^Basic ", "");

                byte[] __idAndPwd;

                try {
                    __idAndPwd = DatatypeConverter.parseBase64Binary(authHeader);
                } catch (IllegalArgumentException iae) {
                    __idAndPwd = null;
                }

                if (__idAndPwd != null) {
                    String[] idAndPwd = new String(__idAndPwd, StandardCharsets.UTF_8).split(":");

                    if (idAndPwd.length == 2) {
                        Account a = idm.verify(idAndPwd[0], new PasswordCredential(idAndPwd[1].toCharArray()));

                        if (a != null) {
                            BasicDBList _roles = new BasicDBList();

                            _roles.addAll(a.getRoles());

                            BasicDBObject root = new BasicDBObject();

                            root.append("authenticated", true);
                            root.append("roles", _roles);

                            Representation rep = new Representation(url);
                            rep.addProperties(root);

                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, Representation.HAL_JSON_MEDIA_TYPE);
                            exchange.setResponseCode(HttpStatus.SC_OK);
                            exchange.getResponseSender().send(rep.toString());
                            exchange.endExchange();
                            return;
                        }
                    }
                }
            }

            BasicDBObject root = new BasicDBObject();

            root.append("authenticated", false);
            root.append("roles", null);

            Representation rep = new Representation("/_logic/roles/mine");
            rep.addProperties(root);

            if (sendChallenge) {
                exchange.getResponseHeaders().add(WWW_AUTHENTICATE, challenge);
                exchange.setResponseCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                exchange.setResponseCode(HttpStatus.SC_OK);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, Representation.HAL_JSON_MEDIA_TYPE);
            exchange.getResponseSender().send(rep.toString());

            exchange.endExchange();
        } else {
            exchange.setResponseCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }
}
