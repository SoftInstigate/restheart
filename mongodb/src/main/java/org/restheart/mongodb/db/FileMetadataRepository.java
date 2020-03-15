package org.restheart.mongodb.db;

import com.mongodb.client.ClientSession;
import org.bson.BsonDocument;
import org.restheart.handlers.exchange.OperationResult;

/**
 *
 * @author Nath Papadacis {@literal <nath@thirststudios.co.uk>}
 */
public interface FileMetadataRepository {

    /**
     * 
     * @param cs the client session
     * @param dbName
     * @param collName
     * @param documentId
     * @param filter
     * @param shardKeys
     * @param newContent
     * @param requestEtag
     * @param patching
     * @param checkEtag
     * @return 
     */
    public abstract OperationResult updateMetadata(
            final ClientSession cs,
            final String dbName,
            final String collName,
            final Object documentId,
            final BsonDocument filter,
            final BsonDocument shardKeys,
            final BsonDocument newContent,
            final String requestEtag,
            final boolean patching,
            final boolean checkEtag);

}
