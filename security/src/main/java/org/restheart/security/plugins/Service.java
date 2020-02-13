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

import java.util.Map;

import org.restheart.security.handlers.PipedHttpHandler;
import org.restheart.security.handlers.ResponseSender;

/**
 * @see https://restheart.org/docs/develop/security-plugins/#services
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class Service extends PipedHttpHandler
        implements ConfigurablePlugin {
/**
     * The configuration properties passed to this handler.
     */
    protected final Map<String, Object> confArgs;

    /**
     * Creates a new instance of the Service
     *
     * @param confArgs arguments optionally specified in the configuration file
     */
    public Service(Map<String, Object> confArgs) {
        super(new ResponseSender());
        this.confArgs = confArgs;
    }
    
    /**
     *
     * @return the default uri of the service, used if not specified in plugin
     * configuration
     */
    public String defaultUri() {
        return null;
    }
}