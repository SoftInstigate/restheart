/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
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
