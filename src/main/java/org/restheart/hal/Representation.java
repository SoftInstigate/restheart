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
package org.restheart.hal;

import java.util.Objects;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Representation {
    /**
     * Supported content types
     */
    public static final String HAL_JSON_MEDIA_TYPE = "application/hal+json";
    public static final String JSON_MEDIA_TYPE = "application/json";
    public static final String APP_FORM_URLENCODED_TYPE = "application/x-www-form-urlencoded";
    public static final String MULTIPART_FORM_DATA_TYPE = "multipart/form-data";

    private final BsonDocument properties;
    private final BsonDocument embedded;
    private final BsonDocument links;

    /**
     *
     * @param href
     */
    public Representation(String href) {
        properties = new BsonDocument();
        embedded = new BsonDocument();
        links = new BsonDocument();

        if (href != null) {
            links.put("self", new BsonDocument("href", new BsonString(href)));
        }
    }

    /**
     *
     */
    public Representation() {
        this(null);
    }

    public RequestContext.TYPE getType() {
        if (properties == null) {
            return null;
        }

        Object _type = properties.get("_type");

        if (_type == null) {
            return null;
        }

        return RequestContext.TYPE.valueOf(_type.toString());
    }

    public BsonDocument asBsonDocument() {
        if (embedded == null || embedded.isEmpty()) {
            properties.remove("_embedded");
        } else {
            properties.append("_embedded", embedded);
        }

        if (links == null || links.isEmpty()) {
            properties.remove("_links");
        } else {
            properties.append("_links", links);
        }

        if (links != null && !links.isEmpty()) {
            properties.append("_links", links);
        }

        return properties;
    }

    /**
     *
     * @param link
     */
    public void addLink(Link link) {
        links.putAll(link.getBsonDocument());
    }

    /**
     *
     * @param linkArrayRef
     * @return the created or existing link array
     */
    public BsonArray addLinkArray(String linkArrayRef) {
        if (!links.containsKey(linkArrayRef)) {
            links.append(linkArrayRef, new BsonArray());
        }

        BsonArray linkArray = links.getArray(linkArrayRef);

        return linkArray;
    }

    /**
     *
     * @param link
     * @param inArray
     */
    public void addLink(Link link, boolean inArray) {
        BsonArray linkArray = addLinkArray(link.getRef());

        linkArray.add(link.getBsonDocument().get(link.getRef()));

        links.put(link.getRef(), linkArray);
    }

    /**
     *
     * @param key
     * @param value
     */
    public void addProperty(String key, BsonValue value) {
        properties.append(key, value);
    }

    /**
     *
     * @param props
     */
    public void addProperties(BsonDocument props) {
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
        if (!embedded.containsKey(rel)) {
            embedded.append(rel, new BsonArray());
        }

        BsonArray repArray = embedded.getArray(rel);

        repArray.add(rep.asBsonDocument());
    }

    public void addWarning(String warning) {
        Representation nrep = new Representation("#warnings");
        nrep.addProperty("message", new BsonString(warning));
        addRepresentation("rh:warnings", nrep);
    }

    @Override
    public String toString() {
        return asBsonDocument().toJson();
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
        return Objects.equals(this.links, other.links);
    }
}
