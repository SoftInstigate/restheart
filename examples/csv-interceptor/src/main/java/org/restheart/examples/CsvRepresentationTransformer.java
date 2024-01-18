/*-
 * ========================LICENSE_START=================================
 * csv-interceptor
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

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
    description = "transforms the response to CSV format when the qparam ?csv is specified")
public class CsvRepresentationTransformer implements MongoInterceptor {
    @Override
    public void handle(MongoRequest request, MongoResponse response) {
        var docs = response.getContent().asArray();
        var sb = new StringBuilder();

        // add the header
        if (!docs.isEmpty()) {
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
        response.setCustomSender(() -> response.getExchange().getResponseSender().send(sb.toString()));
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isGet()
            && request.isCollection()
            && response.getContent() != null
            && request.getQueryParameterOrDefault("csv", null) != null;
    }
}
