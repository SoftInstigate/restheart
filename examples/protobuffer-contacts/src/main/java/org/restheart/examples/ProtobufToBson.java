/*-
 * ========================LICENSE_START=================================
 * protobuffer-contacts
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.exchange.UninitializedRequest;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.MongoServiceAttachments;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

@RegisterPlugin(
    name = "protobufToBson",
    description = "Transforms the protobuf request to BSON",
    interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
    requiresContent = true)
public class ProtobufToBson implements WildcardInterceptor {
    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) {
        // with InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT
        // request is instanceof UninitializedRequest
        var uninitializedRequest = (UninitializedRequest) request;

        uninitializedRequest.setCustomRequestInitializer(e -> {
            try {
                // parse the ContactPostRequest
                var protobufReq = ContactPostRequest.parseFrom(uninitializedRequest.getRawContent());

                // with MongoServiceAttachments.attachBsonContent()
                // MongoRequest.init() skips the parsing of the request content
                // and use the Bson attached to the exchange
                MongoServiceAttachments.attachBsonContent(request.getExchange(), decode(protobufReq));
            } catch(Throwable ex) {
                // set request in error
                var r = MongoRequest.init(e, "/proto", "/restheart/contacts");
                r.setInError(true);
            }

            // map /proto to the collection restheart.coll
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

    /**
     * transform the message to BsonDocument
     *
     * uses the PBCodecProvider
     *
     * @param message
     * @return the message as BsonDocument
     * @throws InvalidProtocolBufferException
     */
    private BsonDocument decode(AbstractMessage message) throws InvalidProtocolBufferException {
        var json = JsonFormat.printer().print(message);
        return BsonUtils.parse(json).asDocument();
    }
}
