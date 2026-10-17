/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.interceptors;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.restheart.ai.util.BucketChunkingConfig;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;

import com.mongodb.client.MongoClient;

/**
 * Before a bulk delete of files in a bucket with chunking rules, notes the ids of the files the
 * filter selects, for {@link DocumentChunkingInterceptor} to delete their chunks afterwards. After
 * the delete the files are gone, and so is any way to know which they were. The chunker then
 * deletes the chunks only of the candidates that are actually gone: a write filter of the ACL may
 * have spared some.
 */
@RegisterPlugin(
        name = "chunkedFilesDeleteCollector",
        description = "notes which files a bulk delete may remove from a bucket with chunking rules, so their chunks can be deleted",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
        // on by default: it acts only on a bulk delete of files in a bucket with chunking rules
        enabledByDefault = true)
public class ChunkedFilesDeleteCollector implements MongoInterceptor {

    /** The attached parameter with the ids, a {@code List<BsonValue>}. */
    static final String CANDIDATES = "restheart-ai-chunked-files-delete-candidates";

    @Inject("mclient")
    private MongoClient mclient;

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var filter = request.getFiltersDocument();
        var ids = new ArrayList<BsonValue>();
        mclient.getDatabase(request.getDBName())
                .getCollection(request.getCollectionName(), BsonDocument.class)
                .find(filter == null ? new BsonDocument() : filter)
                .projection(new BsonDocument("_id", new BsonInt32(1)))
                .forEach(d -> ids.add(d.get("_id")));
        request.attachParam(CANDIDATES, List.copyOf(ids));
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && DocumentChunkingInterceptor.isBulkFilesDelete(request)
                && BucketChunkingConfig.declares(request.getCollectionProps());
    }
}
