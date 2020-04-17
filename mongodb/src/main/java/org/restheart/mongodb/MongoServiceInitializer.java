/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb;

import java.util.Map;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.PLUGINS_ARGS_KEY;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.mongodb.interceptors.MetadataCachesSingleton;
import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "mongoInitializer",
        description = "executes mongo service init tasks",
        initPoint = InitPoint.BEFORE_STARTUP,
        priority = -10)
public class MongoServiceInitializer implements Initializer {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(MongoService.class);

    private boolean mongoSrvEnabled = false;

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public void init(Map<String, Object> confArgs) {
        MongoServiceConfiguration.init(confArgs);

        this.mongoSrvEnabled = isMongoEnabled(confArgs);
    }

    @Override
    public void init() {
        if (!this.mongoSrvEnabled) {
            return;
        }

        // initialize MetadataCachesSingleton
        MetadataCachesSingleton.init(MongoServiceConfiguration.get());
    }

    @InjectPluginsRegistry
    public void injectMongoClient(PluginsRegistry pluginsRegistry) {
        if (!this.mongoSrvEnabled) {
            return;
        }

        MongoClientSingleton.init(MongoServiceConfiguration.get().getMongoUri(),
                pluginsRegistry);

        // force first connection to MongoDb
        MongoClientSingleton.getInstance();
    }

    private boolean isMongoEnabled(Map<String, Object> confArgs) {
        if (confArgs.get(PLUGINS_ARGS_KEY) != null
                && confArgs.get(PLUGINS_ARGS_KEY) instanceof Map) {
            var pa = (Map) confArgs.get(PLUGINS_ARGS_KEY);

            if (pa.get("mongo") != null
                    && pa.get("mongo") instanceof Map) {
                var mc = (Map) pa.get("mongo");

                if (mc.get("enabled") != null
                        && mc.get("enabled") instanceof Boolean) {
                    return (Boolean) mc.get("enabled");
                }
            }
        }

        return true;
    }
}
