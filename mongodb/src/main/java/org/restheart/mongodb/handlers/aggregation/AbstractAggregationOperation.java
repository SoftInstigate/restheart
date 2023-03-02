/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;

public abstract class AbstractAggregationOperation {

    private static final Set<String> MAP_REDUCE_ALIASES = Sets.newHashSet(new String[]{TYPE.MAP_REDUCE.name(), "map reduce", "mapReduce", "map-reduce", "mr"});

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

            if (MAP_REDUCE_ALIASES.contains(type)) {
                return new MapReduce(query);
            } else if (AGGREGATION_PIPELINE_ALIASES.contains(type)) {
                return new AggregationPipeline(query);
            } else {
                throw new InvalidMetadataException("query has invalid '" + TYPE_ELEMENT_NAME + "': " + type);
            }
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
            var _obj = aVars.asDocument();

            _obj.forEach((key, value) -> {
                if (key.startsWith("$")) {
                    throw new SecurityException("aggregation variables cannot include operators");
                }

                if (value.isDocument() || value.isArray()) {
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

            if (MAP_REDUCE_ALIASES.contains(stype)) {
                this.type = TYPE.MAP_REDUCE;
            } else if (AGGREGATION_PIPELINE_ALIASES.contains(stype)) {
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
     * @param obj
     * @param aVars RequestContext.getAggregationVars()
     * @return the json object where the variables <code>{"_$var": "name" }</code>
     * or <code>{"_$var": [ "name", "defaultValue" ] }</code> are
     * replaced with the values defined in the avars URL query parameter
     * @throws org.restheart.exchange.InvalidMetadataException
     * @throws org.restheart.exchange.QueryVariableNotBoundException
     */
    protected BsonValue bindAggregationVariables(BsonValue obj, BsonDocument aVars) throws InvalidMetadataException, QueryVariableNotBoundException {
        if (obj == null) {
            return null;
        }

        if (obj.isDocument()) {
            var _obj = obj.asDocument();

            if (_obj.size() == 1 && _obj.get("$var") != null) {
                var v = _obj.get("$var");

                if (v.isArray() && v.asArray().size() == 2) { // case {"$var": [ "name", "value" ] }
                    var _name = v.asArray().get(0);
                    var defaultValue = v.asArray().get(1);

                    if (!_name.isString()) {
                        throw new InvalidMetadataException("wrong variable name " + v.toString());
                    }

                    var name = _name.asString().getValue();

                    return aVars == null || aVars.get(name) == null
                        ? defaultValue
                        : aVars.get(_name.asString().getValue());
                } else if (v.isString()) { // case { "$var": "name" }
                    if (aVars == null || aVars.get(v.asString().getValue()) == null) {
                        throw new QueryVariableNotBoundException("variable " + v.asString().getValue() + " not bound");
                    }

                    return aVars.get(v.asString().getValue());
                } else {
                    throw new InvalidMetadataException("wrong variable name " + v.toString());
                }
            } else {
                var ret = new BsonDocument();

                for (var key : _obj.keySet()) {
                    ret.put(key, bindAggregationVariables(_obj.get(key), aVars));
                }

                return ret;
            }
        } else if (obj.isArray()) {
            var ret = new BsonArray();

            for (var el : obj.asArray().getValues()) {
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
