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

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.mongodb.metadata.InvalidMetadataException;
public abstract class AbstractAggregationOperation {

    private static final Set<String> MAP_REDUCE_ALIASES
            = Sets.newHashSet(new String[]{TYPE.MAP_REDUCE.name(),
        "map reduce", "mapReduce", "map-reduce", "mr"});

    private static final Set<String> AGGREGATION_PIPELINE_ALIASES
            = Sets.newHashSet(new String[]{TYPE.AGGREGATION_PIPELINE.name(),
        "aggregation pipeline", "aggregationPipeline", "pipeline",
        "aggregation-pipeline", "aggregation", "aggregate", "ap"});

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
    public static List<AbstractAggregationOperation>
            getFromJson(BsonDocument collProps)
            throws InvalidMetadataException {
        if (collProps == null) {
            return null;
        }

        ArrayList<AbstractAggregationOperation> ret = new ArrayList<>();

        BsonValue _aggregations = collProps.get(AGGREGATIONS_ELEMENT_NAME);

        if (_aggregations == null) {
            return ret;
        }

        if (!_aggregations.isArray()) {
            throw new InvalidMetadataException("element '"
                    + AGGREGATIONS_ELEMENT_NAME
                    + "' is not an array list." + _aggregations);
        }

        BsonArray aggregations = _aggregations.asArray();

        for (BsonValue _query : aggregations.getValues()) {
            if (!_query.isDocument()) {
                throw new InvalidMetadataException("element '"
                        + AGGREGATIONS_ELEMENT_NAME
                        + "' is not valid." + _query);
            }

            ret.add(getQuery(_query.asDocument()));
        }

        return ret;
    }

    private static AbstractAggregationOperation getQuery(BsonDocument query)
            throws InvalidMetadataException {

        BsonValue _type = query.get(TYPE_ELEMENT_NAME);

        if (!query.containsKey(TYPE_ELEMENT_NAME)) {
            throw new InvalidMetadataException(
                    "query does not have '"
                    + TYPE_ELEMENT_NAME
                    + "' property");
        }

        if (!_type.isString()) {
            throw new InvalidMetadataException(
                    "query property '"
                    + TYPE_ELEMENT_NAME
                    + "' must be a String: "
                    + _type.toString());
        }

        String type = _type.asString().getValue();

        if (MAP_REDUCE_ALIASES.contains(type)) {
            return new MapReduce(query);
        } else if (AGGREGATION_PIPELINE_ALIASES.contains(type)) {
            return new AggregationPipeline(query);
        } else {
            throw new InvalidMetadataException("query has invalid '"
                    + TYPE_ELEMENT_NAME + "': " + type);
        }
    }

    /**
     * checks if the aggregation variable start with $ this is not allowed since
     * the client would be able to modify the aggregation stages
     *
     * @param aVars RequestContext.getAggregationVars()
     */
    public static void checkAggregationVariables(BsonValue aVars) throws SecurityException {
        if (aVars == null) {
            return;
        }
        if (aVars.isDocument()) {
            BsonDocument _obj = aVars.asDocument();

            _obj.forEach((key, value) -> {
                if (key.startsWith("$")) {
                    throw new SecurityException(
                            "aggregation variables cannot include operators");
                }

                if (value.isDocument()
                        || value.isArray()) {
                    checkAggregationVariables(value);
                }
            });
        } else if (aVars.isArray()) {
            aVars.asArray().getValues().stream()
                    .filter(el -> (el.isDocument() || el.isArray()))
                    .forEachOrdered(AbstractAggregationOperation::checkAggregationVariables);
        }
    }

    private final TYPE type;
    private final String uri;

    /**
     *
     * @param properties
     * @throws org.restheart.mongodb.metadata.InvalidMetadataException
     */
    public AbstractAggregationOperation(BsonDocument properties)
            throws InvalidMetadataException {
        BsonValue _uri = properties.get(URI_ELEMENT_NAME);

        if (!properties.containsKey(TYPE_ELEMENT_NAME)) {
            throw new InvalidMetadataException(
                    "query does not have '"
                    + TYPE_ELEMENT_NAME
                    + "' property");
        }

        BsonValue _type = properties.get(TYPE_ELEMENT_NAME);

        if (!_type.isString()) {
            throw new InvalidMetadataException(
                    "query property not have '"
                    + TYPE_ELEMENT_NAME
                    + "' is not a String: "
                    + _type.toString());
        }

        String stype = _type.asString().getValue();

        if (MAP_REDUCE_ALIASES.contains(stype)) {
            this.type = TYPE.MAP_REDUCE;
        } else if (AGGREGATION_PIPELINE_ALIASES.contains(stype)) {
            this.type = TYPE.AGGREGATION_PIPELINE;
        } else {
            throw new InvalidMetadataException(
                    "query has invalid '"
                    + TYPE_ELEMENT_NAME
                    + "' property: "
                    + stype);
        }

        if (!properties.containsKey(URI_ELEMENT_NAME)) {
            throw new InvalidMetadataException("query does not have '"
                    + URI_ELEMENT_NAME + "' property");
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
     * @param obj
     * @param aVars RequestContext.getAggregationVars()
     * @return the json object where the variables ({"_$var": "var") are
     * replaced with the values defined in the avars URL query parameter
     * @throws org.restheart.mongodb.metadata.InvalidMetadataException
     * @throws org.restheart.mongodb.handlers.aggregation.QueryVariableNotBoundException
     */
    protected BsonValue bindAggregationVariables(
            BsonValue obj,
            BsonDocument aVars)
            throws InvalidMetadataException, QueryVariableNotBoundException {
        if (obj == null) {
            return null;
        }
        
        if (obj.isDocument()) {
            BsonDocument _obj = obj.asDocument();

            if (_obj.size() == 1 && _obj.get("$var") != null) {
                BsonValue varName = _obj.get("$var");

                if (!(varName.isString())) {
                    throw new InvalidMetadataException("wrong variable name "
                            + varName.toString());
                }

                if (aVars == null
                        || aVars.get(varName.asString().getValue()) == null) {
                    throw new QueryVariableNotBoundException("variable "
                            + varName.asString().getValue() + " not bound");
                }

                return aVars.get(varName.asString().getValue());
            } else {
                BsonDocument ret = new BsonDocument();

                for (String key : _obj.keySet()) {
                    ret.put(key,
                            bindAggregationVariables(
                                    _obj.get(key), aVars));
                }

                return ret;
            }
        } else if (obj.isArray()) {
            BsonArray ret = new BsonArray();

            for (BsonValue el : obj.asArray().getValues()) {
                if (el.isDocument()) {
                    ret.add(bindAggregationVariables(el, aVars));
                } else if (el.isArray()) {
                    ret.add(bindAggregationVariables(el, aVars));
                } else {
                    ret.add(el);
                }
            }

            return ret;

        } else {
            return obj;
        }
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
