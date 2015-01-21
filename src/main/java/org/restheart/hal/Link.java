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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class Link {

    private final BasicDBObject dbObject = new BasicDBObject();

    /**
     *
     * @param ref
     * @param href
     */
    public Link(String ref, String href) {
        if (ref == null || href == null || ref.isEmpty() || href.isEmpty()) {
            throw new IllegalArgumentException("constructor args cannot be null or empty");
        }

        dbObject.put(ref, new BasicDBObject("href", href));
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
            ((BasicDBObject) dbObject.get(ref)).put("templated", true);
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

        ((BasicDBObject) dbObject.get(ref)).put("name", name);
    }

    /**
     *
     * @return
     */
    public String getRef() {
        return (String) dbObject.keySet().toArray()[0];
    }

    /**
     *
     * @return
     */
    public String getHref() {
        return (String) ((DBObject) dbObject.get(getRef())).get("href");
    }

    BasicDBObject getDBObject() {
        return dbObject;
    }
}
