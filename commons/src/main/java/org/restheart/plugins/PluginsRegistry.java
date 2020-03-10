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
package org.restheart.plugins;

import io.undertow.predicate.Predicate;
import java.util.Set;
import org.restheart.ConfigurationException;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authenticator;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.TokenManager;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface PluginsRegistry {
    /**
     * @return the authMechanisms
     */
    public Set<PluginRecord<AuthMechanism>> getAuthMechanisms();
    
    /**
     * @return the authenticators
     */
    public Set<PluginRecord<Authenticator>> getAuthenticators();

    /**
     *
     * @param name the name of the authenticator
     * @return the authenticator
     * @throws org.restheart.ConfigurationException
     */
    public PluginRecord<Authenticator> getAuthenticator(String name) throws
            ConfigurationException;


    /**
     * @return the authenticators
     */
    public PluginRecord<TokenManager> getTokenManager();

    /**
     * @return the authenticators
     */
    public Set<PluginRecord<Authorizer>> getAuthorizers();

    /**
     * @return the initializers
     */
    public Set<PluginRecord<Initializer>> getInitializers();

    /**
     * @return the preStartupInitializers
     */
    public Set<PluginRecord<PreStartupInitializer>> getPreStartupInitializers();
    
    /**
     * @return the services
     */
    public Set<PluginRecord<Service>> getServices();
    
    public Set<PluginRecord<Interceptor>> getInterceptors();
    
    /**
     * global security predicates must all resolve to true to allow the request
     *
     * @return the globalSecurityPredicates allow to get and set the global
     * security predicates to apply to all requests
     */
    public Set<Predicate> getGlobalSecurityPredicates();
    
    /**
     *
     * @return the globalCheckers
     */
    public Set<PluginRecord<Checker>> getCheckers();
    
    /**
     *
     * @return the transformers
     */
    public Set<PluginRecord<Transformer>> getTransformers();
    
    /**
     *
     * @return the hooks
     */
    public Set<PluginRecord<Hook>> getHooks();
    
    /**
     *
     * @return the globalCheckers
     */
    public Set<GlobalChecker> getGlobalCheckers();
    
    /**
     * @return the globalTransformers
     */
    public Set<GlobalTransformer> getGlobalTransformers();

    /**
     * @return the globalHooks
     */
    public Set<GlobalHook> getGlobalHooks();
}
