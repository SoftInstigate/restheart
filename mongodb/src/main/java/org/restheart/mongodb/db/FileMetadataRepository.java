/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.db;

import java.util.Optional;

import com.mongodb.client.ClientSession;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.ExchangeKeys.METHOD;
public interface FileMetadataRepository {

    /**
     *
     * @param cs the client session
     * @param method the request method
     * @param dbName
     * @param collName
     * @param documentId
     * @param filter
     * @param shardKeys
     * @param newContent
     * @param requestEtag
     * @param checkEtag
     * @return the operation result
     */
    public abstract OperationResult updateFileMetadata(
            final Optional<ClientSession> cs,
            final METHOD method,
            final String dbName,
            final String collName,
            final Optional<BsonValue> documentId,
            final Optional<BsonDocument> filter,
            final Optional<BsonDocument> shardKeys,
            final BsonDocument newContent,
            final String requestEtag,
            final boolean checkEtag);
}
