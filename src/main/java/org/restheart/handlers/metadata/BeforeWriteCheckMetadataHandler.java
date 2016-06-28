/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.Objects;
import org.bson.BsonArray;
import org.bson.BsonValue;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.RequestChecker;
import org.restheart.hal.metadata.singletons.Checker;
import org.restheart.hal.metadata.singletons.Checker.PHASE;
import org.restheart.hal.metadata.singletons.CheckersUtils;
import org.restheart.hal.metadata.singletons.NamedSingletonsFactory;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BeforeWriteCheckMetadataHandler extends PipedHttpHandler {
    static final Logger LOGGER
            = LoggerFactory.getLogger(BeforeWriteCheckMetadataHandler.class);

    public static final String SINGLETON_GROUP_NAME = "checkers";

    public static final String ROOT_KEY = "checkers";

    /**
     * Creates a new instance of CheckMetadataHandler
     *
     * handler that applies the checkers defined in the collection properties
     *
     * @param next
     */
    public BeforeWriteCheckMetadataHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (doesCheckerAppy(context)) {
            if (check(exchange, context)) {
                if (getNext() != null) {
                    getNext().handleRequest(exchange, context);
                }
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("schema check failed");

                List<String> warnings = context.getWarnings();

                if (warnings != null && !warnings.isEmpty()) {
                    warnings.stream().forEach(w -> {
                        sb.append(", ").append(w);
                    });
                }

                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        sb.toString());
            }
        } else if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
    }

    private boolean doesCheckerAppy(RequestContext context) {
        return context.getCollectionProps() != null
                && context.getCollectionProps().containsKey(ROOT_KEY);
    }

    protected boolean check(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        List<RequestChecker> checkers = RequestChecker
                .getFromJson(context.getCollectionProps());

        return checkers != null
                && checkers.stream().allMatch(checker -> {
                    try {
                        Checker _checker = (Checker) NamedSingletonsFactory
                                .getInstance()
                                .get(ROOT_KEY, checker.getName());

                        if (_checker == null) {
                            throw new IllegalArgumentException(
                                    "cannot find singleton "
                                    + checker.getName()
                                    + " in singleton group checkers");
                        }

                        // all checkers (both BEFORE_WRITE and AFTER_WRITE) are checked
                        // to support the request; if any checker does not support the
                        // request and it is configured to fail in this case,
                        // then the request fails.
                        if (!_checker.doesSupportRequests(context)
                                && !checker.skipNotSupported()) {
                            LOGGER.debug("checker "
                                    + _checker.getClass().getSimpleName()
                                    + " does not support this request. "
                                    + "check will "
                                    + (checker.skipNotSupported()
                                            ? "not fail"
                                            : "fail"));

                            String noteMsg = "";

                            if (CheckersUtils.doesRequestUsesDotNotation(
                                    context.getContent())) {
                                noteMsg = noteMsg.concat(
                                        "uses the dot notation");
                            }

                            if (CheckersUtils.doesRequestUsesUpdateOperators(
                                    context.getContent())) {
                                noteMsg = noteMsg.isEmpty()
                                        ? "uses update operators"
                                        : noteMsg
                                        .concat(" and update operators");
                            }

                            if (CheckersUtils.isBulkRequest(context)) {
                                noteMsg = noteMsg.isEmpty()
                                        ? "is a bulk operation"
                                        : noteMsg
                                        .concat(" and it is a "
                                                + "bulk operation");
                            }

                            context.addWarning("the checker "
                                    + _checker.getClass().getSimpleName()
                                    + " does not support this request and "
                                    + "is configured to fail in this case. "
                                    + "Note that the request "
                                    + noteMsg);
                            return false;
                        }

                        if (doesCheckerApply(context, _checker)
                                && _checker.doesSupportRequests(context)) {

                            BsonValue content = context.getContent();

                            BsonValue _data;

                            if (_checker.getPhase(context)
                                    == PHASE.BEFORE_WRITE) {
                                _data = context.getContent();
                            } else {
                                Objects.requireNonNull(
                                        context.getDbOperationResult());

                                _data = context
                                        .getDbOperationResult()
                                        .getNewData();
                            }

                            if (_data.isDocument()) {
                                return _checker.check(
                                        exchange,
                                        context,
                                        _data.asDocument(),
                                        checker.getArgs());
                            } else if (content.isArray()) {
                                // content can be an array of bulk POST

                                BsonArray arrayContent = _data.asArray();

                                return arrayContent.stream().allMatch(obj -> {
                                    if (obj.isDocument()) {
                                        return _checker
                                                .check(
                                                        exchange,
                                                        context,
                                                        obj.asDocument(),
                                                        checker.getArgs());
                                    } else {
                                        LOGGER.warn(
                                                "element of content array "
                                                + "is not an object");
                                        return true;
                                    }
                                });

                            } else {
                                LOGGER.warn(
                                        "content is not an object or an array");
                                return true;
                            }
                        } else {
                            return true;
                        }

                    } catch (IllegalArgumentException ex) {
                        context.addWarning("error applying checker: "
                                + ex.getMessage());
                        return false;
                    }
                });
    }

    protected boolean doesCheckerApply(
            RequestContext context,
            Checker checker) {
        return checker.getPhase(context) == Checker.PHASE.BEFORE_WRITE;
    }
}
