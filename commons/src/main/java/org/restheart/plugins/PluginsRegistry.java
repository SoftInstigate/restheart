/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins;

import io.undertow.predicate.Predicate;
import io.undertow.server.handlers.PathHandler;
import java.util.Set;
import org.restheart.ConfigurationException;
import org.restheart.exchange.PipelineInfo;
import org.restheart.handlers.PipelinedHandler;
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
     * Gets the RESTHeart root handler
     * 
     * Avoid adding handlers using PathHandler.addPrefixPath() or
     * PathHandler.addExactPath(). Instead use PluginsRegistry.plug() which sets
     * also the correct PipelineInfo
     *
     * @return the RESTHeart root handler
     */
    public PathHandler getRootPathHandler();

    /**
     * Plugs a pipeline into the root handler binding it to the path; also sets
     * its PipelineInfo.
     *
     * @param path If the request contains this path, run the pipeline
     * @param handler The handler which is activated upon match
     * @param info The PipelineInfo describing the handling pipeline
     */
    public void plugPipeline(String path, PipelinedHandler handler, PipelineInfo info);

    /**
     * @param path
     * @return the PipelineInfo of the pipeline handling the request
     */
    public PipelineInfo getPipelineInfo(String path);
}
