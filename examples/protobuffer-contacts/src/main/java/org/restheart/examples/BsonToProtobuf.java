/*-
 * ========================LICENSE_START=================================
 * protobuffer-contacts
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import org.bson.json.JsonMode;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.LambdaUtils;

@RegisterPlugin(
    name = "bsonToProtobuf",
    description = "Transforms the BSON response to protobuf",
    interceptPoint = InterceptPoint.RESPONSE,
    requiresContent = true)
public class BsonToProtobuf implements MongoInterceptor {
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        // get the created document id
        var id = BsonUtils.toJson(response.getDbOperationResult().getNewId(), JsonMode.RELAXED);

        // build the ContactPostReply
        var builder = ContactPostReply.newBuilder().setId(id);

        // the custom sender will send the ContactPostReply in place of request content (that is a BsonValue)
        response.setCustomSender(() -> {
            try {
                response.getExchange().getResponseSender().send(builder.build().toByteString().toStringUtf8());
            } catch(Throwable t) {
                LambdaUtils.throwsSneakyException(t);
            }
        });
    }

    /**
     * @return true if the request is a insert POST to /proto
     */
    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isPost()
            && "/proto".equals(request.getPath())
            && response.getDbOperationResult() != null
            && response.getDbOperationResult().getNewId() != null;
    }
}
