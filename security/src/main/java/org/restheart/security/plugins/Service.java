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

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class Service extends PipedHttpHandler
        implements ConfigurablePlugin {

    private final String name;

    private final String uri;

    private final Boolean secured;

    /**
     * The configuration properties passed to this handler.
     */
    private final Map<String, Object> args;

    /**
     * Creates a new instance of the Service
     *
     * @param next
     * @param name
     * @param uri
     * @param secured
     * @param args
     */
    public Service(PipedHttpHandler next,
            String name,
            String uri,
            Boolean secured,
            Map<String, Object> args) {
        super(next);
        this.name = name;
        this.uri = uri;
        this.secured = secured;
        this.args = args;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * @return the secured
     */
    public Boolean getSecured() {
        return secured;
    }

    /**
     * @return the args
     */
    public Map<String, Object> getArgs() {
        return args;
    }
}
