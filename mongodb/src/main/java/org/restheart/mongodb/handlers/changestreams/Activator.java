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
package org.restheart.mongodb.handlers.changestreams;

import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.TYPE;
import static org.restheart.mongodb.ConnectionChecker.connected;
import static org.restheart.mongodb.ConnectionChecker.replicaSet;
import org.restheart.mongodb.handlers.RequestDispatcherHandler;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "changeStreamActivator",
        description = "activates support for change streams",
        priority = Integer.MIN_VALUE + 1)
public class Activator implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    @Inject("mclient")
    private MongoClient mclient;

    @Override
    public void init() {
        if (!connected(mclient)) {
            LOGGER.error("Cannot enable Change Streams: MongoDB not connected.");
        } else {
            if (replicaSet(mclient)) {
                enableChangeStreams();
            } else {
                LOGGER.warn("Cannot enable Change Streams: MongoDB is a standalone instance and Change Streams require a Replica Set.");
            }
        }
    }

    private void enableChangeStreams() {
        var dispatcher = RequestDispatcherHandler.getInstance();

        try {
            // Add Change Stream handler
            dispatcher.putHandler(TYPE.CHANGE_STREAM, METHOD.GET, new GetChangeStreamHandler());
        } catch (Throwable t) {
            LOGGER.error("Error, change streams disabled {}", t.getMessage(), t);
        }
    }
}
