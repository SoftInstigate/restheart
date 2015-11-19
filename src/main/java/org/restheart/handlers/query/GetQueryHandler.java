/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.handlers.query;

import com.mongodb.AggregationOutput;
import com.mongodb.DBObject;
import com.mongodb.MapReduceCommand.OutputType;
import com.mongodb.MapReduceOutput;
import com.mongodb.MongoCommandException;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.restheart.hal.Representation;
import org.restheart.hal.metadata.AbstractQuery;
import org.restheart.hal.metadata.AggregationPipelineQuery;
import org.restheart.hal.metadata.MapReduceQuery;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GetQueryHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetQueryHandler.class);

    /**
     * Default ctor
     */
    public GetQueryHandler() {
        super();
    }

    /**
     * Default ctor
     *
     * @param next
     */
    public GetQueryHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        String queryUri = context.getQuery();

        List<AbstractQuery> queries = AbstractQuery.getFromJson(context.getCollectionProps());

        Optional<AbstractQuery> _query = queries.stream().filter(q -> q.getUri().equals(queryUri)).findFirst();

        if (!_query.isPresent()) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "query does not exist");
            return;
        }

        ArrayList<DBObject> data = new ArrayList();
        int size = 0;

        AbstractQuery query = _query.get();

        if (query.getType() == AbstractQuery.TYPE.MAP_REDUCE) {
            MapReduceOutput mrOutput;

            MapReduceQuery mapReduce = (MapReduceQuery) query;

            try {
                mrOutput = getDatabase()
                        .getCollection(context.getDBName(), context.getCollectionName())
                        .mapReduce(mapReduce.getMap(), mapReduce.getReduce(), null, OutputType.INLINE, mapReduce.getUnescapedQuery());
            } catch (MongoCommandException ce) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "error executing mapReduce", ce);
                return;
            }
            
            if (mrOutput == null)  {
                ResponseHelper.endExchange(exchange, HttpStatus.SC_NO_CONTENT);
                return;
            }

            // ***** get data
            for (DBObject obj : mrOutput.results()) {
                data.add(obj);
            }
            
            size = mrOutput.getOutputCount();
        } else if (query.getType() == AbstractQuery.TYPE.AGGREGATION_PIPELINE) {
            AggregationOutput agrOutput;

            try {
                agrOutput = getDatabase()
                        .getCollection(context.getDBName(), context.getCollectionName())
                        .aggregate(((AggregationPipelineQuery) query)
                                .getStagesAsList());
            } catch (MongoCommandException ce) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "error executing aggreation pipeline", ce);
                return;
            }

            if (agrOutput == null)  {
                ResponseHelper.endExchange(exchange, HttpStatus.SC_NO_CONTENT);
                return;
            }

            // ***** get data
            for (DBObject obj : agrOutput.results()) {
                data.add(obj);
            }
            
            size = data.size();

        } else {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown query type");
            return;
        }

        if (exchange.isComplete()) {
            // if an error occured getting data, the exchange is already closed
            return;
        }

        try {
            QueryResultRepresentationFactory crp = new QueryResultRepresentationFactory();
            Representation rep = crp.getRepresentation(exchange, context, data, size);

            exchange.setResponseCode(HttpStatus.SC_OK);

            // call the ResponseTranformerMetadataHandler if piped in
            if (getNext() != null) {
                DBObject responseContent = rep.asDBObject();
                context.setResponseContent(responseContent);

                getNext().handleRequest(exchange, context);
            }

            crp.sendRepresentation(exchange, context, rep);
            exchange.endExchange();
        } catch (IllegalQueryParamenterException ex) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
