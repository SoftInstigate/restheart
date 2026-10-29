/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import com.mongodb.MongoClientSettings;
import com.mongodb.internal.TimeoutSettings;
import com.mongodb.internal.connection.Cluster;
import com.mongodb.internal.session.ServerSessionPool;
import com.mongodb.internal.thread.AsyncClientExecutor;
import com.mongodb.session.ServerSession;
import java.util.UUID;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SimpleServerSessionPool extends ServerSessionPool {
    private final UUID sid;

    /**
     *
     * @param cluster
     * @param sid
     */
    public SimpleServerSessionPool(Cluster cluster, UUID sid) {
        this(cluster, sid, SessionsUtils.getSettings());
    }

    private SimpleServerSessionPool(Cluster cluster, UUID sid, MongoClientSettings settings) {
        // Same arguments the driver's MongoClientImpl uses. The pool's OperationContext is only
        // used to end pooled sessions on close(); this pool never pools sessions (get() is
        // overridden), so the no-op executor is enough.
        super(cluster, AsyncClientExecutor.NO_OP, TimeoutSettings.create(settings), settings.getServerApi());
        this.sid = sid;
    }

    /**
     *
     * @return
     */
    @Override
    public ServerSession get() {
        return ClientSessionFactory.getInstance().createServerSession(sid);
    }
}
