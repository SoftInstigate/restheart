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
package org.restheart.mongodb.db.sessions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import com.mongodb.client.MongoClient;
import com.mongodb.client.internal.OperationExecutor;
import com.mongodb.internal.connection.Cluster;
import com.mongodb.internal.session.ServerSessionPool;

import org.restheart.mongodb.RHMongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SessionsUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionsUtils.class);

    private static final MongoClient MCLIENT = RHMongoClients.mclient();

    /**
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public static Cluster getCluster() {
        try {
            @SuppressWarnings("rawtypes")
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
            @SuppressWarnings("rawtypes")
            Class clazz = MCLIENT.getClass();
            Method getServerSessionPool = clazz.getDeclaredMethod("getServerSessionPool");
            getServerSessionPool.setAccessible(true);

            return (ServerSessionPool) getServerSessionPool.invoke(MCLIENT);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException ex) {
            LOGGER.error("error invokng MongoClient.getCluster() through reflection", ex);
            return null;
        }
    }

    public static OperationExecutor getOperationExecutor() {
        try {
            var mclient = RHMongoClients.mclient();
            var getOperationExecutor = mclient.getClass().getDeclaredMethod("getOperationExecutor");

            getOperationExecutor.setAccessible(true);
            return (OperationExecutor) getOperationExecutor.invoke(mclient);
        } catch(Throwable t) {
            throw new RuntimeException("could not instantiate OperationExecutor", t);
        }
    }
}
