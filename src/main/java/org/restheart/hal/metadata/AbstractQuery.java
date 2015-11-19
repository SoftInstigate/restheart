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
package org.restheart.hal.metadata;

import com.google.common.collect.Sets;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class AbstractQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractQuery.class);

    public enum TYPE {
        MAP_REDUCE,
        AGGREGATION_PIPELINE,
    };

    private static final Set<String> MAP_REDUCE_ALIASES = Sets.newHashSet(new String[]{TYPE.MAP_REDUCE.name(), "map reduce", "mapReduce", "map-reduce", "mr"});
    private static final Set<String> AGGREGATION_PIPELINE_ALIASES = Sets.newHashSet(new String[]{TYPE.AGGREGATION_PIPELINE.name(), "aggregation pipeline", "aggregationPipeline", "aggregation-pipeline", "aggregation", "aggregate", "ap"});

    public static final String QUERIES_ELEMENT_NAME = "queries";
    public static final String URI_ELEMENT_NAME = "uri";
    public static final String TYPE_ELEMENT_NAME = "type";

    public static final String PIPELINE_ELEMENT_NAME = "pipeline";

    private final TYPE type;
    private final String uri;

    /**
     *
     * @param properties
     * @throws org.restheart.hal.metadata.InvalidMetadataException
     */
    public AbstractQuery(DBObject properties) throws InvalidMetadataException {
        Object _type = properties.get(TYPE_ELEMENT_NAME);
        Object _uri = properties.get(URI_ELEMENT_NAME);

        if (_type == null || !(_type instanceof String)) {
            throw new InvalidMetadataException("query element does not have '" + TYPE_ELEMENT_NAME + "' property");
        }

        if (_uri == null || !(_uri instanceof String)) {
            throw new InvalidMetadataException("query element does not have '" + URI_ELEMENT_NAME + "' property");
        }

        if (MAP_REDUCE_ALIASES.contains((String) _type)) {
            this.type = TYPE.MAP_REDUCE;
        } else if (AGGREGATION_PIPELINE_ALIASES.contains((String) _type)) {
            this.type = TYPE.AGGREGATION_PIPELINE;
        } else {
            throw new InvalidMetadataException("query element has invalid '" + TYPE_ELEMENT_NAME + "' property: " + _type);
        }

        this.uri = (String) _uri;
    }

    /**
     *
     * @param collProps
     * @return
     * @throws InvalidMetadataException
     */
    public static List<AbstractQuery> getFromJson(DBObject collProps) throws InvalidMetadataException {
        if (collProps == null) {
            return null;
        }

        ArrayList<AbstractQuery> ret = new ArrayList<>();

        Object _queries = collProps.get(QUERIES_ELEMENT_NAME);

        if (_queries == null) {
            return ret;
        }

        if (!(_queries instanceof BasicDBList)) {
            throw new InvalidMetadataException("element '" + QUERIES_ELEMENT_NAME + "' is not an array list." + _queries);
        }

        BasicDBList queries = (BasicDBList) _queries;

        for (Object _query : queries.toArray()) {
            if (!(_query instanceof DBObject)) {
                throw new InvalidMetadataException("element '" + QUERIES_ELEMENT_NAME + "' is not valid." + _query);
            }

            ret.add(getQuery((DBObject) _query));
        }

        return ret;
    }

    private static AbstractQuery getQuery(DBObject query) throws InvalidMetadataException {

        Object _type = query.get(TYPE_ELEMENT_NAME);

        if (_type == null) {
            throw new InvalidMetadataException("query element does not have '" + TYPE_ELEMENT_NAME + "' property");
        }

        if (MAP_REDUCE_ALIASES.contains(_type.toString())) {
            return new MapReduceQuery(query);
        } else if (AGGREGATION_PIPELINE_ALIASES.contains(_type.toString())) {
            return new AggregationPipelineQuery(query);
        } else {
            throw new InvalidMetadataException("query element has invalid '" + TYPE_ELEMENT_NAME + "': " + _type.toString());
        }
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
     * replaces the underscore prefixed operators (eg _$exists) with the
     * corresponding operator (eg $exists). This is needed because MongoDB does
     * not allow to store keys that are valid operators.
     *
     * @param obj
     * @return the json object where the underscore prefixed operators are
     * replaced with the corresponding operator
     */
    protected Object replaceEscapedOperators(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof BasicDBObject) {
            BasicDBObject ret = new BasicDBObject();

            ((BasicDBObject) obj).keySet().stream().forEach(k -> {
                String newKey = k.startsWith("_$") ? k.substring(1) : k;
                Object value = ((BasicDBObject) obj).get(k);

                if (value instanceof BasicDBObject) {
                    ret.put(newKey, replaceEscapedOperators((BasicDBObject) value));
                } else if (value instanceof BasicDBList) {
                    BasicDBList newList = new BasicDBList();

                    ((BasicDBList) value).stream().forEach(v -> {
                        newList.add(replaceEscapedOperators(v));
                    });

                    ret.put(newKey, newList);
                } else {
                    ret.put(newKey, replaceEscapedOperators(value));
                }

            });

            return ret;
        } else if (obj instanceof BasicDBList) {
            BasicDBList ret = new BasicDBList();

            ((BasicDBList) obj).stream().forEach(value -> {
                if (value instanceof BasicDBObject) {
                    ret.add(replaceEscapedOperators((BasicDBObject) value));
                } else if (value instanceof BasicDBList) {
                    BasicDBList newList = new BasicDBList();

                    ((BasicDBList) value).stream().forEach(v -> {
                        newList.add(replaceEscapedOperators(v));
                    });

                    ret.add(newList);
                } else {
                    ret.add(replaceEscapedOperators(value));
                }

            });

            return ret;
        } else if (obj instanceof String) {
            return ((String) obj).startsWith("_$") ? ((String) obj).substring(1) : obj;
        } else {
            return obj;
        }
    }

    /**
     * @param obj
     * @param qvars RequestContext.getQvars()
     * @return the json object where the variables ({"_$var": "var") are replaced
     * with the values defined in the qvar URL query parameter
     * @throws org.restheart.hal.metadata.InvalidMetadataException
     * @throws org.restheart.hal.metadata.QueryVariableNotBoundException
     */
    protected Object bindQueryVariables(Object obj, DBObject qvars) throws InvalidMetadataException, QueryVariableNotBoundException {
        if (obj == null) {
            return null;
        }

        if (obj instanceof BasicDBObject) {
            BasicDBObject _obj = (BasicDBObject) obj;

            if (_obj.size() == 1 && _obj.get("$var") != null) {
                Object varName = _obj.get("$var");

                if (!(varName instanceof String)) {
                    throw new InvalidMetadataException("wrong variable name " + varName.toString());
                }

                if (qvars == null || qvars.get((String) varName) == null) {
                    throw new QueryVariableNotBoundException("variable " + varName + " not bound");
                }

                return qvars.get((String) varName);
            } else {
                BasicDBObject ret = new BasicDBObject();

                for (String key : ((BasicDBObject) obj).keySet()) {
                    ret.put(key, bindQueryVariables(((BasicDBObject) obj).get(key), qvars));
                }
                
                return ret;
            }
        } else if (obj instanceof BasicDBList) {
            BasicDBList ret = new BasicDBList();

            for (Object el : ((BasicDBList) obj).toArray()) {
                ret.add(bindQueryVariables((BasicDBObject) el, qvars));
            }

            return ret;

        } else {
            return obj;
        }
    }
}
