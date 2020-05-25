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
package org.restheart.mongodb.handlers.changestreams;

import com.google.common.collect.Multimaps;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import java.util.Set;


/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Omar Trasatti {@literal <omar@softinstigate.com>}
 */
public class GuavaHashMultimapSingleton {

    private final SetMultimap<SessionKey, ChangeStreamWebSocketSession> MULTIMAP = Multimaps
            .synchronizedSetMultimap(Multimaps.synchronizedSetMultimap(HashMultimap.<SessionKey, ChangeStreamWebSocketSession>create()));

    public static GuavaHashMultimapSingleton getInstance() {
        return CacheManagerSingletonHolder.INSTANCE;
    }

    public static Set<ChangeStreamWebSocketSession> get(SessionKey key) {
        return GuavaHashMultimapSingleton.getInstance().MULTIMAP.get(key);
    }
    
    
    public static boolean add(SessionKey key, ChangeStreamWebSocketSession session) {
        return GuavaHashMultimapSingleton.getInstance().MULTIMAP
                .put(key, session);
    }
    
    public static boolean remove(SessionKey key, ChangeStreamWebSocketSession session) {
        return GuavaHashMultimapSingleton.getInstance().MULTIMAP
                .remove(key, session);
    }

    private static class CacheManagerSingletonHolder {
        private static final GuavaHashMultimapSingleton INSTANCE = new GuavaHashMultimapSingleton();
    }
}
