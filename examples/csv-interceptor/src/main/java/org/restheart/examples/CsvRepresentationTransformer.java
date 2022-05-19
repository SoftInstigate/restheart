package org.restheart.examples;

import java.util.stream.Collectors;

import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BsonUtils;

@RegisterPlugin(name = "csvRepresentationTransformer",
    interceptPoint = InterceptPoint.RESPONSE,
    description = "transform the response to CSV format when the qparam ?csv is specified")
public class CsvRepresentationTransformer implements MongoInterceptor {
    @Override
    public void handle(MongoRequest request, MongoResponse response) {
        var docs = response.getContent().asArray();
        var sb = new StringBuilder();

        // add the header
        if (docs.size() > 0) {
            sb.append(docs.get(0).asDocument().keySet().stream().collect(Collectors.joining(","))).append("\n");
        }

        // add rows
        docs.stream()
            .map(BsonValue::asDocument)
            .forEach(fdoc -> sb.append(
                fdoc.entrySet().stream()
                    .map(e -> e.getValue())
                    .map(v -> BsonUtils.toJson(v))
                    .collect(Collectors.joining(",")))
                .append("\n"));

        response.setContentType("text/csv");

        // setCustomerSender() method name has a typo, should be setCustomSender()
        // will be fixed in v6.2.2
        response.setCustomSender(() -> response.getExchange().getResponseSender().send(sb.toString()));
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isGet()
            && request.isCollection()
            && response.getContent() != null
            && request.getQueryParameterOfDefault("csv", null) != null;
    }
}