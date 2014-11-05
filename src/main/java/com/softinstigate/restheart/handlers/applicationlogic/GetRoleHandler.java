/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.handlers.applicationlogic;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.softinstigate.restheart.hal.Representation;
import com.softinstigate.restheart.handlers.*;
import com.softinstigate.restheart.handlers.RequestContext.METHOD;
import com.softinstigate.restheart.utils.HttpStatus;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.DatatypeConverter;

/**
 *
 * @author uji
 */
public class GetRoleHandler extends ApplicationLogicHandler
{
    public String idmClazzKey = "idm-implementation-class";
    public String idmConfFileKey = "idm-conf-file";

    IdentityManager idm = null;

    public GetRoleHandler(PipedHttpHandler next, Map<String, Object> args) throws Exception
    {
        super(next, args);

        if (args == null)
        {
            throw new IllegalArgumentException("args cannot be null");
        }

        String idmClazz = (String) ((Map<String, Object>) args).get(idmClazzKey);
        String idmConfFile = (String) ((Map<String, Object>) args).get(idmConfFileKey);

        Map<String, Object> idmArgs = new HashMap<>();
        idmArgs.put("conf-file", idmConfFile);

        Object _idm = Class.forName(idmClazz).getConstructor(Map.class).newInstance(idmArgs);
        idm = (IdentityManager) _idm;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

        if (context.getMethod() == METHOD.OPTIONS)
        {
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Host, Origin, X-Requested-With, User-Agent");
            exchange.setResponseCode(HttpStatus.SC_OK);
            exchange.endExchange();
        }
        else if (context.getMethod() == METHOD.GET)
        {
            if (authHeader != null && authHeader.startsWith("Basic "))
            {
                authHeader = authHeader.replaceAll("^Basic ", "");

                byte[] __idAndPwd = null;

                try
                {
                    __idAndPwd = DatatypeConverter.parseBase64Binary(authHeader);
                }
                catch (IllegalArgumentException iae)
                {
                    __idAndPwd = null;
                }

                if (__idAndPwd != null)
                {
                    String[] idAndPwd = new String(__idAndPwd).split(":");

                    if (idAndPwd.length == 2)
                    {
                        Account a = idm.verify(idAndPwd[0], new PasswordCredential(idAndPwd[1].toCharArray()));

                        if (a != null)
                        {
                            BasicDBList _roles = new BasicDBList();

                            _roles.addAll(a.getRoles());

                            BasicDBObject root = new BasicDBObject();

                            root.append("authenticated", true);
                            root.append("roles", _roles);

                            Representation rep = new Representation("/_logic/roles/mine");
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

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, Representation.HAL_JSON_MEDIA_TYPE);
            exchange.setResponseCode(HttpStatus.SC_OK);
            exchange.getResponseSender().send(rep.toString());
            exchange.endExchange();
        }
        else
        {
            exchange.setResponseCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }
}
