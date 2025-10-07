/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.restheart.exchange.IllegalQueryParameterException;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.mongodb.db.Databases;
import org.restheart.security.AggregationPipelineSecurityChecker;
import org.restheart.mongodb.utils.StagesInterpolator;
import org.restheart.mongodb.utils.StagesInterpolator.STAGE_OPERATOR;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MapReduceIterable;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@SuppressWarnings("deprecation")
public class GetAggregationHandler extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetAggregationHandler.class);

    private final Databases dbs = Databases.get();
    private final AggregationPipelineSecurityChecker securityChecker;

    /**
     * Default ctor
     */
    public GetAggregationHandler() {
        super();
        var config = MongoServiceConfiguration.get().getAggregationSecurityConfiguration();
        this.securityChecker = new AggregationPipelineSecurityChecker(config);
    }

    /**
     * Default ctor
     *
     * @param next
     */
    public GetAggregationHandler(PipelinedHandler next) {
        super(next);
        var config = MongoServiceConfiguration.get().getAggregationSecurityConfiguration();
        this.securityChecker = new AggregationPipelineSecurityChecker(config);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        var queryUri = request.getAggregationOperation();

		List<AbstractAggregationOperation> aggregations;

		try {
		  aggregations = AbstractAggregationOperation.getFromJson(request.getCollectionProps());
		} catch(InvalidMetadataException ime) {
		  response.setInError(HttpStatus.SC_UNPROCESSABLE_ENTITY, "unknown query type", ime);
		  next(exchange);
		  return;
		}

        var _query = aggregations.stream().filter(q -> q.getUri().equals(queryUri)).findFirst();

        if (_query.isEmpty()) {
            response.setInError(HttpStatus.SC_NOT_FOUND, "query does not exist");
            next(exchange);
            return;
        }

        var _data = new ArrayList<BsonDocument>();
        var query = _query.get();

        if (null == query.getType()) {
            response.setInError(HttpStatus.SC_UNPROCESSABLE_ENTITY, "unknown query type");
            next(exchange);
            return;
        } else {
            var avars = request.getAggregationVars() == null
                ? new BsonDocument()
                : request.getAggregationVars();

            // add the default variables to the avars document
            StagesInterpolator.injectAvars(request, avars);

		  if (Objects.requireNonNull(query.getType()) == AbstractAggregationOperation.TYPE.AGGREGATION_PIPELINE) {
			AggregateIterable<BsonDocument> agrOutput;
			var pipeline = (AggregationPipeline) query;
			try {
			  var clientSession = request.getClientSession();

			  var stages = StagesInterpolator.interpolate(VAR_OPERATOR.$var, STAGE_OPERATOR.$ifvar, pipeline.getStages(), avars);

			  // Security validation: check aggregation pipeline for blacklisted stages and operators
			  try {
				var stagesArray = new BsonArray();
				stages.forEach(stagesArray::add);
				securityChecker.validatePipelineOrThrow(stagesArray, request.getDBName());
			  } catch (SecurityException se) {
				response.setInError(HttpStatus.SC_FORBIDDEN, "aggregation pipeline security violation: " + se.getMessage());
				LOGGER.warn("Aggregation pipeline blocked for security violation: {}", se.getMessage());
				next(exchange);
				return;
			  }

			  if (clientSession == null) {
				agrOutput = dbs.collection(request.rsOps(), request.getDBName(), request.getCollectionName())
					.aggregate(stages)
					.maxTime(MongoServiceConfiguration.get().getAggregationTimeLimit(), TimeUnit.MILLISECONDS)
					.allowDiskUse(pipeline.getAllowDiskUse().getValue());
			  } else {
				agrOutput = dbs.collection(request.rsOps(), request.getDBName(), request.getCollectionName())
					.aggregate(clientSession, stages)
					.maxTime(MongoServiceConfiguration.get().getAggregationTimeLimit(), TimeUnit.MILLISECONDS)
					.allowDiskUse(pipeline.getAllowDiskUse().getValue());
			  }

			  // when the last stage of the aggregation is $merge or $out
			  // execute the aggregation with AggregateIterable.toCollection()
			  // otherwise the entire view will be retuned, this can be the whole
			  // collection in the worst case
			  var isMergeOrOutSuffixed = stages.get(stages.size() - 1).keySet().stream().filter(k -> "$merge".equals(k) || "_$merge".equals(k) || "$out".equals(k) || "_$out".equals(k)).findAny().isPresent();

			  if (isMergeOrOutSuffixed) {
				agrOutput.toCollection();
			  } else {
				agrOutput.into(_data);
			  }
			} catch (MongoCommandException mce) {
			  response.setInError(HttpStatus.SC_UNPROCESSABLE_ENTITY, "error executing aggregation", mce);
			  LOGGER.error("error executing aggregation /{}/{}/_aggrs/{}: {}", request.getDBName(), request.getCollectionName(), queryUri, mongoCommandExceptionError(mce));
			  next(exchange);
			  return;
			} catch (InvalidMetadataException ex) {
			  response.setInError(HttpStatus.SC_UNPROCESSABLE_ENTITY, "invalid aggregation", ex);
			  LOGGER.error("invalid aggregation /{}/{}/_aggrs/{}", request.getDBName(), request.getCollectionName(), queryUri, ex);
			  next(exchange);
			  return;
			} catch (QueryVariableNotBoundException qvnbe) {
			  response.setInError(HttpStatus.SC_BAD_REQUEST, "cannot execute aggregation", qvnbe);
			  LOGGER.error("error executing aggregation /{}/{}/_aggrs/{}", request.getDBName(), request.getCollectionName(), queryUri, qvnbe);
			  next(exchange);
			  return;
			}
		  } else {
			response.setInError(HttpStatus.SC_UNPROCESSABLE_ENTITY, "unknown pipeline type");
			LOGGER.error("error executing pipeline: unknown type {} for /{}/{}/_aggrs/{}", query.getType(), request.getDBName(), request.getCollectionName(), queryUri);
			next(exchange);
			return;
		  }
        }

        if (exchange.isComplete()) {
            // if an error occurred getting data, the exchange is already closed
            return;
        }

        try {
            var data = new BsonArray();

            _data.stream().forEachOrdered(data::add);

            response.setContent(data);
            response.setCount(data.size());

            response.setContentTypeAsJson();
            response.setStatusCode(HttpStatus.SC_OK);

            // call the ResponseTransformerMetadataHandler if piped in
            next(exchange);
        } catch (IllegalQueryParameterException ex) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, ex.getMessage(), ex);
            next(exchange);
        }
    }

    public static String mongoCommandExceptionError(MongoCommandException mce) {
        var mongoErrorResponse = mce.getResponse();
        var errorCode = mongoErrorResponse.getNumber("code", new BsonInt32(-1)).intValue();
        var errorCodeName = mongoErrorResponse.getString("codeName", new BsonString("")).getValue();
        var errorMessage = mongoErrorResponse.getString("errmsg", new BsonString("")).getValue();

        if (!errorMessage.isEmpty()) {
            errorMessage = errorMessage.length() <= 100 ? errorMessage: errorMessage.substring(0, 100) + "...";
        }

        if (errorCodeName.isEmpty()) {
            return Integer.toString(errorCode);
        } else {
            return String.format("error code: %d, codeName: %s, message: %s", errorCode, errorCodeName, errorMessage);
        }
    }
}
