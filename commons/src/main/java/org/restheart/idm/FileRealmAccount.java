/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.idm;

import java.util.Map;
import java.util.Set;


/**
 * Account implementation used by FileRealmAuthenticator
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class FileRealmAccount extends PwdCredentialAccount {
    private static final long serialVersionUID = -5840534832968478775L;

    private final Map<String, Object> accountProperties;

    /**
     *
     * @param name
     * @param password
     * @param roles
     * @param accountDocument
     */
    public FileRealmAccount(final String name, final char[] password, final Set<String> roles, Map<String, Object> accountProperties) {
        super(name, password, roles);

        if (password == null) {
            throw new IllegalArgumentException("argument password cannot be null");
        }

        this.accountProperties = accountProperties;
    }

    /**
     *
     * @return  accountProperties
     */
    public Map<String, Object> getAccountProperties() {
        return accountProperties;
    }
}
