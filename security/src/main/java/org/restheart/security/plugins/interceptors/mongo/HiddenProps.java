/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.security.plugins.interceptors.mongo;

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.plugins.authorizers.AclPermission;

import java.util.Set;

import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;

@RegisterPlugin(name = "mongoHiddenProps",
    description = "Hides properties from the response according to the mongo.hiddenProps ACL permission",
    interceptPoint = InterceptPoint.RESPONSE,
    enabledByDefault = true)
public class HiddenProps implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var hiddendProps = AclPermission.from(request.getExchange()).getMongoPermissions().getHiddenProps();

        if (response.getContent().isDocument()) {
            hide(response.getContent().asDocument(), hiddendProps);
        } else if (response.getContent().isArray()) {
            response.getContent().asArray().forEach(doc -> hide(doc.asDocument(), hiddendProps));
        }
    }

    private void hide(BsonDocument doc, Set<String> hiddenProps) {
        hiddenProps.stream().forEachOrdered(hiddenProp ->  hide(doc, hiddenProp));
    }

    private void hide(BsonDocument doc, String hiddenProp) {
        if (hiddenProp.contains(".")) {
            var first = hiddenProp.substring(0, hiddenProp.indexOf("."));
            if (first.length() > 0 && doc.containsKey(first) && doc.get(first).isDocument()) {
                hide(doc.get(first).asDocument(), hiddenProp.substring(hiddenProp.indexOf(".")+1));
            }
        } else if (hiddenProp.length() > 0) {
            doc.remove(hiddenProp);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (!request.isHandledBy("mongo") || response.getContent() == null) {
            return false;
        }

        var permission = AclPermission.from(request.getExchange());

        if (permission != null && permission.getMongoPermissions() != null) {
            return !permission.getMongoPermissions().getHiddenProps().isEmpty();
        } else {
            return false;
        }
    }
}
