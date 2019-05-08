/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.db.sessions;

import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.client.internal.MongoClientDelegate;
import com.mongodb.connection.Cluster;
import com.mongodb.internal.session.ServerSessionPool;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import static java.util.Collections.singletonList;
import java.util.List;
import org.restheart.db.MongoDBClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SessionsUtils {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(SessionsUtils.class);

    private static MongoClient MCLIENT = MongoDBClientSingleton
            .getInstance().getClient();

    private static MongoClientDelegate delegate;

    static {
        List<MongoCredential> credentialsList
                = MCLIENT.getCredential() != null
                ? singletonList(MCLIENT.getCredential())
                : Collections.<MongoCredential>emptyList();

        delegate = new MongoClientDelegate(
                getCluster(),
                credentialsList,
                MCLIENT);
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public static Cluster getCluster() {
        try {
            Class clazz = Class.forName("com.mongodb.Mongo");
            Method getCluster = clazz.getDeclaredMethod("getCluster");
            getCluster.setAccessible(true);

            return (Cluster) getCluster.invoke(MCLIENT);
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | SecurityException
                | IllegalAccessException
                | InvocationTargetException ex) {
            LOGGER.error("error invokng MongoClient.getCluster() through reflection", ex);
            return null;
        }
    }

    
    @SuppressWarnings({"unchecked", "deprecation"})
    public static ServerSessionPool getServerSessionPool() {
        try {
            Class clazz = Class.<Mongo>forName("com.mongodb.Mongo");
            Method getServerSessionPool = clazz.getDeclaredMethod("getServerSessionPool");
            getServerSessionPool.setAccessible(true);

            return (ServerSessionPool) getServerSessionPool.invoke(MCLIENT);
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | SecurityException
                | IllegalAccessException
                | InvocationTargetException ex) {
            LOGGER.error("error invokng MongoClient.getCluster() through reflection", ex);
            return null;
        }
    }

    public static MongoClientDelegate getMongoClientDelegate() {
        return delegate;
    }
}
