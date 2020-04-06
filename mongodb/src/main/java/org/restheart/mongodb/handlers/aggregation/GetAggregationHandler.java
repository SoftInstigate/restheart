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
package org.restheart.mongodb.handlers.aggregation;

import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MapReduceIterable;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.handlers.metadata.InvalidMetadataException;
import org.restheart.representation.IllegalQueryParamenterException;
import org.restheart.representation.Resource;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetAggregationHandler extends PipelinedHandler {
    private final DatabaseImpl dbsDAO = new DatabaseImpl();

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
    public GetAggregationHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        
        if (request.isInError()) {
            next(exchange);
            return;
        }

        String queryUri = request.getAggregationOperation();

        List<AbstractAggregationOperation> aggregations
                = AbstractAggregationOperation
                        .getFromJson(request.getCollectionProps());

        Optional<AbstractAggregationOperation> _query
                = aggregations.stream().filter(q
                        -> q.getUri().equals(queryUri)).findFirst();

        if (!_query.isPresent()) {
            response.setIError(
                    HttpStatus.SC_NOT_FOUND, "query does not exist");
            next(exchange);
            return;
        }

        ArrayList<BsonDocument> data = new ArrayList<>();

        AbstractAggregationOperation query = _query.get();

        if (null == query.getType()) {
            response.setIError(
                    HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown query type");
            next(exchange);
            return;
        } else {
            var avars = request.getAggreationVars() == null
                    ? new BsonDocument()
                    : request.getAggreationVars();

            // add @page, @pagesize, @limit and @skip to avars to allow handling 
            // paging in the aggragation via default page and pagesize qparams
            avars.put("@page", new BsonInt32(request.getPage()));
            avars.put("@pagesize", new BsonInt32(request.getPagesize()));
            avars.put("@limit", new BsonInt32(request.getPagesize()));
            avars.put("@skip", new BsonInt32(request.getPagesize()
                    * (request.getPage() - 1)));

            switch (query.getType()) {
                case MAP_REDUCE:
                    MapReduceIterable<BsonDocument> mrOutput;
                    MapReduce mapReduce = (MapReduce) query;
                    try {
                        mrOutput = dbsDAO
                                .getCollection(request.getDBName(),
                                        request.getCollectionName())
                                .mapReduce(
                                        mapReduce.getResolvedMap(avars),
                                        mapReduce.getResolvedReduce(avars))
                                .filter(
                                        mapReduce.getResolvedQuery(avars))
                                .maxTime(MongoServiceConfiguration.get()
                                        .getAggregationTimeLimit(), 
                                        TimeUnit.MILLISECONDS);
                    } catch (MongoCommandException | InvalidMetadataException ex) {
                        response.setInError(
                                HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                "error executing mapReduce", ex);
                        next(exchange);
                        return;
                    } catch (QueryVariableNotBoundException qvnbe) {
                        response.setIError(
                                HttpStatus.SC_BAD_REQUEST,
                                "error executing mapReduce: "
                                + qvnbe.getMessage());
                        next(exchange);
                        return;
                    }
                    // ***** get data
                    for (BsonDocument obj : mrOutput) {
                        data.add(obj);
                    }
                    break;
                case AGGREGATION_PIPELINE:
                    AggregateIterable<BsonDocument> agrOutput;
                    AggregationPipeline pipeline = (AggregationPipeline) query;
                    try {
                        agrOutput = dbsDAO
                                .getCollection(
                                        request.getDBName(),
                                        request.getCollectionName())
                                .aggregate(
                                        pipeline.getResolvedStagesAsList(avars))
                                .maxTime(MongoServiceConfiguration.get()
                                        .getAggregationTimeLimit(),
                                        TimeUnit.MILLISECONDS)
                                .allowDiskUse(pipeline
                                        .getAllowDiskUse().getValue());
                    } catch (MongoCommandException
                            | InvalidMetadataException ex) {
                        response.setInError(
                                HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                "error executing aggreation pipeline", ex);
                        next(exchange);
                        return;
                    } catch (QueryVariableNotBoundException qvnbe) {
                        response.setIError(
                                HttpStatus.SC_BAD_REQUEST,
                                "error executing aggreation pipeline: "
                                + qvnbe.getMessage());
                        next(exchange);
                        return;
                    }
                    // ***** get data
                    for (BsonDocument obj : agrOutput) {
                        data.add(obj);
                    }
                    break;
                default:
                    response.setIError(
                            HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown query type");
                    next(exchange);
                    return;
            }
        }

        if (exchange.isComplete()) {
            // if an error occured getting data, the exchange is already closed
            return;
        }

        try {
            response.setContent(new AggregationResultRepresentationFactory()
                    .getRepresentation(
                            exchange,
                            data,
                            data.size())
                    .asBsonDocument());

            response.setContentType(Resource.HAL_JSON_MEDIA_TYPE);
            response.setStatusCode(HttpStatus.SC_OK);

            // call the ResponseTransformerMetadataHandler if piped in
            next(exchange);
        } catch (IllegalQueryParamenterException ex) {
            response.setInError(
                    HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
            next(exchange);
        }
    }
}
