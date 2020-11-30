/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2020 SoftInstigate
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
package org.restheart.test.plugins.services;

import java.util.function.BiConsumer;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.RegisterPlugin;

/**
 * An idea to further simplify service definition. TBC
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */

public abstract class DelegateHandleService implements ByteArrayService  {
    BiConsumer<ByteArrayRequest, ByteArrayResponse> handle;

    public DelegateHandleService() {
        this.handle = (r,s) -> {};
    }
    
    public final void delegate(BiConsumer<ByteArrayRequest, ByteArrayResponse> handle) {
        this.handle = handle;
    }

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        this.handle.accept(request, response);
    }
}

@RegisterPlugin(name = "helloWorld", description = "")
class MyService extends DelegateHandleService {
    MyService() {
        delegate((r, s) -> s.setContent("Hello World".getBytes()));  
    }
}
