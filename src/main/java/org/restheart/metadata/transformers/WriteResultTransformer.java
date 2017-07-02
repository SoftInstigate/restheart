package org.restheart.metadata.transformers;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class WriteResultTransformer implements Transformer {

    @Override
    public void transform(
            HttpServerExchange exchange,
            RequestContext context,
            BsonValue contentToTransform,
            BsonValue args) {
        if (context.getDbOperationResult() == null) {
        } else {
            BsonDocument resp = null;

            if (contentToTransform == null || !contentToTransform.isDocument()) {
                resp = new BsonDocument();
                context.setResponseContent(resp);
            } else if (contentToTransform.isDocument()) {
                resp = contentToTransform.asDocument();
            }

            if (resp != null) {
                resp.append("oldData", context.getDbOperationResult().getOldData()
                        == null
                                ? new BsonNull()
                                : context.getDbOperationResult().getOldData());

                resp.append("newData", context.getDbOperationResult().getNewData()
                        == null
                                ? new BsonNull()
                                : context.getDbOperationResult().getNewData());
            }
        }
    }
}
