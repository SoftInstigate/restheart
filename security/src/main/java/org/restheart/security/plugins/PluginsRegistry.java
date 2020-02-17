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
import java.util.LinkedHashSet;
import java.util.Set;
import org.restheart.security.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginsRegistry {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(PluginsRegistry.class);

    private static final String AUTH_TOKEN_MANAGER_NAME = "@@authTokenManager";
    private static final String AUTHORIZER_NAME = "@@authorizer";

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
                            }
                            catch (ConfigurationException pcex) {
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
                            }
                            catch (ConfigurationException pcex) {
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
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var authorizersConf = Bootstrapper.getConfiguration()
                                .getAuthorizers();

                        var authorizerConf = authorizersConf.stream().filter(am -> name
                        .equals(am.get("name")))
                                .findFirst();

                        if (authorizerConf != null) {
                            try {
                                return PluginsFactory
                                        .createAuthorizer(authorizerConf.get());
                            }
                            catch (ConfigurationException pcex) {
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

    private final Set<PluginRecord<Service>> services
            = new LinkedHashSet<>();

    private final Set<PluginRecord<Initializer>> initializers
            = new LinkedHashSet<>();

    private final Set<PluginRecord<PreStartupInitializer>> preStartupInitializers
            = new LinkedHashSet<>();

    private final Set<PluginRecord<RequestInterceptor>> requestInterceptors
            = new LinkedHashSet<>();

    private final Set<PluginRecord<ResponseInterceptor>> responseInterceptors
            = new LinkedHashSet<>();

    private static PluginsRegistry HOLDER;

    public static synchronized PluginsRegistry getInstance() {
        if (HOLDER == null) {
            HOLDER = new PluginsRegistry();
        }

        return HOLDER;
    }

    private PluginsRegistry() {
        this.preStartupInitializers.addAll(PluginsFactory.createPreStartupInitializers());
        this.initializers.addAll(PluginsFactory.createInitializers());
        this.services.addAll(PluginsFactory.createServices());
        this.requestInterceptors.addAll(PluginsFactory.createRequestInterceptors());
        this.responseInterceptors.addAll(PluginsFactory.createResponseInterceptors());
    }

    public Set<PluginRecord<Initializer>> getInitializers() {
        return this.initializers;
    }

    public Set<PluginRecord<PreStartupInitializer>> getPreStartupInitializers() {
        return this.preStartupInitializers;
    }

    public Set<PluginRecord<Service>> getServices() {
        return this.services;
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

    public Authorizer getAuthorizer(String name)
            throws ConfigurationException {
        Optional<Authorizer> op = AUTHORIZERS_CACHE
                .getLoading(name);

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

    public Set<PluginRecord<RequestInterceptor>> getRequestInterceptors() {
        return requestInterceptors;
    }

    public Set<PluginRecord<ResponseInterceptor>> getResponseInterceptors() {
        return responseInterceptors;
    }
}
