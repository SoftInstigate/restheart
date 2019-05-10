package org.restheart.plugins.transformers;

import org.restheart.plugins.Transformer;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;
import org.restheart.plugins.RegisterPlugin;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "writeResult",
        description = "Adds a body to write responses with "
                + "updated and old version of the written document.")
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
            
            // this to deal with POST collection
            if (context.isCollection() && context.isPost()) {
                BsonDocument body = new BsonDocument();
                BsonDocument embedded = new BsonDocument();
                BsonArray rhdoc = new BsonArray();
                
                rhdoc.add(resp);
                embedded.put("rh:result", rhdoc);
                body.put("_embedded", embedded);
                context.setResponseContent(body);
            }
        }
    }
}
