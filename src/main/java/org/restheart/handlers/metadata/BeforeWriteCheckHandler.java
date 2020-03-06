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
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.metadata.CheckerMetadata;
import org.restheart.plugins.Checker;
import org.restheart.plugins.Checker.PHASE;
import org.restheart.plugins.GlobalChecker;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.checkers.CheckersUtils;
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

    /**
     *
     */
    public static final String SINGLETON_GROUP_NAME = "checkers";

    /**
     *
     */
    public static final String ROOT_KEY = "checkers";

    /**
     * Creates a new instance of CheckMetBeforeWriteCheckHandleradataHandler
     *
     * handler that applies the checkers defined in the collection properties
     *
     * @param next
     */
    public BeforeWriteCheckHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange)
            throws Exception {
        if ((doesCheckersApply(exchange)
                && !applyCheckers(exchange))
                || (doesGlobalCheckersApply()
                && !applyGlobalCheckers(exchange))) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_BAD_REQUEST,
                    "request check failed");
        }

        next(exchange);
    }

    /**
     *
     * @param exchange
     * @return
     * @throws InvalidMetadataException
     */
    protected boolean applyCheckers(HttpServerExchange exchange)
            throws InvalidMetadataException {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        
        List<CheckerMetadata> requestCheckers = CheckerMetadata.getFromJson(
                request.getCollectionProps());

        return requestCheckers != null
                && requestCheckers.stream().allMatch(checkerMetadata -> {
                    try {
                        var checkerRecord = PluginsRegistry.getInstance().
                                getChecker(checkerMetadata.getName());
                        var checker = checkerRecord.getInstance();

                        BsonDocument confArgs = checkerRecord
                                .getConfArgsAsBsonDocument();

                        return applyChecker(exchange,
                                checkerMetadata.skipNotSupported(),
                                checker,
                                checkerMetadata.getArgs(),
                                confArgs);
                    } catch (NoSuchElementException ex) {
                        LOGGER.warn(ex.getMessage());
                        response.addWarning(ex.getMessage());
                        return false;
                    } catch (Throwable t) {
                        String err = "Error executing checker '"
                                + checkerMetadata.getName()
                                + "': "
                                + t.getMessage();
                        LOGGER.warn(err);
                        response.addWarning(err);
                        return false;
                    }
                });
    }

    private boolean applyChecker(HttpServerExchange exchange,
            boolean skipNotSupported,
            Checker checker,
            BsonValue args,
            BsonValue confArgs) {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        var context = RequestContext.wrap(exchange);
        
        // all checkers (both BEFORE_WRITE and AFTER_WRITE) are checked
        // to support the request; if any checker does not support the
        // request and it is configured to fail in this case,
        // then the request fails.
        if (!checker.doesSupportRequests(context) && !skipNotSupported) {
            LOGGER.debug("checker '{}' does not support this request. Check will fail", checker.getClass().getSimpleName());
            String noteMsg = "";

            if (CheckersUtils.doesRequestUsesDotNotation(
                    request.getContent())) {
                noteMsg = noteMsg.concat(
                        "uses the dot notation");
            }

            if (CheckersUtils.doesRequestUsesUpdateOperators(
                    request.getContent())) {
                noteMsg = noteMsg.isEmpty()
                        ? "uses update operators"
                        : noteMsg
                                .concat(" and update operators");
            }

            if (CheckersUtils.isBulkRequest(request)) {
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

            response.addWarning(warnMsg);
            return false;
        }

        if (doesCheckersApply(context, checker)
                && checker.doesSupportRequests(context)) {

            BsonValue _data;

            if (checker.getPhase(context)
                    == PHASE.BEFORE_WRITE) {
                _data = request.getContent();
            } else {
                Objects.requireNonNull(
                        response.getDbOperationResult());

                _data = response
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

    boolean applyGlobalCheckers(HttpServerExchange exchange) {
        var context = RequestContext.wrap(exchange);
        
        // execture global request tranformers
        return PluginsRegistry.getInstance().getGlobalCheckers().stream()
                .filter(gc -> doesGlobalCheckerApply(gc, exchange, context))
                .allMatch(gc
                        -> applyChecker(exchange,
                        gc.isSkipNotSupported(),
                        gc.getChecker(),
                        gc.getArgs(),
                        gc.getConfArgs())
                );
    }

    boolean doesCheckersApply(HttpServerExchange exchange) {
        var request = BsonRequest.wrap(exchange);
        
        return request.getCollectionProps() != null
                && request.getCollectionProps().containsKey(ROOT_KEY);
    }

    boolean doesGlobalCheckersApply() {
        return !PluginsRegistry.getInstance().getGlobalCheckers().isEmpty();
    }

    /**
     *
     * @param context
     * @param checker
     * @return
     */
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
