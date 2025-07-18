/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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

import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import static org.restheart.plugins.InterceptPoint.REQUEST_AFTER_AUTH;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.utils.BsonUtils;
import static org.restheart.utils.BsonUtils.array;
import static org.restheart.utils.BsonUtils.document;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.nulabinc.zxcvbn.Feedback;
import com.nulabinc.zxcvbn.Zxcvbn;

/**
 * helper interceptor to add token headers to Access-Control-Expose-Headers to
 * handle CORS request
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name="userPwdStrengthEnforcer",
        description = "enforce strong password for mongoRealmAuthenticator",
        interceptPoint = REQUEST_AFTER_AUTH,
        enabledByDefault = true,
        priority = Integer.MIN_VALUE)
public class UserPwdStrengthEnforcer implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPwdStrengthEnforcer.class);

    private static final Zxcvbn zxcvbn = new Zxcvbn();

    private MongoRealmAuthenticator mra;
    private String usersCollection;
    private String propNamePassword;
    private Integer minimumPasswordStrength;

    private boolean enabled = false;

    @Inject("registry")
    private PluginsRegistry registry;

    @OnInit
    public void init() {
        PluginRecord<Authenticator> pr;

        try {
            pr = registry.getAuthenticator("mongoRealmAuthenticator");
        } catch (ConfigurationException ce) {
            enabled = false;
            return;
        }

        if (pr == null || !pr.isEnabled()) {
            enabled = false;
        } else {
            this.mra = (MongoRealmAuthenticator) pr.getInstance();

            if (!mra.isEnforceMinimumPasswordStrength()) {
                this.enabled = false;
                return;
            }

            this.usersCollection = this.mra.getUsersCollection();
            this.propNamePassword = this.mra.getPropPassword();
            this.minimumPasswordStrength = this.mra.getMinimumPasswordStrength();

            if (usersCollection == null
                    || propNamePassword == null
                    || minimumPasswordStrength == null) {
                LOGGER.error("Wrong configuration of mongoRealmAuthenticator! "
                        + "Password field of users documents "
                        + "is not automatically checked for password strength: "
                        + "usersCollection: {}, "
                        + "propNamePassword: {}, minimumPasswordStrength: {}})",
                        usersCollection, propNamePassword, minimumPasswordStrength);
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
            // nothing to do
        } else if (content.isArray() && request.isPost()) {
            // POST collection with array of documents
            JsonArray passwords = JsonPath.read(BsonUtils.toJson(content), "$.[*].".concat(this.propNamePassword));

            int[] iarr = {0};

            StreamSupport.stream(Spliterators.spliteratorUnknownSize(passwords.iterator(), 0), false)
                .takeWhile(p -> !response.isInError())
                .forEach(plain -> {
                if (plain != null && plain.isJsonPrimitive() && plain.getAsJsonPrimitive().isString()) {
                    var password = plain.getAsJsonPrimitive().getAsString();
                    var measure = zxcvbn.measure(password);

                    if (measure.getScore()  < this.minimumPasswordStrength) {
                        reject(response, measure.getFeedback(), iarr[0]);
                    }
                }

                iarr[0]++;
            });
        } else if (content.isDocument()) {
            // PUT/PATCH document or bulk PATCH
            try {
                JsonElement plain = JsonPath.read(BsonUtils.toJson(content), "$.".concat(this.propNamePassword));

                if (plain != null && plain.isJsonPrimitive() && plain.getAsJsonPrimitive().isString()) {
                    var password = plain.getAsJsonPrimitive().getAsString();

                    var measure = zxcvbn.measure(password);

                    if (measure.getScore()  < this.minimumPasswordStrength) {
                        reject(response, measure.getFeedback());
                    }
                }
            } catch (PathNotFoundException pnfe) {
                // nothing to do
            }
        }
    }

    private void reject(MongoResponse response, Feedback feedback) {
        reject(response, feedback, null);
    }

    private void reject(MongoResponse response, Feedback feedback, Integer idx) {
        var error = document()
            .put("message", idx == null ? "Password is too weak" : "Password is too weak in user document at index " + idx)
            .put("http status code", HttpStatus.SC_BAD_REQUEST)
            .put("http status description", HttpStatus.getStatusText(HttpStatus.SC_BAD_REQUEST));

        var warning = feedback.getWarning();

        if (warning != null && !warning.isEmpty()) {
            error.put("warning", warning);
        }

        var suggestions = feedback.getSuggestions();

        if (suggestions != null && !suggestions.isEmpty()) {
            var _suggestions = array();
            suggestions.forEach(_suggestions::add);
            error.put("suggestions", _suggestions);
        }

        response.setContent(error);
        response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        response.setInError(true);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return enabled
            && request.isHandledBy("mongo")
            && request.isWriteDocument()
            && request.isContentTypeJson()
            && (request.attachedParam("override-users-db") != null || this.mra.getUsersDb(request).equalsIgnoreCase(request.getDBName())) // if usersdb is overridden then any users collection in any db must be processed
            && this.usersCollection.equalsIgnoreCase(request.getCollectionName());
    }
}
