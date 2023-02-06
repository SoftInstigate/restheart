/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
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
package org.restheart.plugins.security;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import org.restheart.plugins.ConfigurablePlugin;

/**
 * See https://restheart.org/docs/plugins/security-plugins/#authenticators
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Authenticator extends IdentityManager, ConfigurablePlugin {
    @Override
    public Account verify(final Account account);

    @Override
    public Account verify(final String id, final Credential credential);

    @Override
    public Account verify(final Credential credential);
}
