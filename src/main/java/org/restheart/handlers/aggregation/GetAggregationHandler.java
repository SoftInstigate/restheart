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

import com.mongodb.AggregationOutput;
import com.mongodb.DBObject;
import com.mongodb.MapReduceCommand.OutputType;
import com.mongodb.MapReduceOutput;
import com.mongodb.MongoCommandException;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
            ResponseHelper.endExchangeWithMessage(exchange,
                    HttpStatus.SC_NOT_FOUND, "query does not exist");
            return;
        }

        ArrayList<DBObject> data = new ArrayList();
        int size;

        AbstractAggregationOperation query = _query.get();

        if (query.getType() == AbstractAggregationOperation.TYPE.MAP_REDUCE) {
            MapReduceOutput mrOutput;

            MapReduce mapReduce = (MapReduce) query;

            try {
                mrOutput = getDatabase()
                        .getCollection(context.getDBName(),
                                context.getCollectionName())
                        .mapReduce(mapReduce.getResolvedMap(context.getAggreationVars()),
                                mapReduce.getResolvedReduce(context.getAggreationVars()),
                                null,
                                OutputType.INLINE,
                                mapReduce.getResolvedQuery(context.getAggreationVars()));
            } catch (MongoCommandException | InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(exchange,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "error executing mapReduce", ex);
                return;
            } catch (QueryVariableNotBoundException qvnbe) {
                ResponseHelper.endExchangeWithMessage(exchange,
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
            for (DBObject obj : mrOutput.results()) {
                data.add(obj);
            }

            size = mrOutput.getOutputCount();
        } else if (query.getType()
                == AbstractAggregationOperation.TYPE.AGGREGATION_PIPELINE) {
            AggregationOutput agrOutput;

            try {
                agrOutput = getDatabase()
                        .getCollection(context.getDBName(),
                                context.getCollectionName())
                        .aggregate(((AggregationPipeline) query)
                                .getResolvedStagesAsList(context.getAggreationVars()));
            } catch (MongoCommandException | InvalidMetadataException ex) {
                ResponseHelper.endExchangeWithMessage(exchange,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "error executing aggreation pipeline", ex);
                return;
            } catch (QueryVariableNotBoundException qvnbe) {
                ResponseHelper.endExchangeWithMessage(exchange,
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
            for (DBObject obj : agrOutput.results()) {
                data.add(obj);
            }

            size = data.size();

        } else {
            ResponseHelper.endExchangeWithMessage(exchange,
                    HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown query type");
            return;
        }

        if (exchange.isComplete()) {
            // if an error occured getting data, the exchange is already closed
            return;
        }

        try {
            AggregationResultRepresentationFactory crp
                    = new AggregationResultRepresentationFactory();

            // create representation applying pagination
            Representation rep = crp.getRepresentation(exchange,
                    context, applyPagination(data, context), size);

            exchange.setStatusCode(HttpStatus.SC_OK);

            // call the ResponseTranformerMetadataHandler if piped in
            if (getNext() != null) {
                DBObject responseContent = rep.asDBObject();
                context.setResponseContent(responseContent);

                getNext().handleRequest(exchange, context);
            }

            crp.sendRepresentation(exchange, context, rep);
            exchange.endExchange();
        } catch (IllegalQueryParamenterException ex) {
            ResponseHelper.endExchangeWithMessage(exchange,
                    HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private List<DBObject>
            applyPagination(List<DBObject> data, RequestContext context) {
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
