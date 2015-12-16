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
package org.restheart.hal.metadata;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.regex.Matcher;
import org.restheart.utils.JsonUtils;

/**
 * represents a map reduce.
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class MapReduce extends AbstractAggregationOperation {
    private final String map;
    private final String reduce;
    private final DBObject query;

    public static final String MAP_ELEMENT_NAME = "map";
    public static final String REDUCE_ELEMENT_NAME = "reduce";
    public static final String QUERY_ELEMENT_NAME = "query";

    /**
     * @param properties the json properties object. It must include the
     * following properties:
     * <ul>
     * <li><code>type</code></li>
     * <li><code>uri</code></li>
     * <li><code>map</code></li>
     * <li><code>reduce</code></li>
     * </ul>
     * Optionally it can include the following property:
     * <ul>
     * <li><code>query</code></li>
     * </ul>
     * <strong>Note</strong> that the dollar prefixed operators in the query
     * must be underscore escaped, e.g. "_$exits"
     * <p>
     * Example:      <code>
     * aggregations: [
     * {
     *   "type":"mapReduce",
     *   "uri":"test",
     *   "map":"function() { emit(this.name, this.age) }",
     *   "reduce":"function(key, values) { return Array.avg(values) }",
     *   "query": {"name":{"_$exists":true}}
     * }]
     * </code>
     * @throws org.restheart.hal.metadata.InvalidMetadataException
     */
    public MapReduce(DBObject properties)
            throws InvalidMetadataException {
        super(properties);

        Object _map = properties.get(MAP_ELEMENT_NAME);
        Object _reduce = properties.get(REDUCE_ELEMENT_NAME);
        Object _query = properties.get(QUERY_ELEMENT_NAME);

        if (_map == null || !(_map instanceof String)) {
            throw new InvalidMetadataException("invalid query with uri "
                    + getUri() + "; the "
                    + MAP_ELEMENT_NAME
                    + " property is not valid: " + _map);
        }

        if (_reduce == null || !(_reduce instanceof String)) {
            throw new InvalidMetadataException("invalid query with uri "
                    + getUri()
                    + "; the " + REDUCE_ELEMENT_NAME
                    + " property is not valid: " + _reduce);
        }

        if (_query != null && !(_query instanceof DBObject)) {
            throw new InvalidMetadataException("invalid query with uri "
                    + getUri()
                    + "; the " + QUERY_ELEMENT_NAME
                    + " property is not valid: " + _query);
        }

        this.map = (String) _map;
        this.reduce = (String) _reduce;
        this.query = (DBObject) _query;
    }

    /**
     * @return the map
     */
    public String getMap() {
        return map;
    }

    /**
     * @return the reduce
     */
    public String getReduce() {
        return reduce;
    }

    /**
     * @return the query
     */
    public DBObject getQuery() {
        return query;
    }

    /**
     * @param aVars RequestContext.getAggregationVars()
     * @return the query with unescaped operators and bound variables
     * @throws org.restheart.hal.metadata.InvalidMetadataException
     * @throws org.restheart.hal.metadata.QueryVariableNotBoundException
     */
    public DBObject getResolvedQuery(BasicDBObject aVars)
            throws InvalidMetadataException, QueryVariableNotBoundException {
        return (DBObject) bindAggregationVariables(
                JsonUtils.replaceEscapedKeys(query), aVars);
    }

    /**
     * @param aVars RequestContext.getAggregationVars()
     * @return the map function with bound aggregation variables
     */
    public String getResolvedMap(BasicDBObject aVars) {
        if (aVars == null || aVars.isEmpty()) {
            return map;
        } else {
            String escapedAVars = "\""
                    + aVars.toString().replaceAll("\"", "\\\\\\\\\"")
                    + "\"";
            
            String ret = map == null ? null
                    : map.replaceAll(
                            Matcher.quoteReplacement("$") + "vars",
                            escapedAVars);

            return ret;
        }
    }

    /**
     * @param aVars RequestContext.getAggregationVars()
     * @return the reduce function with bound aggregation variables
     */
    public String getResolvedReduce(BasicDBObject aVars) {
        if (aVars == null || aVars.isEmpty()) {
            return map;
        } else {
            String escapedAVars = "\""
                    + aVars.toString().replaceAll("\"", "\\\\\\\\\"")
                    + "\"";
            
            String ret = reduce == null ? null
                    : reduce.replaceAll(
                            Matcher.quoteReplacement("$") + "vars",
                            escapedAVars
                    );

            return ret;
        }
    }
}
