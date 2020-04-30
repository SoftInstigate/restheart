/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
