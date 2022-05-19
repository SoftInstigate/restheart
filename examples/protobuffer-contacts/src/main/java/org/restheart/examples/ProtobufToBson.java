package org.restheart.examples;

import com.googlecode.protobuf.format.JsonFormat;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.exchange.UninitializedRequest;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.PluginUtils;
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
        var req = (UninitializedRequest) request;

        // parse the protocol buffer request
        var helloReq = ContactPostRequest.parseFrom(req.getRawContent());
        // transform it to json
        var json = new JsonFormat().printToString(helloReq).getBytes();

        // overwrite the request body with the json
        req.setRawContent(json);
        // and set the content type to application/json
        req.setContentTypeAsJson();

        // the MongoService will initialize the request parsing the json

        PluginUtils.attachCustomRequestInitializer(request, e -> {
            LOGGER.debug("******* custom initializer!");
            // we remap the request to the collection restheart.coll
            // with a custom request initializer
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
}
