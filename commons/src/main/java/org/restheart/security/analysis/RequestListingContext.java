/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
 * =========================LICENSE_END==================================
 */
package org.restheart.security.analysis;

import java.util.Optional;

import org.bson.BsonValue;
import org.restheart.exchange.Request;
import org.restheart.security.AclVarsInterpolator;
import org.restheart.security.EvaluationScope.Scope;
import org.restheart.security.analysis.PredicateExpression.Atom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.predicate.PredicateParser;

/**
 * A {@link ListingContext} over a real request and a candidate resource.
 *
 * <p>The request is the live one — the {@code POST /mcp} that is asking for a listing — and it is
 * read for the session only: the account, its roles, the variables a resolver declares
 * {@link Scope#LISTING}. Nothing of its path or its query is used, because the resource being
 * considered is not the one being requested. Everything else is left undetermined, which shows the
 * resource rather than hiding it.
 */
public class RequestListingContext implements ListingContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestListingContext.class);

    private final Request<?> request;
    private final String path;
    private final String method;

    /**
     * @param request the live request whose session is asking
     * @param path    the path of the resource being considered
     * @param method  the method of the action the listing is about
     */
    public RequestListingContext(Request<?> request, String path, String method) {
        this.request = request;
        this.path = path;
        this.method = method;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String method() {
        return method;
    }

    /**
     * Only the attributes of the session. A request header or a query parameter belongs to the call
     * that has not been made, and the ones about the path are the candidate's, which
     * {@link ListingEvaluator} reads for itself.
     */
    @Override
    public Optional<String> attribute(String token) {
        try {
            return switch (token) {
                case "%u", "%{REMOTE_USER}" -> Optional.ofNullable(request.getAuthenticatedAccount())
                        .map(a -> a.getPrincipal().getName());
                case "%a", "%{REMOTE_IP}" -> Optional.ofNullable(request.getExchange().getSourceAddress())
                        .map(address -> address.getAddress())
                        .map(address -> address.getHostAddress());
                default -> Optional.empty();
            };
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * A variable, resolved only when its resolver declares that its value does not depend on the
     * call. {@code @user.plan} does not, so a plan gate decides a listing exactly;
     * {@code @qparams['id']} does, so it stays undetermined.
     */
    @Override
    public Optional<String> variable(String token) {
        if (AclVarsInterpolator.scopeOfVar(token).orElse(Scope.CALL) != Scope.LISTING) {
            return Optional.empty();
        }

        return AclVarsInterpolator.resolveRegisteredVar(request, token).flatMap(RequestListingContext::asString);
    }

    @Override
    public Optional<Scope> scopeOf(String predicateName) {
        return PredicateScopes.of(predicateName);
    }

    /**
     * A predicate of a plugin that declared {@link Scope#LISTING}: it reads the session and nothing
     * of the candidate resource, so invoking it on the live request answers what it would answer
     * anywhere. Anything it throws leaves the atom undetermined.
     */
    @Override
    public Optional<Boolean> evaluate(Atom atom) {
        try {
            var predicate = PredicateParser.parse(atom.text(), getClass().getClassLoader());

            return Optional.of(predicate.resolve(request.getExchange()));
        } catch (Exception e) {
            LOGGER.debug("predicate {} declares LISTING but could not be evaluated for a catalog listing: {}",
                    atom.name(), e.getMessage());

            return Optional.empty();
        }
    }

    /** A resolved variable as the predicate language would compare it. */
    private static Optional<String> asString(BsonValue value) {
        if (value == null || value.isNull()) {
            return Optional.empty();
        }

        return switch (value.getBsonType()) {
            case STRING -> Optional.of(value.asString().getValue());
            case INT32 -> Optional.of(String.valueOf(value.asInt32().getValue()));
            case INT64 -> Optional.of(String.valueOf(value.asInt64().getValue()));
            case DOUBLE -> Optional.of(String.valueOf(value.asDouble().getValue()));
            case BOOLEAN -> Optional.of(String.valueOf(value.asBoolean().getValue()));
            case OBJECT_ID -> Optional.of(value.asObjectId().getValue().toHexString());
            default -> Optional.empty();
        };
    }
}
