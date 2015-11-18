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

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class AbstractQuery {
    public enum TYPE {
        MAP_REDUCE,
        AGGREGATION
    };

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
            throw new InvalidMetadataException("query element does not have " + TYPE_ELEMENT_NAME + " property");
        }

        if (_uri == null || !(_uri instanceof String)) {
            throw new InvalidMetadataException("query element does not have " + URI_ELEMENT_NAME + " property");
        }

        try {
            this.type = TYPE.valueOf((String) _type);
        } catch (IllegalArgumentException iae) {
            throw new InvalidMetadataException("query element has invalid " + TYPE_ELEMENT_NAME + " property: " + _type);
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
            throw new InvalidMetadataException("query element does not have " + TYPE_ELEMENT_NAME + " property");
        }

        if (TYPE.MAP_REDUCE.name().equals(_type.toString())) {
            return new MapReduceQuery(query);
        } else if (TYPE.AGGREGATION.name().equals(_type.toString())) {
            throw new InvalidMetadataException("AGGREGATION NOT YET IMPLEMENTED");
        } else {
            throw new InvalidMetadataException("query element has invalid " + TYPE_ELEMENT_NAME + ": " + _type.toString());
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
}
