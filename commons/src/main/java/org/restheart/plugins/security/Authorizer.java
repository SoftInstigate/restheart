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
package org.restheart.plugins.security;

import org.restheart.exchange.Request;
import org.restheart.plugins.ConfigurablePlugin;

/**
 * Seehttps://restheart.org/docs/plugins/security-plugins/#authorizers
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Authorizer extends ConfigurablePlugin {

    /**
     *
     * @param request
     * @return true if request is allowed
     */
    @SuppressWarnings("rawtypes")
    boolean isAllowed(final Request request);

    /**
     *
     * @param request
     * @return true if not authenticated user won't be allowed
     */
    @SuppressWarnings("rawtypes")
    boolean isAuthenticationRequired(final Request request);
}
