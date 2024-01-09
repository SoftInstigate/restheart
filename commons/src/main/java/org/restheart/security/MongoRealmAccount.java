/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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
package org.restheart.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bson.BsonDocument;
import org.restheart.utils.BsonUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Account implementation used by MongoRealmAuthenticator
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoRealmAccount extends PwdCredentialAccount implements WithProperties<BsonDocument> {
    private static final long serialVersionUID = -5840534832968478775L;

    private final BsonDocument properties;

    /**
     *
     * @param name
     * @param password
     * @param roles
     * @param accountDocument
     */
    public MongoRealmAccount(final String name, final char[] password, final Set<String> roles, BsonDocument properties) {
        super(name, password, roles);

        if (password == null) {
            throw new IllegalArgumentException("argument password cannot be null");
        }

        this.properties = properties;
    }

    @Override
    public BsonDocument properties() {
        return properties;
    }

    private static Gson GSON = new GsonBuilder().serializeNulls().create();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, ? super Object> propertiesAsMap() {
        if (properties == null) {
            return null;
        }

        // we use GSON rather than BsonUtils.bsonToDocument()
        // to preserve the BSON strict representation format
        // as in d: { "$date": 123.0 }; using Document it will turn it to d: 0
        return GSON.fromJson(BsonUtils.toJson(properties), HashMap.class);
    }
}
