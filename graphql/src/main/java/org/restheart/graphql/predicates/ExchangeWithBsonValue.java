/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2023 SoftInstigate
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

package org.restheart.graphql.predicates;

import org.bson.BsonValue;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.AttachmentKey;

/**
 * Helper class to create a void HttpServerExchange with a BsonValue attachment
 * and retrieve the document from it
 *
 * Needed to make the undertow's predicates work with BsonValue
 */
public class ExchangeWithBsonValue {
    public static HttpServerExchange exchange(BsonValue value) {
        var e = new HttpServerExchange((ServerConnection)null);
        e.putAttachment(DOC_KEY, value);
        return e;
    }

    public static BsonValue value(HttpServerExchange exchange) {
        return exchange.getAttachment(DOC_KEY);
    }

    private static AttachmentKey<BsonValue> DOC_KEY = AttachmentKey.create(BsonValue.class);
}
