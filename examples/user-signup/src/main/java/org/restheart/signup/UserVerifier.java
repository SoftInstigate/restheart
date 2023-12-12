/*-
 * ========================LICENSE_START=================================
 * user-signup
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

package org.restheart.signup;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

/**
 * Verifies a registered user
 *
 * It assumes that the user collection is 'restheart.users', the roles property
 * is 'roles' and the id of the user is "_id" (the default configuration values
 * of mongoRealAuthenticator)
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "userVerifier",
        description = "verifies user",
        defaultURI = "/verify")
public class UserVerifier implements JsonService {
    @Inject("mclient")
    private MongoClient mclient;

    private final String userDb = "restheart";
    private final String usersCollection = "users";
    private final String propId = "_id";
    private final String rolesProperty = "roles";

    @Override
    public void handle(JsonRequest request, JsonResponse response)
            throws Exception {
        if (request.isOptions()) {
            handleOptions(request);
        } else if (request.isGet() && checkRequest(request)) {
            var qparams = request.getQueryParameters();

            if (!qparams.containsKey("username")
                    || qparams.get("username").isEmpty()
                    || !qparams.containsKey("code")
                    || qparams.get("code").isEmpty()) {
                var resp = new JsonObject();
                resp.addProperty("status", "error, missing verification code");
                response.setContent(resp);
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            } else {
                var username = qparams.get("username").getFirst();
                var code = qparams.get("code").getFirst();

                if (verify(username, code)) {
                    unlock(username);
                    var resp = new JsonObject();
                    resp.addProperty("status", "verified");
                    response.setContent(resp);
                    response.setStatusCode(HttpStatus.SC_OK);
                } else {
                    var resp = new JsonObject();
                    resp.addProperty("status", "error, wrong verification code");
                    response.setContent(resp);
                    response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                }
            }
        } else {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        }
    }

    private boolean checkRequest(JsonRequest request) {
        var qparams = request.getQueryParameters();

        return qparams.containsKey("username")
                && !qparams.get("username").isEmpty()
                && qparams.containsKey("code")
                && !qparams.get("code").isEmpty();
    }

    /**
     * Checks the code of registered user
     *
     * @param username
     * @param code
     */
    private boolean verify(String username, String code) {
        var coll = this.mclient.getDatabase(userDb)
                .getCollection(usersCollection, BsonDocument.class);

        var query = and(eq(propId, new BsonString(username)),
                eq("code", new BsonString(code)),
                eq("roles", new BsonString("UNVERIFIED")));

        return coll.find(query).first() != null;
    }

    /**
     * Sets roles = [ "USER" ]
     *
     * @param username
     */
    private void unlock(String username) {
        var coll = this.mclient.getDatabase(userDb)
                .getCollection(usersCollection, BsonDocument.class);

        final BsonArray UNLOCKED_ROLES = new BsonArray();
        UNLOCKED_ROLES.add(new BsonString("USER"));

        coll.findOneAndUpdate(eq(propId, new BsonString(username)),
                set(rolesProperty, UNLOCKED_ROLES));
    }
}
