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
package org.restheart.security.plugins;

import java.util.Optional;

import org.restheart.security.Bootstrapper;
import org.restheart.security.cache.Cache;
import org.restheart.security.cache.CacheFactory;
import org.restheart.security.cache.LoadingCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.restheart.security.ConfigurationException;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginsRegistry {

    private static final String AUTH_TOKEN_MANAGER_NAME = "@@authTokenManager";
    private static final String ACCESS_MANAGER_NAME = "@@accessManager";

    private static final LoadingCache<String, Authenticator> AUTHENTICATORS_CACHE
            = CacheFactory.createLocalLoadingCache(
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var authenticators = Bootstrapper.getConfiguration()
                                .getAuthenticators();

                        var authenticatorConf = authenticators.stream().filter(
                                authenticator -> name
                                        .equals(authenticator.get("name")))
                                .findFirst();

                        if (authenticatorConf.isPresent()) {
                            try {
                                return PluginsFactory
                                        .createAuthenticator(authenticatorConf.get());
                            } catch (ConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Authenticator "
                            + name
                            + " not found.";

                            throw new IllegalStateException(errorMsg,
                                    new ConfigurationException(errorMsg));
                        }
                    });

    private static final LoadingCache<String, AuthMechanism> AUTH_MECHANISMS_CACHE
            = CacheFactory.createLocalLoadingCache(
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var amsConf = Bootstrapper.getConfiguration().getAuthMechanisms();

                        var amConf = amsConf.stream().filter(am -> name
                        .equals(am.get("name")))
                                .findFirst();

                        if (amConf.isPresent()) {
                            try {
                                return PluginsFactory
                                        .createAutenticationMechanism(amConf.get());
                            } catch (ConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Authentication Mechanism "
                            + name
                            + " not found.";

                            throw new IllegalStateException(errorMsg,
                                    new ConfigurationException(errorMsg));
                        }
                    });

    private static final LoadingCache<String, Authorizer> AUTHORIZERS_CACHE
            = CacheFactory.createLocalLoadingCache(
                    1,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var authorizerConf = Bootstrapper.getConfiguration()
                                .getAuthorizers();

                        if (authorizerConf != null) {
                            try {
                                return PluginsFactory
                                        .createAuthorizer(authorizerConf);
                            } catch (ConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Authorizer "
                            + " not configured.";

                            throw new IllegalStateException(errorMsg,
                                    new ConfigurationException(errorMsg));
                        }
                    });

    private static final LoadingCache<String, Service> SERVICES_CACHE
            = CacheFactory.createLocalLoadingCache(
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var srvsConf = Bootstrapper.getConfiguration()
                                .getServices();

                        var srvConf = srvsConf.stream().filter(srv -> name
                        .equals(srv.get("name")))
                                .findFirst();

                        if (srvConf.isPresent()) {
                            try {
                                return PluginsFactory
                                        .createService(srvConf.get());
                            } catch (ConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Service "
                            + name
                            + " not found.";

                            throw new IllegalStateException(errorMsg,
                                    new ConfigurationException(errorMsg));
                        }
                    });

    private static final List<RequestInterceptor> REQUEST_INTERCEPTORS
            = Collections.synchronizedList(new ArrayList<>());

    private static final List<ResponseInterceptor> RESPONSE_INTECEPTORS
            = Collections.synchronizedList(new ArrayList<>());

    private static PluginsRegistry HOLDER;

    public static synchronized PluginsRegistry getInstance() {
        if (HOLDER == null) {
            HOLDER = new PluginsRegistry();
        }

        return HOLDER;
    }

    private PluginsRegistry() {
    }

    public Authenticator getAuthenticator(String name)
            throws ConfigurationException {
        Optional<Authenticator> op = AUTHENTICATORS_CACHE
                .getLoading(name);

        if (op == null) {
            throw new ConfigurationException(
                    "No Authenticator configured with name: " + name);
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new ConfigurationException(
                    "No Authenticator configured with name: " + name);
        }
    }

    public AuthMechanism getAuthenticationMechanism(String name)
            throws ConfigurationException {
        Optional<AuthMechanism> op = AUTH_MECHANISMS_CACHE
                .getLoading(name);

        if (op == null) {
            throw new ConfigurationException(
                    "No Authentication Mechanism configured with name: " + name);
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new ConfigurationException(
                    "No Authentication Mechanism configured with name: " + name);
        }
    }

    public Service getService(String name)
            throws ConfigurationException {
        Optional<Service> op = SERVICES_CACHE
                .getLoading(name);

        if (op == null) {
            throw new ConfigurationException(
                    "No Service configured with name: " + name);
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new ConfigurationException(
                    "No Service configured with name: " + name);
        }
    }

    public Authorizer getAuthorizer()
            throws ConfigurationException {
        Optional<Authorizer> op = AUTHORIZERS_CACHE
                .getLoading(ACCESS_MANAGER_NAME);

        if (op == null) {
            throw new ConfigurationException(
                    "No Authorizer configured");
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new ConfigurationException(
                    "No Authorizer configured");
        }
    }

    public TokenManager getTokenManager()
            throws ConfigurationException {
        Optional<Authenticator> op = AUTHENTICATORS_CACHE
                .get(AUTH_TOKEN_MANAGER_NAME);

        if (op == null) {
            Authenticator atm = PluginsFactory
                    .createTokenManager(Bootstrapper.getConfiguration()
                            .getTokenManager());

            AUTHENTICATORS_CACHE.put(AUTH_TOKEN_MANAGER_NAME, atm);
            return (TokenManager) atm;
        }

        if (op.isPresent()) {
            return (TokenManager) op.get();
        } else {
            throw new ConfigurationException(
                    "No Token Manager configured");
        }
    }

    public List<RequestInterceptor> getRequestInterceptors() {
        return REQUEST_INTERCEPTORS;
    }

    public List<ResponseInterceptor> getResponseInterceptors() {
        return RESPONSE_INTECEPTORS;
    }
}
