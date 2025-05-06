/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.client.MongoClient;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "mongoClients",
        description = "helper singleton that holds the MongoClient",
        initPoint = InitPoint.BEFORE_STARTUP,
        priority = -10)
public class RHMongoClients implements Initializer {

    @Inject("mclient")
    private MongoClient mclient;

    private static MongoClient MC_HOLDER;

    @OnInit
    public void onInit() {
        MC_HOLDER = mclient;
    }

    @Override
    public void init() {
    }

    public static com.mongodb.client.MongoClient mclient() {
        return MC_HOLDER;
    }

    @VisibleForTesting
    public static void setClients(MongoClient mclient) {
        MC_HOLDER = mclient;
    }
}
