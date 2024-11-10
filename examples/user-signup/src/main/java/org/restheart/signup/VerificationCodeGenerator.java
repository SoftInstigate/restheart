/*-
 * ========================LICENSE_START=================================
 * user-signup
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

package org.restheart.signup;

import java.math.BigInteger;
import java.security.SecureRandom;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import com.mongodb.client.MongoClient;
import static com.mongodb.client.model.Filters.eq;

/**
 * whan an unauthenticated client creates a user user document, this interceptor
 * adds the verification code
 *
 * it also forbids the client to update an existing document
 *
 * it assumes that the user collection is 'users' and the roles property is
 * 'roles' (the default configuration values of mongoRealmAuthenticator)
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "verificationCodeGenerator", description = "adds the verification code whan an unauthenticated client creates a user user document", interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class VerificationCodeGenerator implements MongoInterceptor {

    private static final SecureRandom RND_GENERATOR = new SecureRandom();

    @Inject("mclient")
    private MongoClient mclient;

    @Override
    public void handle(MongoRequest request, MongoResponse response) {
        if (userExists(request)) {
            // unautheticated client cannot update documents
            response.setInError(HttpStatus.SC_FORBIDDEN, "not authorized");
        } else {
            // unautheticated client is creating a user, add verification code
            var user = request.getContent().asDocument();
            user.put("code", nextCode());
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        // applies to write request from unauthenticated clients
        return this.mclient != null && request.isWriteDocument()
                && "users".equalsIgnoreCase(request.getCollectionName()) && request.getContent() != null
                && request.getContent().isDocument() && (request.getAuthenticatedAccount() == null
                        || request.getAuthenticatedAccount().getRoles().contains("$unauthenticated"));
    }

    /**
     * Checks if the a user with _id already exists
     *
     * @param request
     * @return
     */
    private boolean userExists(MongoRequest request) {
        if (request.getContent().asDocument().containsKey("_id")) {
            var id = request.getContent().asDocument().get("_id");

            var existingUser = this.mclient
                .getDatabase(request.getDBName())
                .getCollection(request.getCollectionName(), BsonDocument.class)
                .find(eq("_id", id)).first();

            return existingUser != null;
        } else {
            return false;
        }
    }

    private BsonString nextCode() {
        return new BsonString(new BigInteger(256, RND_GENERATOR).toString(Character.MAX_RADIX));
    }
}
