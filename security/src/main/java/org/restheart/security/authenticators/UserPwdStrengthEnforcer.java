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

import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import static org.restheart.plugins.InterceptPoint.REQUEST_AFTER_AUTH;

import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.restheart.plugins.Inject;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authenticator;
import org.restheart.utils.BsonUtils;
import static org.restheart.utils.BsonUtils.document;
import static org.restheart.utils.BsonUtils.array;
import org.restheart.utils.HttpStatus;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.nulabinc.zxcvbn.Feedback;
import com.nulabinc.zxcvbn.Zxcvbn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static Zxcvbn zxcvbn = new Zxcvbn();

    private String usersDb;
    private String usersCollection;
    private String propNamePassword;
    private Integer minimumPasswordStrength;

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

            if (!rhAuth.isEnforceMinimumPasswordStrenght()) {
                this.enabled = false;
                return;
            }

            this.usersDb = rhAuth.getUsersDb();
            this.usersCollection = rhAuth.getUsersCollection();
            this.propNamePassword = rhAuth.getPropPassword();
            this.minimumPasswordStrength = rhAuth.getMinimumPasswordStrength();

            if (usersDb == null
                    || usersCollection == null
                    || propNamePassword == null
                    || minimumPasswordStrength == null) {
                LOGGER.error("Wrong configuration of mongoRealmAuthenticator! "
                        + "Password field of users documents "
                        + "is not automatically checked for password strenght: "
                        + "{usersDb: {}, usersCollection: {}, "
                        + "propNamePassword: {}, minimumPasswordStrength: {}})",
                        usersDb, usersCollection, propNamePassword, minimumPasswordStrength);
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
                return;
            }
        }
    }

    private void reject(MongoResponse response, Feedback feedback) {
        reject(response, feedback, null);
    }

    private void reject(MongoResponse response, Feedback feedback, Integer idx) {
        var error = document()
            .put("message", idx == null ? "Password is too weak" : "Password is too weak in user document at index " + idx)
            .put("http status code", HttpStatus.SC_NOT_ACCEPTABLE)
            .put("http status description", HttpStatus.getStatusText(HttpStatus.SC_NOT_ACCEPTABLE));

        var warning = feedback.getWarning();

        if (warning != null && !warning.isEmpty()) {
            error.put("warning", warning);
        }

        var suggestions = feedback.getSuggestions();

        if (suggestions != null && !suggestions.isEmpty()) {
            var _suggestions = array();
            suggestions.stream().forEach(suggestion -> _suggestions.add(suggestion.toString()));
            error.put("suggestions", _suggestions);
        }

        response.setContent(error);
        response.setStatusCode(HttpStatus.SC_NOT_ACCEPTABLE);
        response.setInError(true);
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
