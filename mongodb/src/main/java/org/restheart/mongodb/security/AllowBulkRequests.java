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

package org.restheart.mongodb.security;

import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.MongoPermissions;
import org.restheart.utils.HttpStatus;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;

@RegisterPlugin(name = "mongoPermissionAllowBulkRequests",
    description = "Allow bulk PATCH and bulk DELETE according to the mongo.allowBulkPatch and mongo.allowBulkDelete ACL permissions",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    enabledByDefault = true)
public class AllowBulkRequests implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        response.setStatusCode(HttpStatus.SC_FORBIDDEN);
        response.setInError(true);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        var mongoPermissions = MongoPermissions.of(request);

        if (!request.isHandledBy("mongo") || mongoPermissions == null) {
            return false;
        }

        return (!mongoPermissions.isAllowBulkDelete() && request.isBulkDocuments() && request.isDelete())
            || (!mongoPermissions.isAllowBulkPatch() && request.isBulkDocuments() && request.isPatch());
    }
}
