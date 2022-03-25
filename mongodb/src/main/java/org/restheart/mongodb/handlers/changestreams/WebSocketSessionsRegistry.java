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
package org.restheart.mongodb.handlers.changestreams;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import java.util.Set;


/**
 * Registry to keep track of web socket sessions
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */
public class WebSocketSessionsRegistry {

    private final SetMultimap<SessionKey, ChangeStreamWebSocketSession> MULTIMAP = Multimaps
            .synchronizedSetMultimap(Multimaps.synchronizedSetMultimap(HashMultimap.<SessionKey, ChangeStreamWebSocketSession>create()));

    public static WebSocketSessionsRegistry getInstance() {
        return CacheManagerSingletonHolder.INSTANCE;
    }

    public Set<ChangeStreamWebSocketSession> get(SessionKey key) {
        return MULTIMAP.get(key);
    }

    public Set<SessionKey> keySet() {
        return MULTIMAP.keySet();
    }

    public boolean add(SessionKey key, ChangeStreamWebSocketSession session) {
        return MULTIMAP.put(key, session);
    }

    public boolean remove(SessionKey key, ChangeStreamWebSocketSession session) {
        return MULTIMAP.remove(key, session);
    }

    private static class CacheManagerSingletonHolder {
        private static final WebSocketSessionsRegistry INSTANCE = new WebSocketSessionsRegistry();
    }
}
