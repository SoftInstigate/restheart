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
package org.restheart.mongodb.handlers.changestreams;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry to keep track of open change streasms
 *
 * wraps a map to easily allow to get corresponding SessionInfo that
 * encapsulates db, coll, and opName
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChangeStreamsRegistry {

    private final Map<SessionKey, SessionInfo> OPENED_STREAMS = new ConcurrentHashMap<SessionKey, SessionInfo>();

    public static ChangeStreamsRegistry getInstance() {
        return ChangeStreamsRegistrySingletonHolder.INSTANCE;
    }

    public SessionInfo get(SessionKey key) {
        return OPENED_STREAMS.get(key);
    }

    public Set<SessionKey> keySet() {
        return OPENED_STREAMS.keySet();
    }

    public SessionInfo put(SessionKey key, SessionInfo info) {
        return OPENED_STREAMS.put(key, info);
    }

    public boolean containsKey(SessionKey key) {
        return OPENED_STREAMS.containsKey(key);
    }

    public SessionInfo remove(SessionKey key) {
        return OPENED_STREAMS.remove(key);
    }

    public Set<SessionKey> getSessionKeysOnDb(String db) {
        var ret = new HashSet<SessionKey>();
        if (db == null) {
            return ret;
        } else {
            keySet().stream()
            .filter(k -> db.equals(get(k).getDb()))
            .forEach(ret::add);
            return ret;
        }
    }

    public Set<SessionKey> getSessionKeysOnCollection(String db, String coll) {
        var ret = new HashSet<SessionKey>();
        if (db == null || coll == null) {
            return ret;
        } else {
            keySet().stream()
            .filter(k -> db.equals(get(k).getDb()) && coll.equals(get(k).getCollection()))
            .forEach(ret::add);
            return ret;
        }
    }

    public Set<SessionKey> getSessionKeysOnOperation(String db, String coll, String operation) {
        var ret = new HashSet<SessionKey>();
        if (db == null || coll == null || operation == null) {
            return ret;
        } else {
            keySet().stream()
            .filter(k -> db.equals(get(k).getDb()) && coll.equals(get(k).getCollection()) && operation.equals(get(k).getChangeStreamOperation()))
            .forEach(ret::add);
            return ret;
        }
    }

    private static class ChangeStreamsRegistrySingletonHolder {
        private static final ChangeStreamsRegistry INSTANCE = new ChangeStreamsRegistry();
    }
}
