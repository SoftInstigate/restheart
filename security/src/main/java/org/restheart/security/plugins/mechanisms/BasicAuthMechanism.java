/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.plugins.mechanisms;

import com.mongodb.connection.Cluster;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import org.restheart.ConfigurationException;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "basicAuthMechanism",
        description = "handles the basic authentication scheme",
        enabledByDefault = false)
public class BasicAuthMechanism extends io.undertow.security.impl.BasicAuthenticationMechanism
        implements AuthMechanism {

    public static final String SILENT_HEADER_KEY = "No-Auth-Challenge";
    public static final String SILENT_QUERY_PARAM_KEY = "noauthchallenge";

    private IdentityManager identityManager;

    public BasicAuthMechanism()
            throws ConfigurationException {
        super("RESTHeart Realm", "basicAuthMechanism", false);
    }

    @InjectConfiguration
    @InjectPluginsRegistry
    public void init(final Map<String, Object> args,
            PluginsRegistry pluginsRegistry)
            throws ConfigurationException {

        // the authenticator specified in auth mechanism configuration
        setIdentityManager(pluginsRegistry
                .getAuthenticator(argValue(args, "authenticator"))
                .getInstance());
    }

    public void setIdentityManager(IdentityManager idm) {
        try {
            var clazz = Class.forName("io.undertow.security.impl.BasicAuthenticationMechanism");
            var idmF = clazz.getDeclaredField("identityManager");
            idmF.setAccessible(true);

            idmF.set(this, idm);
        } catch (ClassNotFoundException
                | SecurityException
                | NoSuchFieldException
                | IllegalAccessException ex) {
        }
    }

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        if (exchange.getRequestHeaders().contains(SILENT_HEADER_KEY)
                || exchange.getQueryParameters().containsKey(SILENT_QUERY_PARAM_KEY)) {
            return new ChallengeResult(true, UNAUTHORIZED);
        } else {
            return super.sendChallenge(exchange, securityContext);
        }
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange,
            final SecurityContext securityContext) {
        return super.authenticate(exchange, securityContext);
    }

    @Override
    public String getMechanismName() {
        return "basicAuthMechanism";
    }
}
