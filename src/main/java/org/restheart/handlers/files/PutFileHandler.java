package org.restheart.handlers.files;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.db.GridFsDAO;
import org.restheart.db.GridFsRepository;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class PutFileHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PutFileHandler.class);

    private final GridFsRepository gridFsDAO;

    public PutFileHandler() {
        super();
        this.gridFsDAO = new GridFsDAO();
    }

    public PutFileHandler(PipedHttpHandler next) {
        super(next);
        this.gridFsDAO = new GridFsDAO();
    }

    public PutFileHandler(PipedHttpHandler next, GridFsDAO gridFsDAO) {
        super(next);
        this.gridFsDAO = gridFsDAO;
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        final BsonValue _metadata = context.getContent();

        // must be an object
        if (!_metadata.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data cannot be an array");
            next(exchange, context);
            return;
        }

        BsonDocument metadata = _metadata.asDocument();

        BsonValue id = context.getDocumentId();

        if (metadata.get("_id") != null
                && metadata.get("_id").equals(id)) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "_id in content body is different than id in URL");
            next(exchange, context);
            return;
        }

        metadata.put("_id", id);

        OperationResult result;

        try {
            if (context.getFilePath() != null) {
                result = gridFsDAO
                        .upsertFile(getDatabase(),
                                context.getDBName(),
                                context.getCollectionName(),
                                metadata,
                                context.getFilePath(),
                                id,
                                context.getETag(),
                                context.isETagCheckRequired());
            } else {
                // throw new RuntimeException("error. file data is null");
                // try to pass to next handler in order to PUT new metadata on existing file.
                next(exchange, context);
                return;
            }
        } catch (MongoWriteException t) {
            if (((MongoException) t).getCode() == 11000) {

                // update not supported
                String errMsg = "file resource update is not yet implemented";
                LOGGER.error(errMsg, t);
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_NOT_IMPLEMENTED,
                        errMsg);
                next(exchange, context);
                return;
            }

            throw t;
        }

        context.setDbOperationResult(result);

        context.setResponseStatusCode(result.getHttpCode());

        next(exchange, context);
    }
}
