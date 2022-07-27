package org.restheart.examples;

import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.exchange.UninitializedRequest;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.MongoServiceAttachments;

import io.github.gaplotech.PBBsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(
    name = "protobufToBson",
    description = "Transforms the protobuf request to BSON",
    interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
    requiresContent = true)
public class ProtobufToBson implements WildcardInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtobufToBson.class);

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {

        // the MongoService will initialize the request parsing the json
        // // overwrite the request body with the json
        // req.setRawContent(json);
        // // and set the content type to application/json
        // req.setContentTypeAsJson();

        // with InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT
        // request is instanceof UninitializedRequest
        var uninitializedRequest = (UninitializedRequest) request;

        uninitializedRequest.setCustomRequestInitializer(e -> {
            var req = (UninitializedRequest) request;

            try {
                // parse the protocol buffer request
                var helloReq = ContactPostRequest.parseFrom(req.getRawContent());

                // MongoRequest.init() will skip the parsing of the request content
                // and use the Bson attached to the exchange
                // with MongoServiceAttachments.attachBsonContent()
                MongoServiceAttachments.attachBsonContent(request.getExchange(), decode(helloReq));
            } catch(Throwable ex) {
                var r = MongoRequest.init(e, "/proto", "/restheart/contacts");
                r.setInError(true);
            }

            // we remap the request to the collection restheart.coll
            MongoRequest.init(e, "/proto", "/restheart/contacts");
        });
    }

    /**
     * @return true if the request is a POST to /proto with content-type=application/protobuf
     */
    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        // interceptors at REQUEST_BEFORE_EXCHANGE_INIT
        // receive UninitializedRequest as request argument
        return request instanceof UninitializedRequest
            && request.isPost()
            && "application/protobuf".equals(request.getContentType())
            && "/proto".equals(request.getPath());
    }

    private BsonDocument decode(ContactPostRequest req) {
        var doc = new BsonDocument();

        var bsonWriter = new PBBsonWriter(
            true,
            true,
            new BsonDocumentWriter(doc));

        bsonWriter.write(req);

        LOGGER.debug("msg: {}", BsonUtils.toJson(doc));

        return doc;
    }
}
