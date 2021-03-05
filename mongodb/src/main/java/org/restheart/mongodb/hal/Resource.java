/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.mongodb.hal;

import java.util.Objects;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.exchange.ExchangeKeys;
import org.restheart.exchange.ExchangeKeys.REPRESENTATION_FORMAT;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.exchange.MongoRequest;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class Resource {

    /**
     * Supported content types
     */
    public static final String HAL_JSON_MEDIA_TYPE = "application/hal+json";

    /**
     *
     */
    public static final String JSON_MEDIA_TYPE = "application/json";

    /**
     *
     */
    public static final String APP_FORM_URLENCODED_TYPE = "application/x-www-form-urlencoded";

    /**
     *
     */
    public static final String APPLICATION_PDF_TYPE = "application/pdf";

    /**
     *
     */
    public static final String MULTIPART_FORM_DATA_TYPE = "multipart/form-data";

    /**
     *
     */
    public static final String JAVACRIPT_MEDIA_TYPE = "application/javascript";
    private static final String TYPE = "_type";
    private static final String EMBEDDED = "_embedded";
    private static final String LINKS = "_links";

    private final BsonDocument properties;
    private final BsonDocument embedded;
    private final BsonDocument links;

    /**
     *
     * @param href
     */
    public Resource(String href) {
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
    public Resource() {
        this(null);
    }

    /**
     *
     * @return
     */
    public TYPE getType() {
        if (properties == null) {
            return null;
        }

        Object _type = properties.get(TYPE);

        if (_type == null) {
            return null;
        }

        return ExchangeKeys.TYPE.valueOf(_type.toString());
    }

    /**
     *
     * @return
     */
    public BsonDocument asBsonDocument() {
        if (embedded == null || embedded.isEmpty()) {
            properties.remove(EMBEDDED);
        } else {
            properties.append(EMBEDDED, embedded);
        }

        if (links == null || links.isEmpty()) {
            properties.remove(LINKS);
        } else {
            properties.append(LINKS, links);
        }

        if (links != null && !links.isEmpty()) {
            properties.append(LINKS, links);
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
    public void addChild(String rel, Resource rep) {
        if (!embedded.containsKey(rel)) {
            embedded.append(rel, new BsonArray());
        }

        BsonArray repArray = embedded.getArray(rel);

        repArray.add(rep.asBsonDocument());
    }

    /**
     *
     * @param warning
     */
    public void addWarning(String warning) {
        Resource nrep = new Resource("#warnings");
        nrep.addProperty("message", new BsonString(warning));
        addChild("rh:warnings", nrep);
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
        final Resource other = (Resource) obj;
        if (!Objects.equals(this.properties, other.properties)) {
            return false;
        }
        if (!Objects.equals(this.embedded, other.embedded)) {
            return false;
        }
        return Objects.equals(this.links, other.links);
    }

    /**
     * @param request
     * @return true if representationFormat == HAL
     */
    public static boolean isHAL(MongoRequest request) {
        return request.getRepresentationFormat() == REPRESENTATION_FORMAT.HAL;
    }

    /**
     * @param request
     * @return true if representationFormat == SHAL or PLAIN_JSON or PJ
     */
    public static boolean isSHAL(MongoRequest request) {
        return request.getRepresentationFormat() == REPRESENTATION_FORMAT.SHAL
                || request.getRepresentationFormat()
                == REPRESENTATION_FORMAT.PLAIN_JSON
                || request.getRepresentationFormat()
                == REPRESENTATION_FORMAT.PJ;
    }

    /**
     * @param request
     * @return true if representationFormat == STSNDARD or S
     */
    public static boolean isStandardRep(MongoRequest request) {
        return request.getRepresentationFormat() == REPRESENTATION_FORMAT.STANDARD
                || request.getRepresentationFormat() == REPRESENTATION_FORMAT.S;
    }
}
