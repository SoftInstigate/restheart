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
package org.restheart.handlers.aggregation;

import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MapReduceIterable;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.bson.BsonDocument;
import org.restheart.hal.Representation;
import org.restheart.hal.metadata.AbstractAggregationOperation;
import org.restheart.hal.metadata.AggregationPipeline;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.MapReduce;
import org.restheart.hal.metadata.QueryVariableNotBoundException;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetAggregationHandler extends PipedHttpHandler {

    /**
     * Default ctor
     */
    public GetAggregationHandler() {
        super();
    }

    /**
     * Default ctor
     *
     * @param next
     */
    public GetAggregationHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange,
            RequestContext context) throws Exception {
        String queryUri = context.getAggregationOperation();

        List<AbstractAggregationOperation> aggregations
                = AbstractAggregationOperation
                .getFromJson(context.getCollectionProps());

        Optional<AbstractAggregationOperation> _query
                = aggregations.stream().filter(q
                        -> q.getUri().equals(queryUri)).findFirst();

        if (!_query.isPresent()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_FOUND, "query does not exist");
            return;
        }

        ArrayList<BsonDocument> data = new ArrayList<>();
        int size;

        AbstractAggregationOperation query = _query.get();

        if (query.getType() == AbstractAggregationOperation.TYPE.MAP_REDUCE) {
            MapReduceIterable<BsonDocument> mrOutput;

            MapReduce mapReduce = (MapReduce) query;
            try {
                mrOutput = getDatabase()
                        .getCollection(context.getDBName(),
                                context.getCollectionName())
                        .mapReduce(
                                mapReduce.getResolvedMap(context.getAggreationVars()),
                                mapReduce.getResolvedReduce(context.getAggreationVars()))
                        .filter(
                                mapReduce.getResolvedQuery(context.getAggreationVars()));
            } catch (MongoCommandException | InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "error executing mapReduce", ex);
                return;
            } catch (QueryVariableNotBoundException qvnbe) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "error executing mapReduce: "
                        + qvnbe.getMessage());
                return;
            }

            if (mrOutput == null) {
                ResponseHelper.endExchange(exchange, HttpStatus.SC_NO_CONTENT);
                return;
            }

            // ***** get data
            for (BsonDocument obj : mrOutput) {
                data.add(obj);
            }

            size = data.size();
        } else if (query.getType()
                == AbstractAggregationOperation.TYPE.AGGREGATION_PIPELINE) {
            AggregateIterable<BsonDocument> agrOutput;

            AggregationPipeline pipeline = (AggregationPipeline) query;

            try {
                agrOutput = getDatabase()
                        .getCollection(
                                context.getDBName(),
                                context.getCollectionName())
                        .aggregate(
                                pipeline
                                .getResolvedStagesAsList(
                                        context.getAggreationVars()));
            } catch (MongoCommandException | InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "error executing aggreation pipeline", ex);
                return;
            } catch (QueryVariableNotBoundException qvnbe) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "error executing aggreation pipeline: "
                        + qvnbe.getMessage());
                return;
            }

            if (agrOutput == null) {
                ResponseHelper.endExchange(exchange, HttpStatus.SC_NO_CONTENT);
                return;
            }

            // ***** get data
            for (BsonDocument obj : agrOutput) {
                data.add(obj);
            }

            size = data.size();
        } else {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown query type");
            return;
        }

        if (exchange.isComplete()) {
            // if an error occured getting data, the exchange is already closed
            return;
        }

        try {
            context.setResponseContent(new AggregationResultRepresentationFactory()
                    .getRepresentation(
                            exchange,
                            context,
                            applyPagination(data, context),
                            size)
                    .asBsonDocument());

            context.setResponseContentType(Representation.HAL_JSON_MEDIA_TYPE);
            context.setResponseStatusCode(HttpStatus.SC_OK);

            // call the ResponseTransformerMetadataHandler if piped in
            if (getNext() != null) {
                getNext().handleRequest(exchange, context);
            }
        } catch (IllegalQueryParamenterException ex) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private List<BsonDocument> applyPagination(
            List<BsonDocument> data,
            RequestContext context) {
        if (data == null) {
            return data;
        }

        int start = context.getPagesize() * (context.getPage() - 1);
        int end = start + context.getPagesize();

        if (data.size() < start) {
            return Collections.emptyList();
        } else {
            return data.subList(start, end > data.size() ? data.size() : end);
        }
    }
}
