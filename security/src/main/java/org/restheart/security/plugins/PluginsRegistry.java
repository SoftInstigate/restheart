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

    private Set<PluginRecord<AuthMechanism>> authMechanisms;
    
    private Set<PluginRecord<Service>> services;

    private Set<PluginRecord<Initializer>> initializers;

    private Set<PluginRecord<PreStartupInitializer>> preStartupInitializers;

    private Set<PluginRecord<RequestInterceptor>> requestInterceptors;

    private Set<PluginRecord<ResponseInterceptor>> responseInterceptors;

    private static PluginsRegistry HOLDER;

    public static synchronized PluginsRegistry getInstance() {
        if (HOLDER == null) {
            HOLDER = new PluginsRegistry();
        }

        return HOLDER;
    }

    private PluginsRegistry() {
    }
    
    /**
     * @return the authMechanisms
     */
    public Set<PluginRecord<AuthMechanism>> getAuthMechanisms() {
        if (this.authMechanisms == null) {
            this.authMechanisms = new LinkedHashSet<>();
            this.authMechanisms.addAll(PluginsFactory.authMechanisms());
        }
        
        return this.authMechanisms;
    }

    /**
     * @return the initializers
     */
    public Set<PluginRecord<Initializer>> getInitializers() {
        if (this.initializers == null) {
            this.initializers = new LinkedHashSet<>();
            this.initializers.addAll(PluginsFactory.initializers());
        }
        
        return this.initializers;
    }

    /**
     * @return the preStartupInitializers
     */
    public Set<PluginRecord<PreStartupInitializer>> getPreStartupInitializers() {
        if (this.preStartupInitializers == null) {
            this.preStartupInitializers = new LinkedHashSet<>();
            this.preStartupInitializers.addAll(
                    PluginsFactory.preStartupInitializers());
        }
        
        return this.preStartupInitializers;
    }
    
    public Set<PluginRecord<RequestInterceptor>> getRequestInterceptors() {
        if (this.requestInterceptors == null) {
            this.requestInterceptors = new LinkedHashSet<>();
            this.requestInterceptors.addAll(PluginsFactory
                    .requestInterceptors());
        }
        
        return requestInterceptors;
    }

    public Set<PluginRecord<ResponseInterceptor>> getResponseInterceptors() {
        if (this.responseInterceptors == null) {
            this.responseInterceptors = new LinkedHashSet<>();
            this.responseInterceptors.addAll(PluginsFactory
                    .responseInterceptors());
        }
        
        return responseInterceptors;
    }

    /**
     * @return the services
     */
    public Set<PluginRecord<Service>> getServices() {
        if (this.services == null) {
            this.services = new LinkedHashSet<>();
            this.services.addAll(PluginsFactory.services());
        }
        
        return this.services;
    }

    /**
     * @param name
     * @return the authenticators
     * @throws org.restheart.security.ConfigurationException
     */
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

    /**
     * @param name
     * @return the authorizers
     * @throws org.restheart.security.ConfigurationException
     */
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
    
    /**
     * @return the token manager
     * @throws org.restheart.security.ConfigurationException
     */
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
}
