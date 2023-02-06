/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2023 SoftInstigate
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

import java.util.Set;
import org.bson.BsonDocument;

/**
 * Account implementation used by MongoRealmAuthenticator
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoRealmAccount extends PwdCredentialAccount {
    private static final long serialVersionUID = -5840534832968478775L;

    private final BsonDocument accountDocument;

    /**
     *
     * @param name
     * @param password
     * @param roles
     * @param accountDocument
     */
    public MongoRealmAccount(final String name, final char[] password, final Set<String> roles, BsonDocument accountDocument) {
        super(name, password, roles);

        if (password == null) {
            throw new IllegalArgumentException("argument password cannot be null");
        }

        this.accountDocument = accountDocument;
    }

    /**
     *
     * @return accountDocument
     */
    public BsonDocument getAccountDocument() {
        return accountDocument;
    }
}
