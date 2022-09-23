/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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

import org.restheart.Configuration;
import org.restheart.mongodb.db.sessions.TxnClientSessionFactory;
import org.restheart.mongodb.interceptors.MetadataCachesSingleton;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "mongoInitializer",
        description = "executes mongo service init tasks",
        initPoint = InitPoint.BEFORE_STARTUP,
        priority = -10)
public class MongoServiceInitializer implements Initializer {
    private boolean mongoSrvEnabled = false;

    @Inject("rh-config")
    private Configuration config;

    @OnInit
    public void onInit() {
        MongoServiceConfiguration.init(config.toMap());

        TxnClientSessionFactory.init(MongoServiceConfiguration.get().getMongoUri());

        this.mongoSrvEnabled = isMongoEnabled(config.toMap());

        if (!this.mongoSrvEnabled) {
            return;
        }
    }

    @Override
    public void init() {
        if (!this.mongoSrvEnabled) {
            return;
        }

        // initialize MetadataCachesSingleton
        MetadataCachesSingleton.init(MongoServiceConfiguration.get());
    }

    private boolean isMongoEnabled(Map<String, Object> confArgs) {
        if (confArgs.get(PLUGINS_ARGS_KEY) != null && confArgs.get(PLUGINS_ARGS_KEY) instanceof Map) {
            @SuppressWarnings("rawtypes")
            var pa = (Map) confArgs.get(PLUGINS_ARGS_KEY);

            if (pa.get("mongo") != null && pa.get("mongo") instanceof Map) {
                @SuppressWarnings("rawtypes")
                var mc = (Map) pa.get("mongo");

                if (mc.get("enabled") != null && mc.get("enabled") instanceof Boolean) {
                    return (Boolean) mc.get("enabled");
                }
            }
        }

        return true;
    }
}
