/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "mongoClients",
        description = "helper singleton that holds the MongoClients",
        initPoint = InitPoint.BEFORE_STARTUP,
        priority = -10)
public class RHMongoClients implements Initializer {

    @Inject("mclient")
    private com.mongodb.client.MongoClient mclient;

    @Inject("mclient-reactive")
    private com.mongodb.reactivestreams.client.MongoClient mclientReactive;

    private static com.mongodb.client.MongoClient MC_HOLDER;
    private static com.mongodb.reactivestreams.client.MongoClient MCR_HOLDER;

    @OnInit
    public void onInit() {
        MC_HOLDER = mclient;
        MCR_HOLDER = mclientReactive;
    }

    @Override
    public void init() {
    }

    public static com.mongodb.client.MongoClient mclient() {
        return MC_HOLDER;
    }

    public static com.mongodb.reactivestreams.client.MongoClient mclientReactive() {
        return MCR_HOLDER;
    }

    @VisibleForTesting
    public static void setClients(com.mongodb.client.MongoClient mclient, com.mongodb.reactivestreams.client.MongoClient mclientReactive) {
        MC_HOLDER = mclient;
        MCR_HOLDER = mclientReactive;
    }
}
