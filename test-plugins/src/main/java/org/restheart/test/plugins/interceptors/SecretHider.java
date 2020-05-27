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

package org.restheart.test.plugins.interceptors;

import java.util.ArrayList;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "secretHider",
        description = "forbis write requests "
        + "on '/coll' "
        + "containing the property 'secret' "
        + "to users does not have the role 'admin'",
        enabledByDefault = false,
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class SecretHider implements MongoInterceptor {
    static final Logger LOGGER = LoggerFactory.getLogger(SecretHider.class);

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var content = request.getContent();

        if (keys(content).stream()
                .anyMatch(k -> "secret".equals(k)
                || k.endsWith(".secret"))) {

            response.setInError(HttpStatus.SC_FORBIDDEN,
                    "cannot write secret");
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo") 
                && !request.isAccountInRole("admin")
                && "/coll".equals(request.getCollectionName())
                && (request.isPost() || request.isPatch() || request.isPut());
    }

    /**
     * @return the keys of the JSON
     */
    private ArrayList<String> keys(BsonValue val) {
        var keys = new ArrayList<String>();

        if (val == null) {
            return keys;
        } else if (val.isDocument()) {
            val.asDocument().keySet().forEach(k -> {
                keys.add(k);
                keys.addAll(keys(val.asDocument().get(k)));
            });
        } else if (val.isArray()) {
            val.asArray().forEach(v -> keys.addAll(keys(v)));
        }

        return keys;
    }
}
