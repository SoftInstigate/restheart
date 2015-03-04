/*
 * RESTHeart - the data REST API server
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
package org.restheart.hal;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSONSerializers;
import com.mongodb.util.ObjectSerializer;
import java.util.Objects;
import org.bson.BSONObject;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class Representation {
    private final ObjectSerializer serializer = JSONSerializers.getStrict();

    /**
     * Supported content types
     */
    public static final String HAL_JSON_MEDIA_TYPE = "application/hal+json";
    public static final String JSON_MEDIA_TYPE = "application/json";
    public static final String APP_FORM_URLENCODED_TYPE = "application/x-www-form-urlencoded";
    public static final String MULTIPART_FORM_DATA_TYPE = "multipart/form-data";

    private final BasicDBObject properties;
    private final BasicDBObject embedded;
    private final BasicDBObject links;

    /**
     *
     * @param href
     */
    public Representation(String href) {
        properties = new BasicDBObject();
        embedded = new BasicDBObject();
        links = new BasicDBObject();
        
        properties.append("_embedded", embedded);
        properties.append("_links", links);
        
        links.put("self", new BasicDBObject("href", href));
    }
    
    public RequestContext.TYPE getType() {
        if (properties == null)
            return null;
        
        Object _type = properties.get("_type");
        
        if (_type == null)
            return null;
        
        return RequestContext.TYPE.valueOf(_type.toString());
    }

    public BasicDBObject asDBObject() {
        return properties;
    }

    /**
     *
     * @param link
     */
    public void addLink(Link link) {
        links.putAll((BSONObject) ((Link) link).getDBObject());
    }

    /**
     *
     * @param link
     * @param inArray
     */
    public void addLink(Link link, boolean inArray) {
        BasicDBList linkArray = (BasicDBList) links.get(link.getRef());

        if (linkArray == null) {
            linkArray = new BasicDBList();
            links.append(link.getRef(), linkArray);
        }

        linkArray.add(link.getDBObject().get(link.getRef()));

        links.put(link.getRef(), linkArray);
    }

    /**
     *
     * @param key
     * @param value
     */
    public void addProperty(String key, Object value) {
        properties.append(key, value);
    }

    /**
     *
     * @param props
     */
    public void addProperties(DBObject props) {
        if (props == null) {
            return;
        }

        properties.putAll(props);
    }

    /**
     *
     * @param rel
     * @param rep
     */
    public void addRepresentation(String rel, Representation rep) {
        BasicDBList repArray = (BasicDBList) embedded.get(rel);

        if (repArray == null) {
            repArray = new BasicDBList();

            embedded.append(rel, repArray);
        }

        repArray.add(rep.asDBObject());
    }

    public void addWarning(String warning) {
        Representation nrep = new Representation("#warnings");
        nrep.addProperty("message", warning);
        addRepresentation("rh:warnings", nrep);
    }

    @Override
    public String toString() {
        return serializer.serialize(asDBObject());
    }

    @Override
    public int hashCode() {
        return Objects.hash(embedded, links, properties);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Representation other = (Representation) obj;
        if (!Objects.equals(this.properties, other.properties)) {
            return false;
        }
        if (!Objects.equals(this.embedded, other.embedded)) {
            return false;
        }
        if (!Objects.equals(this.links, other.links)) {
            return false;
        }
        return true;
    }
}
