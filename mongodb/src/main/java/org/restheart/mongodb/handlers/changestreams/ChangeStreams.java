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
 * Registry to keep track of open change streams
 * wraps a map to easily allow to get corresponding ChangeStreamInfo that
 * encapsulates db, coll, and opName
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChangeStreams {
    // todo use caffeine cache
    private final Map<ChangeStreamKey, ChangeStreamInfo> CHANGE_STREAMS = new ConcurrentHashMap<>();

    public static ChangeStreams getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public ChangeStreamInfo get(ChangeStreamKey key) {
        return CHANGE_STREAMS.get(key);
    }

    public Set<ChangeStreamKey> keySet() {
        return CHANGE_STREAMS.keySet();
    }

    public ChangeStreamInfo put(ChangeStreamKey key, ChangeStreamInfo info) {
        return CHANGE_STREAMS.put(key, info);
    }

    public boolean containsKey(ChangeStreamKey key) {
        return CHANGE_STREAMS.containsKey(key);
    }

    public ChangeStreamInfo remove(ChangeStreamKey key) {
        return CHANGE_STREAMS.remove(key);
    }

    public Set<ChangeStreamKey> getChangeStreamKeysOnDb(String db) {
        var ret = new HashSet<ChangeStreamKey>();
        if (db == null) {
            return ret;
        } else {
            keySet().stream()
                .filter(k -> db.equals(get(k).getDb()))
                .forEach(ret::add);
            return ret;
        }
    }

    public Set<ChangeStreamKey> getChangeStreamKeysOnCollection(String db, String coll) {
        var ret = new HashSet<ChangeStreamKey>();
        if (db == null || coll == null) {
            return ret;
        } else {
            keySet().stream()
                .filter(k -> db.equals(get(k).getDb()) && coll.equals(get(k).getCollection()))
                .forEach(ret::add);
            return ret;
        }
    }

    public Set<ChangeStreamKey> getChangeStreamKeysOnOperation(String db, String coll, String operation) {
        var ret = new HashSet<ChangeStreamKey>();
        if (db == null || coll == null || operation == null) {
            return ret;
        } else {
            keySet().stream()
                .filter(k -> db.equals(get(k).getDb()) && coll.equals(get(k).getCollection()) && operation.equals(get(k).getChangeStreamOperation()))
                .forEach(ret::add);
            return ret;
        }
    }

    private static class SingletonHolder {
        private static final ChangeStreams INSTANCE = new ChangeStreams();
    }
}
