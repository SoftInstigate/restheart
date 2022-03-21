/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart.security.authorizers;

import java.util.Map;
import org.restheart.ConfigurationException;
import org.restheart.exchange.Request;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "fullAuthorizer",
        description = "authorizes all requests",
        enabledByDefault = false)
public class FullAuthorizer implements Authorizer {

    private boolean authenticationRequired;

    /**
     * this Authorizer allows any operation to any user
     *
     * @param authenticationRequired
     */
    public FullAuthorizer(boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    /**
     * this Authorizer allows any operation to any user
     *
     * @throws org.restheart.ConfigurationException
     */
    public FullAuthorizer() throws ConfigurationException {
        this(false);
    }

    @InjectConfiguration
    public void initConf(Map<String, Object> confArgs) {
        this.authenticationRequired = argValue(confArgs, "authentication-required");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isAllowed(final Request request) {
        return true;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isAuthenticationRequired(final Request request) {
        return !request.isOptions() && authenticationRequired;
    }
}
