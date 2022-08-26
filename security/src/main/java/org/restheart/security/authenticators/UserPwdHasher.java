/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2022 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.authenticators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.bson.BsonString;
import org.mindrot.jbcrypt.BCrypt;
import org.restheart.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "userPwdHasher",
        description = "automatically hashes the user password",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
        requiresContent = true)
public class UserPwdHasher implements MongoInterceptor {

    static final Logger LOGGER = LoggerFactory.getLogger(UserPwdHasher.class);

    private String usersDb;
    private String usersCollection;
    private String propNamePassword;
    private Integer complexity;

    private boolean enabled = false;

    @Inject("registry")
    private PluginsRegistry registry;

    @OnInit
    public void init() {
        PluginRecord<Authenticator> _mra;

        try {
            _mra = registry.getAuthenticator("mongoRealmAuthenticator");
        } catch (ConfigurationException ce) {
            enabled = false;
            return;
        }

        if (_mra == null || !_mra.isEnabled()) {
            enabled = false;
        } else {
            var rhAuth = (MongoRealmAuthenticator) _mra.getInstance();

            if (!rhAuth.isBcryptHashedPassword()) {
                this.enabled = false;
                return;
            }

            this.usersDb = rhAuth.getUsersDb();
            this.usersCollection = rhAuth.getUsersCollection();
            this.propNamePassword = rhAuth.getPropPassword();
            this.complexity = rhAuth.getBcryptComplexity();

            if (usersDb == null
                    || usersCollection == null
                    || propNamePassword == null
                    || complexity == null) {
                LOGGER.error("Wrong configuration of mongoRealmAuthenticator! "
                        + "Password field of users documents "
                        + "is not automatically entcrypted: "
                        + "{usersDb: {}, usersCollection: {}, "
                        + "propNamePassword: {}, complexity: {}})",
                        usersDb, usersCollection, propNamePassword, complexity);
                enabled = false;
            } else {
                enabled = true;
            }
        }
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var content = request.getContent();

        if (content == null) {
            return;
        } else if (content.isArray() && request.isPost()) {
            // POST collection with array of documents
            JsonArray passwords = JsonPath.read(BsonUtils.toJson(content), "$.[*].".concat(this.propNamePassword));

            int[] iarr = {0};

            passwords.forEach(plain -> {
                if (plain != null && plain.isJsonPrimitive() && plain.getAsJsonPrimitive().isString()) {
                    var hashed = BCrypt.hashpw(plain.getAsJsonPrimitive().getAsString(), BCrypt.gensalt(complexity));

                    content.asArray().get(iarr[0]).asDocument().put(this.propNamePassword, new BsonString(hashed));
                }

                iarr[0]++;
            });
        } else if (content.isDocument()) {
            // PUT/PATCH document or bulk PATCH
            JsonElement plain;
            try {
                plain = JsonPath.read(BsonUtils.toJson(content), "$.".concat(this.propNamePassword));

                if (plain != null && plain.isJsonPrimitive() && plain.getAsJsonPrimitive().isString()) {
                    String hashed = BCrypt.hashpw(plain.getAsJsonPrimitive().getAsString(), BCrypt.gensalt(complexity));

                    content.asDocument().put(this.propNamePassword, new BsonString(hashed));
                }
            } catch (PathNotFoundException pnfe) {
                return;
            }
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return enabled
                && request.isHandledBy("mongo")
                && request.isWriteDocument()
                && request.isContentTypeJson()
                && this.usersDb.equalsIgnoreCase(request.getDBName())
                && this.usersCollection.equalsIgnoreCase(request.getCollectionName());
    }
}
