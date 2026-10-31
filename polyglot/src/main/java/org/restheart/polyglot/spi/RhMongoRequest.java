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
package org.restheart.polyglot.spi;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.restheart.exchange.MongoRequest;
import org.restheart.polyglot.spi.proxy.BsonProxy;
import org.restheart.polyglot.spi.proxy.ReadOnlyObject;

/**
 * {@code req.mongo}: the MongoDB specific part of a request handled by the MongoDB service, a
 * curated subset of {@link MongoRequest} and not a wholesale re-export of its ninety methods.
 *
 * <ul>
 *   <li>{@code dbName}, {@code collectionName}, {@code documentId} (native, {@code { $oid }} for an ObjectId)</li>
 *   <li>{@code filter}, {@code sort}, {@code projection}: the parsed query parameters, null when absent</li>
 *   <li>{@code page}, {@code pagesize}, {@code count}</li>
 *   <li>{@code isDocument()}, {@code isCollection()}, {@code isDb()}, {@code isCollectionIndexes()},
 *       {@code isFile()}, {@code isBulkDocuments()}, {@code isCount()}</li>
 *   <li>{@code raw}: the {@link MongoRequest}</li>
 * </ul>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhMongoRequest extends ReadOnlyObject {
    private static final List<String> KEYS = List.of("dbName", "collectionName", "documentId", "filter", "sort",
            "projection", "page", "pagesize", "count", "raw",
            "isDocument", "isCollection", "isDb", "isCollectionIndexes", "isFile", "isBulkDocuments", "isCount");

    private final MongoRequest request;

    RhMongoRequest(MongoRequest request) {
        this.request = request;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "dbName" -> request.getDBName();
            case "collectionName" -> request.getCollectionName();
            case "documentId" -> BsonProxy.toGuest(request.getDocumentId());
            case "filter" -> request.getFilter() == null || request.getFilter().isEmpty() ? null : BsonProxy.toGuest(request.getFiltersDocument());
            case "sort" -> request.getSortBy() == null || request.getSortBy().isEmpty() ? null : BsonProxy.toGuest(request.getSortByDocument());
            case "projection" -> request.getKeys() == null || request.getKeys().isEmpty() ? null : BsonProxy.toGuest(request.getProjectionDocument());
            case "page" -> request.getPage();
            case "pagesize" -> request.getPagesize();
            case "count" -> request.isCount();
            case "raw" -> request;
            case "isDocument" -> (ProxyExecutable) a -> request.isDocument();
            case "isCollection" -> (ProxyExecutable) a -> request.isCollection();
            case "isDb" -> (ProxyExecutable) a -> request.isDb();
            case "isCollectionIndexes" -> (ProxyExecutable) a -> request.isCollectionIndexes();
            case "isFile" -> (ProxyExecutable) a -> request.isFile();
            case "isBulkDocuments" -> (ProxyExecutable) a -> request.isBulkDocuments();
            case "isCount" -> (ProxyExecutable) a -> request.isCount();
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromList(new ArrayList<Object>(KEYS));
    }

    @Override
    public boolean hasMember(String key) {
        return KEYS.contains(key);
    }
}
