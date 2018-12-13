/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
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
package io.uiam.plugins.service;

import java.util.Map;

import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.ConfigurablePlugin;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class PluggableService extends PipedHttpHandler
        implements ConfigurablePlugin {

    private final String name;

    private final String uri;

    private final Boolean secured;

    /**
     * The configuration properties passed to this handler.
     */
    private final Map<String, Object> args;

    /**
     * Creates a new instance of the PluggableService
     *
     * @param next
     * @param name
     * @param uri
     * @param secured
     * @param args
     */
    public PluggableService(PipedHttpHandler next,
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
