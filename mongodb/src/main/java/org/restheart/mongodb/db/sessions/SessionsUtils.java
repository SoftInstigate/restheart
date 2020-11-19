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
package org.restheart.mongodb.db.sessions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.mongodb.MongoClient;
import com.mongodb.client.internal.MongoClientDelegate;
import com.mongodb.internal.connection.Cluster;
import com.mongodb.internal.session.ServerSessionPool;

import org.restheart.mongodb.db.MongoClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SessionsUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionsUtils.class);

    private static final MongoClient MCLIENT = MongoClientSingleton.getInstance().getClient();

    private static final MongoClientDelegate DELEGATE;

    static {
        DELEGATE = new MongoClientDelegate(getCluster(), null, MCLIENT, null);
    }

    /**
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public static Cluster getCluster() {
        try {
            Class clazz = MCLIENT.getClass();
            Method getCluster = clazz.getDeclaredMethod("getCluster");
            getCluster.setAccessible(true);

            return (Cluster) getCluster.invoke(MCLIENT);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException ex) {
            LOGGER.error("error invokng MongoClient.getCluster() through reflection", ex);
            return null;
        }
    }

    /**
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public static ServerSessionPool getServerSessionPool() {
        try {
            Class clazz = MCLIENT.getClass();
            Method getServerSessionPool = clazz.getDeclaredMethod("getServerSessionPool");
            getServerSessionPool.setAccessible(true);

            return (ServerSessionPool) getServerSessionPool.invoke(MCLIENT);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException ex) {
            LOGGER.error("error invokng MongoClient.getCluster() through reflection", ex);
            return null;
        }
    }

    /**
     *
     * @return
     */
    public static MongoClientDelegate getMongoClientDelegate() {
        return DELEGATE;
    }
}
