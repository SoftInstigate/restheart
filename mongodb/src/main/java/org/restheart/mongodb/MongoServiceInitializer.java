/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import org.restheart.configuration.Configuration;
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
        Map<String, Object> mongoConfig = config.getOrDefault("mongo", null);
        if (mongoConfig != null) {
            MongoServiceConfiguration.init(mongoConfig);

            TxnClientSessionFactory.init(MongoServiceConfiguration.get().getMongoUri());

            this.mongoSrvEnabled = isMongoEnabled(mongoConfig);
        } else {
            this.mongoSrvEnabled = false;
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

    private boolean isMongoEnabled(Map<String, Object> mc) {
        if (mc.get("enabled") != null && mc.get("enabled") instanceof Boolean enabled) {
            return enabled;
        } else {
            return true;
        }
    }
}
