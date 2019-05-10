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
import java.util.NoSuchElementException;
import java.util.Objects;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.plugins.Checker;
import org.restheart.plugins.Checker.PHASE;
import org.restheart.plugins.checkers.CheckersUtils;
import org.restheart.plugins.GlobalChecker;
import org.restheart.metadata.CheckerMetadata;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BeforeWriteCheckHandler extends CheckHandler {

    static final Logger LOGGER
            = LoggerFactory.getLogger(BeforeWriteCheckHandler.class);

    public static final String SINGLETON_GROUP_NAME = "checkers";

    public static final String ROOT_KEY = "checkers";

    /**
     * Creates a new instance of CheckMetBeforeWriteCheckHandleradataHandler
     *
     * handler that applies the checkers defined in the collection properties
     *
     * @param next
     */
    public BeforeWriteCheckHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (doesCheckersApply(context) && !applyCheckers(exchange, context)) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    "request check failed");
        }

        if (doesGlobalCheckersApply()
                && !applyGlobalCheckers(exchange, context)) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    "request check failed");
        }

        next(exchange, context);
    }

    protected boolean applyCheckers(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        List<CheckerMetadata> requestCheckers = CheckerMetadata.getFromJson(
                context.getCollectionProps());

        return requestCheckers != null
                && requestCheckers.stream().allMatch(checkerMetadata -> {
                    try {
                        var checkerRecord = PluginsRegistry.getInstance().
                                getChecker(checkerMetadata.getName());
                        var checker = checkerRecord.getInstance();

                        BsonDocument confArgs = checkerRecord
                                .getConfArgsAsBsonDocument();

                        return applyChecker(exchange,
                                context,
                                checkerMetadata.skipNotSupported(),
                                checker,
                                checkerMetadata.getArgs(),
                                confArgs);
                    } catch (NoSuchElementException ex) {
                        LOGGER.warn(ex.getMessage());
                        context.addWarning(ex.getMessage());
                        return false;
                    } catch (Throwable t) {
                        String err = "Error executing checker '"
                                + checkerMetadata.getName()
                                + "': "
                                + t.getMessage();
                        LOGGER.warn(err);
                        context.addWarning(err);
                        return false;
                    }
                });
    }

    private boolean applyChecker(HttpServerExchange exchange,
            RequestContext context,
            boolean skipNotSupported,
            Checker checker,
            BsonValue args,
            BsonValue confArgs) {
        // all checkers (both BEFORE_WRITE and AFTER_WRITE) are checked
        // to support the request; if any checker does not support the
        // request and it is configured to fail in this case,
        // then the request fails.
        if (!checker.doesSupportRequests(context) && !skipNotSupported) {
            LOGGER.debug("checker '{}' does not support this request. Check will fail", checker.getClass().getSimpleName());
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

            String warnMsg = "the checker "
                    + checker.getClass().getSimpleName()
                    + " does not support this request and "
                    + "is configured to fail in this case.";

            if (!noteMsg.isEmpty()) {
                warnMsg = warnMsg.concat(" Note that the request "
                        + noteMsg);
            }

            context.addWarning(warnMsg);
            return false;
        }

        if (doesCheckersApply(context, checker)
                && checker.doesSupportRequests(context)) {

            BsonValue _data;

            if (checker.getPhase(context)
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
                return checker.check(
                        exchange,
                        context,
                        _data.asDocument(),
                        args,
                        confArgs);
            } else if (_data.isArray()) {
                // content can be an array of bulk POST

                BsonArray arrayContent = _data.asArray();

                return arrayContent.stream().allMatch(obj -> {
                    if (obj.isDocument()) {
                        return checker.check(
                                exchange,
                                context,
                                obj.asDocument(),
                                args,
                                confArgs);
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
    }

    boolean applyGlobalCheckers(HttpServerExchange exchange, RequestContext context) {
        // execture global request tranformers
        return getGlobalCheckers().stream()
                .filter(gc -> doesGlobalCheckerApply(gc, exchange, context))
                .allMatch(gc
                        -> applyChecker(exchange,
                        context,
                        gc.isSkipNotSupported(),
                        gc.getChecker(),
                        gc.getArgs(),
                        gc.getConfArgs())
                );
    }

    boolean doesCheckersApply(RequestContext context) {
        return context.getCollectionProps() != null
                && context.getCollectionProps().containsKey(ROOT_KEY);
    }

    boolean doesGlobalCheckersApply() {
        return !getGlobalCheckers().isEmpty();
    }

    protected boolean doesCheckersApply(
            RequestContext context,
            Checker checker) {
        return checker.getPhase(context) == Checker.PHASE.BEFORE_WRITE;
    }

    boolean doesGlobalCheckerApply(GlobalChecker gc,
            HttpServerExchange exchange,
            RequestContext context) {
        return gc.getPhase(context) == Checker.PHASE.BEFORE_WRITE
                && gc.resolve(exchange, context)
                && doesCheckersApply(context, gc.getChecker());
    }
}
