package org.restheart.db;

import org.bson.BsonDocument;

/**
 *
 * @author Nath Papadacis {@literal <nath@thirststudios.co.uk>}
 */
public interface FileMetadataRepository {

    public abstract OperationResult updateMetadata(
            String dbName,
            String collName,
            Object documentId,
            BsonDocument filter,
            BsonDocument shardKeys,
            BsonDocument newContent,
            String requestEtag,
            boolean patching,
            boolean checkEtag);

}
