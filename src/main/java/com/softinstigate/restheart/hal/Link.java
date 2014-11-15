/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.hal;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 *
 * @author Andrea Di Cesare
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
