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
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import static com.restheart.authenticators.MongoRealmAuthenticator.X_FORWARDED_ACCOUNT_ID;
import static com.restheart.authenticators.MongoRealmAuthenticator.X_FORWARDED_ROLE;
import static com.restheart.authenticators.MongoRealmAuthenticator.getXForwardedAccountIdHeaderName;
import static com.restheart.authenticators.MongoRealmAuthenticator.getXForwardedRolesHeaderName;
import com.restheart.net.Client;
import com.restheart.net.Request;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import org.restheart.plugins.security.PwdCredentialAccount;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ProxyMongoDelegate implements MongoDelegate {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(ProxyMongoDelegate.class);

    private final URI restheartBaseUrl;
    private final URI dbUrl;
    private final URI collUrl;
    private final String createUserDocument;
    private final String propId;
    private final String propPassword;
    private final String jsonPathRoles;

    ProxyMongoDelegate(final URI restheartBaseUrl,
            final URI dbUrl,
            final URI collUrl,
            final String createUserDocument,
            final String propId,
            final String propPassword,
            final String jsonPathRoles) {
        this.restheartBaseUrl = restheartBaseUrl;
        this.dbUrl = dbUrl;
        this.collUrl = collUrl;
        this.propId = propId;
        this.createUserDocument = createUserDocument;
        this.propPassword = propPassword;
        this.jsonPathRoles = jsonPathRoles;
    }

    @Override
    public boolean checkUserCollection() {
        var ajp = "ajp".equalsIgnoreCase(restheartBaseUrl.getScheme());

        try {
            var dbStatus = ajp
                    ? Client.getInstance().execute(new Request(
                            Request.METHOD.GET, dbUrl.resolve(dbUrl.getPath()
                                    .concat("/_size")))
                            .header(getXForwardedAccountIdHeaderName().toString(),
                                    X_FORWARDED_ACCOUNT_ID)
                            .header(getXForwardedRolesHeaderName().toString(),
                                    X_FORWARDED_ROLE)).getStatusCode()
                    : Unirest.get(dbUrl.resolve(dbUrl.getPath().concat("/_size"))
                            .toString())
                            .header(getXForwardedAccountIdHeaderName().toString(),
                                    X_FORWARDED_ACCOUNT_ID)
                            .header(getXForwardedRolesHeaderName().toString(),
                                    X_FORWARDED_ROLE)
                            .asString()
                            .getStatus();

            if (dbStatus == HttpStatus.SC_NOT_FOUND) {
                if (ajp) {
                    Client.getInstance().execute(new Request(
                            Request.METHOD.PUT, dbUrl)
                            .header(getXForwardedAccountIdHeaderName().toString(),
                                    X_FORWARDED_ACCOUNT_ID)
                            .header(getXForwardedRolesHeaderName().toString(),
                                    X_FORWARDED_ROLE));

                    LOGGER.info("Users db created");
                    Client.getInstance().execute(new Request(
                            Request.METHOD.PUT, collUrl)
                            .header(getXForwardedAccountIdHeaderName().toString(),
                                    X_FORWARDED_ACCOUNT_ID)
                            .header(getXForwardedRolesHeaderName().toString(),
                                    X_FORWARDED_ROLE));
                    LOGGER.info("Users collection created");
                } else {
                    Unirest.put(dbUrl.toString())
                            .header(getXForwardedAccountIdHeaderName().toString(),
                                    X_FORWARDED_ACCOUNT_ID)
                            .header(getXForwardedRolesHeaderName().toString(),
                                    X_FORWARDED_ROLE)
                            .asString();
                    LOGGER.info("Users db created");
                    Unirest.put(collUrl.toString())
                            .header(getXForwardedAccountIdHeaderName().toString(),
                                    X_FORWARDED_ACCOUNT_ID)
                            .header(getXForwardedRolesHeaderName().toString(),
                                    X_FORWARDED_ROLE)
                            .asString();
                    LOGGER.info("Users collection created");
                }

                return true;
            } else if (dbStatus == HttpStatus.SC_OK) {
                if (ajp) {
                    var collStatus = Client.getInstance().execute(new Request(
                            Request.METHOD.GET, collUrl
                                    .resolve(collUrl.getPath().concat("/_size")))
                            .header(getXForwardedAccountIdHeaderName().toString(),
                                    X_FORWARDED_ACCOUNT_ID)
                            .header(getXForwardedRolesHeaderName().toString(),
                                    X_FORWARDED_ROLE))
                            .getStatusCode();

                    if (collStatus == HttpStatus.SC_NOT_FOUND) {
                        Client.getInstance().execute(new Request(
                                Request.METHOD.PUT, collUrl)
                                .header(getXForwardedAccountIdHeaderName().toString(),
                                        X_FORWARDED_ACCOUNT_ID)
                                .header(getXForwardedRolesHeaderName().toString(),
                                        X_FORWARDED_ROLE));
                        LOGGER.info("Users collection created");
                    }
                } else {
                    var collStatus = Unirest.get(collUrl
                            .resolve(collUrl.getPath().concat("/_size"))
                            .toString())
                            .asString()
                            .getStatus();

                    if (collStatus == HttpStatus.SC_NOT_FOUND) {
                        Unirest.put(collUrl.toString())
                                .header(getXForwardedAccountIdHeaderName().toString(),
                                        X_FORWARDED_ACCOUNT_ID)
                                .header(getXForwardedRolesHeaderName().toString(),
                                        X_FORWARDED_ROLE)
                                .asString();
                        LOGGER.info("Users collection created");
                    }
                }

                return true;
            }

            return false;
        } catch (UnirestException | IOException ure) {
            return false;
        }
    }

    @Override
    public long countAccounts() {
        var ajp = "ajp".equalsIgnoreCase(restheartBaseUrl.getScheme());

        JsonElement body;

        try {
            if (ajp) {
                var resp = Client.getInstance().execute(new Request(
                        Request.METHOD.GET, collUrl
                                .resolve(collUrl.getPath().concat("/_size")))
                        .header(getXForwardedAccountIdHeaderName().toString(),
                                X_FORWARDED_ACCOUNT_ID)
                        .header(getXForwardedRolesHeaderName().toString(),
                                X_FORWARDED_ROLE));

                if (resp.getStatusCode() == HttpStatus.SC_OK) {
                    body = resp.getBodyAsJson();
                } else {
                    LOGGER.error("Error counting accounts; "
                            + "response status code {}", resp.getStatus());
                    return 1;
                }
            } else {
                var resp = Unirest
                        .get(collUrl.resolve(collUrl.getPath().concat("/_size"))
                                .toString())
                        .header(getXForwardedAccountIdHeaderName().toString(),
                                X_FORWARDED_ACCOUNT_ID)
                        .header(getXForwardedRolesHeaderName().toString(),
                                X_FORWARDED_ROLE)
                        .asString();

                if (resp.getStatus() == HttpStatus.SC_OK) {
                    body = JsonParser.parseString(resp.getBody());
                } else {
                    LOGGER.error("Error counting accounts; "
                            + "response status code {}", resp.getStatus());
                    return 1;
                }
            }

            if (body.isJsonObject()
                    && body.getAsJsonObject().keySet().contains("_size")
                    && body.getAsJsonObject().get("_size").isJsonPrimitive()
                    && body.getAsJsonObject().get("_size").getAsJsonPrimitive().isNumber()) {

                return body.getAsJsonObject().get("_size")
                        .getAsJsonPrimitive()
                        .getAsInt();
            } else {
                LOGGER.error("No _size property in the response "
                        + "of count accounts");
                return 1;
            }

        } catch (UnirestException | IOException | JsonParseException ex) {
            LOGGER.error("Error counting account", ex);
            return 1;
        }
    }

    @Override
    public void createDefaultAccount() {
        var ajp = "ajp".equalsIgnoreCase(restheartBaseUrl.getScheme());

        try {
            if (ajp) {
                var resp = Client.getInstance().execute(new Request(
                        Request.METHOD.POST, collUrl)
                        .header("content-type", "application/json")
                        .header(getXForwardedAccountIdHeaderName().toString(),
                                X_FORWARDED_ACCOUNT_ID)
                        .header(getXForwardedRolesHeaderName().toString(),
                                X_FORWARDED_ROLE)
                        .body(this.createUserDocument));

                if (resp.getStatusCode() == HttpStatus.SC_CREATED) {
                    return;
                } else {
                    LOGGER.error("Error creating default account; "
                            + "response status code {}", resp.getStatus());
                }
            } else {
                var resp = Unirest.post(collUrl.toString())
                        .header("content-type", "application/json")
                        .header(getXForwardedAccountIdHeaderName().toString(),
                                X_FORWARDED_ACCOUNT_ID)
                        .header(getXForwardedRolesHeaderName().toString(),
                                X_FORWARDED_ROLE)
                        .body(this.createUserDocument)
                        .asString();

                if (resp.getStatus() == HttpStatus.SC_CREATED) {
                    return;
                } else {
                    LOGGER.error("Error creating default account; "
                            + "response status code {}", resp.getStatus());
                }
            }
        } catch (UnirestException | IOException ex) {
            LOGGER.error("Error creating default account", ex);
            return;
        }
    }

    @Override
    public PwdCredentialAccount findAccount(String accountId) {
        var ajp = "ajp".equalsIgnoreCase(restheartBaseUrl.getScheme());

        String accounts;

        try {
            if (ajp) {
                var resp = Client.getInstance().execute(new Request(
                        Request.METHOD.GET, collUrl)
                        .header(getXForwardedAccountIdHeaderName().toString(),
                                X_FORWARDED_ACCOUNT_ID)
                        .header(getXForwardedRolesHeaderName().toString(),
                                X_FORWARDED_ROLE)
                        .parameter("np", "true")
                        .parameter("filter", "{\""
                                .concat(this.propId)
                                .concat("\":\"")
                                .concat(accountId)
                                .concat("\"}"))
                        .parameter("pagesize", "" + 1)
                        .parameter("rep", "STANDARD"));

                if (resp.getStatusCode() != HttpStatus.SC_OK) {
                    LOGGER.warn("Wrong response finding the account with id: {}. "
                            + "Response status: {}",
                            accountId,
                            resp.getStatus());
                    return null;
                } else {
                    accounts = resp.getBody();
                }
            } else {
                var resp = Unirest.get(collUrl.toString())
                        .header(getXForwardedAccountIdHeaderName().toString(),
                                X_FORWARDED_ACCOUNT_ID)
                        .header(getXForwardedRolesHeaderName().toString(),
                                X_FORWARDED_ROLE)
                        .queryString("np", true)
                        .queryString("filter", "{\""
                                .concat(this.propId)
                                .concat("\":\"")
                                .concat(accountId)
                                .concat("\"}"))
                        .queryString("pagesize", 1)
                        .queryString("rep", "STANDARD")
                        .asString();

                if (resp.getStatus() != HttpStatus.SC_OK) {
                    LOGGER.warn("Wrong response finding the account with id: {}. "
                            + "Response status: {}",
                            accountId,
                            resp.getStatus());
                    return null;
                } else {
                    accounts = resp.getBody();
                }
            }
        } catch (UnirestException | IOException ex) {
            LOGGER.warn("Error requesting {}: {}", collUrl, ex.getMessage());
            return null;
        }

        JsonElement account;

        try {
            account = JsonPath.read(accounts, "$.[0]");
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
