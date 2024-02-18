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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry to keep track of ChangeStreamWorkers
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ChangeStreamWorkers {
    // todo use caffeine cache
    private final Map<ChangeStreamWorke, ChangeStreamWorker> CHANGE_STREAM_WORKERS = new ConcurrentHashMap<>();

    public static ChangeStreamWorkers getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public Optional<ChangeStreamWorker> get(ChangeStreamWorke key) {
        var csw = CHANGE_STREAM_WORKERS.get(key);

        if (csw == null) {
            return Optional.empty();
        } else {
            return Optional.of(csw);
        }
    }

    public boolean put(ChangeStreamWorker csw) {
        return CHANGE_STREAM_WORKERS.putIfAbsent(csw.getKey(), csw) == null;
    }

    public boolean remove(ChangeStreamWorke key) {
         return CHANGE_STREAM_WORKERS.remove(key) == null;
    }

    public Set<ChangeStreamWorker> getWorkersOnDb(String db) {
        if (db == null) {
            return new HashSet<>();
        } else {
            return CHANGE_STREAM_WORKERS.entrySet().stream()
                .map(e -> e.getValue())
                .filter(csw -> db.equals(csw.getDbName()))
                .collect(Collectors.toSet());
        }
    }

    public Set<ChangeStreamWorker> getWorkersOnCollection(String db, String coll) {
        if (db == null) {
            return new HashSet<>();
        } else {
            return CHANGE_STREAM_WORKERS.entrySet().stream()
                .map(e -> e.getValue())
                .filter(csw -> db.equals(csw.getDbName()) &&  db.equals(csw.getCollName()))
                .collect(Collectors.toSet());
        }
    }

    private static class SingletonHolder {
        private static final ChangeStreamWorkers INSTANCE = new ChangeStreamWorkers();
    }
}
