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

import io.undertow.server.HttpServerExchange;
import java.util.List;
import java.util.Optional;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * transformer in charge of escaping the aggregation stages that can contain
 * dollar prefixed keys (for operators)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AggregationTransformer extends PipelinedHandler {
    static final Logger LOGGER = LoggerFactory
            .getLogger(AggregationTransformer.class);

    final boolean phase;

    /**
     *
     * @param phase if 'true' then transform request otherwise transform the response
     */
    public AggregationTransformer(boolean phase) {
        this.phase = phase;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var contentToTransform = phase
                ? MongoRequest.of(exchange).getContent()
                : MongoResponse.of(exchange).getContent();

        if (contentToTransform != null && contentToTransform.isDocument()) {
            transform(request, contentToTransform.asDocument());
        } else if (contentToTransform != null && contentToTransform.isArray()) {
            contentToTransform.asArray().stream().forEachOrdered(
                    (doc) -> transform(request, doc.asDocument()));
        }

        next(exchange);
    }

    private void transform(MongoRequest request, BsonDocument contentToTransform) {
        if (!contentToTransform.isDocument()) {
            throw new IllegalStateException(
                    "content to transform is not a document");
        }

        BsonDocument _contentToTransform = contentToTransform.asDocument();

        if (request.isCollection() || request.isCollectionMeta()) {
            BsonArray aggrs = getAggregationMetadata(_contentToTransform);

            if (aggrs == null) {
                // nothing to do
                return;
            }

            if (request.isPut() || request.isPatch()) {
                _contentToTransform.put(AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME,
                    BsonUtils.escapeKeys(aggrs, true));
            } else if (request.isGet()) {
                _contentToTransform.put(AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME,
                    BsonUtils.unescapeKeys(aggrs));
            }
        }
    }

    private BsonArray getAggregationMetadata(BsonDocument contentToTransform) {
        List<Optional<BsonValue>> ___aggrs = BsonUtils.getPropsFromPath(contentToTransform,
                        "$." + AbstractAggregationOperation.AGGREGATIONS_ELEMENT_NAME);

        if (___aggrs == null || ___aggrs.isEmpty()) {
            return null;
        }

        Optional<BsonValue> __aggrs = ___aggrs.get(0);

        if (__aggrs == null || !__aggrs.isPresent()) {
            return null;
        }

        BsonValue _aggrs = __aggrs.get();

        if (_aggrs.isArray()) {
            return _aggrs.asArray();
        } else {
            return null;
        }
    }
}
