/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.restheart.exchange.InvalidMetadataException;

public abstract class AbstractAggregationOperation {
    private static final Set<String> AGGREGATION_PIPELINE_ALIASES = Sets.newHashSet(new String[]{TYPE.AGGREGATION_PIPELINE.name(), "aggregation pipeline", "aggregationPipeline", "pipeline", "aggregation-pipeline", "aggregation", "aggregate", "ap"});

    /**
     *
     */
    public static final String AGGREGATIONS_ELEMENT_NAME = "aggrs";

    /**
     *
     */
    public static final String URI_ELEMENT_NAME = "uri";

    /**
     *
     */
    public static final String TYPE_ELEMENT_NAME = "type";

    /**
     *
     * @param collProps
     * @return
     * @throws InvalidMetadataException
     */
    public static List<AbstractAggregationOperation> getFromJson(BsonDocument collProps) throws InvalidMetadataException {
        if (collProps == null) {
            return null;
        }

        var ret = new ArrayList<AbstractAggregationOperation>();

        var _aggregations = collProps.get(AGGREGATIONS_ELEMENT_NAME);

        if (_aggregations == null) {
            return ret;
        }

        if (!_aggregations.isArray()) {
            throw new InvalidMetadataException("element '" + AGGREGATIONS_ELEMENT_NAME + "' is not an array list." + _aggregations);
        }

        var aggregations = _aggregations.asArray();

        for (var _query : aggregations.getValues()) {
            if (!_query.isDocument()) {
                throw new InvalidMetadataException("element '" + AGGREGATIONS_ELEMENT_NAME + "' is not valid." + _query);
            }

            ret.add(getQuery(_query.asDocument()));
        }

        return ret;
    }

    private static AbstractAggregationOperation getQuery(BsonDocument query) throws InvalidMetadataException {
        if (!query.containsKey(TYPE_ELEMENT_NAME)) {
            return new AggregationPipeline(query);
        } else {
            var _type = query.get(TYPE_ELEMENT_NAME);
            if (!_type.isString()) {
                throw new InvalidMetadataException("query property '" + TYPE_ELEMENT_NAME + "' must be a String: " + _type.toString());
            }

            var type = _type.asString().getValue();

            if (AGGREGATION_PIPELINE_ALIASES.contains(type)) {
                return new AggregationPipeline(query);
            } else {
                throw new InvalidMetadataException("query has invalid '" + TYPE_ELEMENT_NAME + "': " + type);
            }
        }
    }

    private final TYPE type;
    private final String uri;

    /**
     *
     * @param properties
     * @throws org.restheart.exchange.InvalidMetadataException
     */
    public AbstractAggregationOperation(BsonDocument properties) throws InvalidMetadataException {
        var _uri = properties.get(URI_ELEMENT_NAME);

        if (!properties.containsKey(TYPE_ELEMENT_NAME)) {
            this.type = TYPE.AGGREGATION_PIPELINE;
        } else {
            var _type = properties.get(TYPE_ELEMENT_NAME);

            if (!_type.isString()) {
                throw new InvalidMetadataException("query property not have '" + TYPE_ELEMENT_NAME + "' is not a String: " + _type.toString());
            }

            var stype = _type.asString().getValue();

            if (AGGREGATION_PIPELINE_ALIASES.contains(stype)) {
                this.type = TYPE.AGGREGATION_PIPELINE;
            } else {
                throw new InvalidMetadataException("query has invalid '" + TYPE_ELEMENT_NAME + "' property: " + stype);
            }
        }

        if (!properties.containsKey(URI_ELEMENT_NAME)) {
            throw new InvalidMetadataException("query does not have '" + URI_ELEMENT_NAME + "' property");
        }

        this.uri = _uri.asString().getValue();
    }

    /**
     * @return the type
     */
    public TYPE getType() {
        return type;
    }

    /**
     * @return the uri
     */
    public String getUri() {
        return uri;
    }

    /**
     *
     */
    public enum TYPE {

        /**
         *
         */
        MAP_REDUCE,

        /**
         *
         */
        AGGREGATION_PIPELINE,
    }
}
