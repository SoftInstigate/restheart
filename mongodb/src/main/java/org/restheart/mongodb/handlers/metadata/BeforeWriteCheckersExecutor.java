/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.exchange.RequestContext;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.metadata.CheckerMetadata;
import org.restheart.mongodb.plugins.checkers.CheckersUtils;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mongodb.Checker;
import org.restheart.plugins.mongodb.Checker.PHASE;
import org.restheart.plugins.mongodb.GlobalChecker;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PipelinedHandler that executes the before-write checkers.
 *
 * It implements Initializer only to be able get pluginsRegistry via
 * InjectPluginsRegistry annotation
 *
 * It is added to the pipeline by RequestDispatcherHandler
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "beforeWriteCheckerExecutor",
        description = "executes before-write checkers",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
@SuppressWarnings("deprecation")
public class BeforeWriteCheckersExecutor extends PipelinedHandler
        implements Initializer {

    public BeforeWriteCheckersExecutor() {
        super(null);
    }

    static final Logger LOGGER
            = LoggerFactory.getLogger(BeforeWriteCheckersExecutor.class);

    /**
     *
     */
    public static final String SINGLETON_GROUP_NAME = "checkers";

    /**
     *
     */
    public static final String ROOT_KEY = "checkers";

    private static PluginsRegistry pluginsRegistry;

    /**
     * Creates a new instance of CheckMetBeforeWriteCheckHandleradataHandler
     *
     * handler that applies the checkers defined in the collection properties
     *
     * @param pluginsRegistry
     */
    @InjectPluginsRegistry
    public void setPluginsRegistry(PluginsRegistry pluginsRegistry) {
        BeforeWriteCheckersExecutor.pluginsRegistry = pluginsRegistry;
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
            BsonResponse.wrap(exchange).setIError(
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
                        var _checkerRecord = pluginsRegistry
                                .getCheckers()
                                .stream()
                                .filter(t -> checkerMetadata
                                .getName().equals(t.getName()))
                                .findFirst();

                        if (_checkerRecord.isPresent()) {
                            var checkerRecord = _checkerRecord.get();

                            var checker = checkerRecord.getInstance();

                            BsonDocument confArgs = JsonUtils.toBsonDocument(
                                    checkerRecord.getConfArgs());

                            return applyChecker(exchange,
                                    checkerMetadata.skipNotSupported(),
                                    checker,
                                    checkerMetadata.getArgs(),
                                    confArgs);
                        } else {
                            LOGGER.warn("Checker set to apply "
                                    + "but not registered: {}",
                                    checkerMetadata.getName());
                            return false;
                        }
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
        return pluginsRegistry.getGlobalCheckers().stream()
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
        return !pluginsRegistry.getGlobalCheckers().isEmpty();
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

    /**
     * does nothing, implements Initializer only to get pluginsRegistry
     */
    @Override
    public void init() { 
    }
}
