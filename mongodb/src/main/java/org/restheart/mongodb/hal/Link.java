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

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
class Link {
    private final BsonDocument doc = new BsonDocument();

    /**
     *
     * @param ref
     * @param href
     */
    public Link(String ref, String href) {
        if (ref == null || href == null || ref.isEmpty() || href.isEmpty()) {
            throw new IllegalArgumentException("constructor args cannot be null or empty");
        }

        doc.put(ref, new BsonDocument("href", new BsonString(href)));
    }

    /**
     *
     * @param ref
     * @param href
     * @param templated
     */
    public Link(String ref, String href, boolean templated) {
        this(ref, href);

        if (templated) {
            doc.getDocument(ref).put("templated", new BsonBoolean(true));
        }
    }

    /**
     *
     * @param name
     * @param ref
     * @param href
     * @param templated
     */
    public Link(String name, String ref, String href, boolean templated) {
        this(ref, href, templated);

        doc.getDocument(ref).put("name", new BsonString(name));
    }

    /**
     *
     * @return
     */
    public String getRef() {
        return (String) doc.keySet().toArray()[0];
    }

    /**
     *
     * @return
     */
    public String getHref() {
        return doc.getDocument(getRef()).getString("href").getValue();
    }

    BsonDocument getBsonDocument() {
        return doc;
    }
}
