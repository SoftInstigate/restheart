/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2022 SoftInstigate
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
package org.restheart.polyglot.interceptors;

import java.util.Map;

import com.mongodb.client.MongoClient;

import org.graalvm.polyglot.Source;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;

public class MongoJSInterceptor extends AbstractJSInterceptor<MongoRequest, MongoResponse> {
    public MongoJSInterceptor(String name,
        String pluginClass,
        String description,
        InterceptPoint interceptPoint,
        Source handleSource,
        Source resolveSource,
        MongoClient mclient,
        Map<String, Object> pluginArgs,
        Map<String, String> contextOptions) {
            super(name, pluginClass, description, interceptPoint, handleSource, resolveSource, mclient, pluginArgs, contextOptions);
    }
}