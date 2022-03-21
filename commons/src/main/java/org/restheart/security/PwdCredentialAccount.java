/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2022 SoftInstigate
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

import io.undertow.security.idm.PasswordCredential;
import java.util.Set;

/**
 * Account implementation that holds PasswordCredential
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PwdCredentialAccount extends BaseAccount {
    private static final long serialVersionUID = -5840334837968478775L;
    final transient private PasswordCredential credential;

    /**
     *
     * @param name
     * @param password
     * @param roles
     */
    public PwdCredentialAccount(final String name, final char[] password, final Set<String> roles) {
        super(name, roles);

        if (password == null) {
            throw new IllegalArgumentException("argument password cannot be null");
        }

        this.credential = new PasswordCredential(password);
    }

    /**
     *
     * @return
     */
    public PasswordCredential getCredentials() {
        return credential;
    }
}
