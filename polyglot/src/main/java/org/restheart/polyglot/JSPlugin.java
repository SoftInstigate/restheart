/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.restheart.configuration.Configuration;
import org.restheart.polyglot.services.JSServiceArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

public abstract class JSPlugin {
    protected static final Logger LOGGER = LoggerFactory.getLogger(JSPlugin.class);

    private static volatile Engine engine;

    /**
     * Returns the shared polyglot Engine, creating it on the dedicated
     * platform thread on first access.  Lazy initialization avoids the
     * deadlock that would occur if we created the Engine in a static
     * initializer (the main thread holds the class-init lock while
     * waiting for the platform thread).
     */
    public static Engine engine() {
        if (engine == null) {
            synchronized (JSPlugin.class) {
                if (engine == null) {
                    try {
                        if (PolyglotThreadUtils.isAlreadyOnPlatformThread()) {
                            engine = PolyglotClassloaderHelper.withPluginsClassloaderResult(Engine::create);
                        } else {
                            engine = PolyglotThreadUtils.onPlatformThread(
                                () -> PolyglotClassloaderHelper.withPluginsClassloaderResult(Engine::create));
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("Error creating polyglot Engine", e);
                    }
                }
            }
        }
        return engine;
    }

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

}
