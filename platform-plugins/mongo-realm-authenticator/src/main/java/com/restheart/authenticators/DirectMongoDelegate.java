/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.restheart.authenticators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCursor;
import static com.mongodb.client.model.Filters.eq;
import java.util.LinkedHashSet;
import org.bson.BsonDocument;
import org.bson.Document;
import org.restheart.plugins.security.PwdCredentialAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DirectMongoDelegate implements MongoDelegate {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(DirectMongoDelegate.class);

    private final MongoClient mclient;
    private final String usersDb;
    private final String usersCollection;
    private final String propId;
    private final String propPassword;
    private final String jsonPathRoles;
    private final BsonDocument createUserDocument;

    DirectMongoDelegate(MongoClient mclient,
            String usersDb,
            String usersCollection,
            String propId,
            String propPassword,
            String jsonPathRoles,
            BsonDocument createUserDocument) {
        this.mclient = mclient;
        this.usersDb = usersDb;
        this.usersCollection = usersCollection;
        this.propId = propId;
        this.propPassword = propPassword;
        this.jsonPathRoles = jsonPathRoles;
        this.createUserDocument = createUserDocument;
    }

    @Override
    public boolean checkUserCollection() {
        var db = mclient.getDatabase(usersDb);

        MongoCursor<String> dbCollections = db
                .listCollectionNames()
                .iterator();

        while (dbCollections.hasNext()) {
            String dbCollection = dbCollections.next();

            if (usersCollection.equals(dbCollection)) {
                return true;
            }
        }

        try {
            db.createCollection(usersCollection);

            return true;
        } catch (Throwable t) {
            LOGGER.error("Error creating users collection", t);
            return false;
        }
    }

    @Override
    public long countAccounts() {
        try {
            return mclient.getDatabase(usersDb)
                    .getCollection(usersCollection)
                    .estimatedDocumentCount();
        } catch (Throwable t) {
            LOGGER.error("Error counting accounts", t);
            return 1;
        }
    }

    @Override
    public void createDefaultAccount() {
        if (this.createUserDocument != null) {
            try {
                mclient.getDatabase(usersDb)
                        .getCollection(usersCollection, BsonDocument.class)
                        .insertOne(this.createUserDocument);
            } catch (Throwable t) {
                LOGGER.error("Error creating default account", t);
            }
        }
    }

    @Override
    public PwdCredentialAccount findAccount(String accountId) {
        var coll = mclient.getDatabase(usersDb).getCollection(usersCollection);

        Document _account;

        try {
            _account = coll.find(eq(propId, accountId)).first();
        } catch (Throwable t) {
            LOGGER.error("Error finding account {}", propId, t);
            return null;
        }

        if (_account == null) {
            return null;
        }

        JsonElement account;

        try {
            account = JsonPath.read(_account.toJson(), "$");
        } catch (IllegalArgumentException | PathNotFoundException pnfe) {
            LOGGER.warn("Cannot find account {}", accountId);
            return null;
        }

        if (!account.isJsonObject()) {
            LOGGER.warn("Retrived document for account {} is not an object", accountId);
            return null;
        }

        JsonElement _password;

        try {
            _password = JsonPath.read(account, "$.".concat(this.propPassword));
        } catch (PathNotFoundException pnfe) {
            LOGGER.warn("Cannot find pwd property '{}' for account {}", this.propPassword,
                    accountId);
            return null;
        }

        if (!_password.isJsonPrimitive()
                || !_password.getAsJsonPrimitive().isString()) {
            LOGGER.warn("Pwd property of account {} is not a string", accountId);
            return null;
        }

        JsonElement _roles;

        try {
            _roles = JsonPath.read(account, this.jsonPathRoles);
        } catch (PathNotFoundException pnfe) {
            LOGGER.warn("Account with id: {} does not have roles");
            _roles = new JsonArray();
        }

        if (!_roles.isJsonArray()) {
            LOGGER.warn("Roles property of account {} is not an array", accountId);
            return null;
        }

        var roles = new LinkedHashSet();

        _roles.getAsJsonArray().forEach(role -> {
            if (role != null && role.isJsonPrimitive()
                    && role.getAsJsonPrimitive().isString()) {
                roles.add(role.getAsJsonPrimitive().getAsString());
            } else {
                LOGGER.warn("A role of account {} is not a string", accountId);
            }
        });

        return new PwdCredentialAccount(accountId,
                _password.getAsJsonPrimitive().getAsString().toCharArray(),
                roles);
    }

}
