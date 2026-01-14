/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2025 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.polyglot;

import java.util.Map;
import java.util.Optional;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.restheart.configuration.Configuration;
import org.restheart.polyglot.services.JSServiceArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

public abstract class JSPlugin {
    protected static final Logger LOGGER = LoggerFactory.getLogger(JSPlugin.class);

    private static final Engine engine = Engine.create();

    private final String modulesReplacements;
    private final Source handleSource;

    private final String name;
    private final String description;
    private final Optional<MongoClient> mclient;
    private final Configuration configuration;

    protected ContextQueue contextQueue;

    /**
     *
     * @param name
     * @param configuration
     * @param description
     * @param modulesReplacements
     * @param handleSource
     * @param mclient
     * @param opts
     */
    public JSPlugin(String name,
        String description,
        Source handleSource,
        String modulesReplacements,
        Configuration configuration,
        Optional<MongoClient> mclient,
        Map<String, String> opts) {
        this.name = name;
        this.description = description;
        this.handleSource = handleSource;
        this.mclient = mclient;
        this.configuration = configuration;
        this.modulesReplacements = modulesReplacements;
        this.contextQueue = new ContextQueue(engine, name, configuration, LOGGER, mclient, modulesReplacements, opts);
    }

    /**
     *
     * @param args
     */
    public JSPlugin(JSServiceArgs args) {
        this.name = args.name();
        this.description = args.description();
        this.handleSource = args.handleSource();
        this.mclient = args.mclient();
        this.configuration = args.configuration();
        this.modulesReplacements = args.modulesReplacements();
        this.contextQueue = new ContextQueue(engine, name, configuration, LOGGER, mclient, modulesReplacements, args.contextOptions());
    }

    public String name() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Provides access to the context queue for executing JavaScript code within a scoped context.
     * 
     * @return the context queue instance
     */
    protected ContextQueue contextQueue() {
        return this.contextQueue;
    }

    public Optional<MongoClient> mclient() {
        return mclient;
    }

    public Source handleSource() {
        return handleSource;
    }

    public Configuration configuration() {
        return configuration;
    }

    public static Engine engine() {
        return engine;
    }
}
