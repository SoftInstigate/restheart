/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2025 SoftInstigate
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

import org.apache.commons.jxpath.JXPathContext;
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

    /**
     * To enhance the performance of XPath expression evaluations, this method caches the
     * JXPathContext object. This cached context facilitates quicker lookups and reduces the
     * overhead associated with creating new contexts for each query.
     *
     * For more information, refer to {@link org.restheart.utils.BsonUtils#get(JXPathContext docCtx, String path)}
     *
     * @param exchange The exchange containing the BsonValue from which the JXPathContext is constructed.
     * @return The JXPathContext built from the BsonValue attached to the exchange.
    */
    public static JXPathContext jxPathCtx(HttpServerExchange exchange) {
        var ctx = exchange.getAttachment(JX_PATH_CTX_KEY);

        if (ctx == null) {
            ctx = JXPathContext.newContext(value(exchange));
            exchange.putAttachment(JX_PATH_CTX_KEY, ctx);
        }

        return ctx;
    }

    private static final AttachmentKey<BsonValue> DOC_KEY = AttachmentKey.create(BsonValue.class);
    private static final AttachmentKey<JXPathContext> JX_PATH_CTX_KEY = AttachmentKey.create(JXPathContext.class);
}
