/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.spi.proxy;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * A {@link ProxyObject} the guest can read and not write: the data the runtime hands to a
 * plugin (query parameters, headers, the account, configuration) describes the request and is
 * not the way to change it.
 *
 * <p>An assignment from the guest throws, and so shows up as an error in the plugin rather
 * than being silently dropped.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class ReadOnlyObject implements ProxyObject {
    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("read-only object, cannot set '" + key + "'");
    }

    @Override
    public boolean removeMember(String key) {
        throw new UnsupportedOperationException("read-only object, cannot remove '" + key + "'");
    }
}
