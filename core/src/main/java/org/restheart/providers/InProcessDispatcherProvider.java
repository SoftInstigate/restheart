/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.providers;

import java.time.Duration;
import java.util.Map;

import org.restheart.Bootstrapper;
import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.InProcessDispatcher;

/**
 * PROOF OF CONCEPT
 *
 * Provides the {@link InProcessDispatcher} that runs a request through this server's
 * own handler chain without a socket. Inject it with
 * {@code @Inject("in-process-dispatcher")}.
 *
 * <p>Configuration: {@code timeout-seconds}, how long a dispatch waits for its response,
 * default {@link InProcessDispatcher#DEFAULT_TIMEOUT}.</p>
 */
@RegisterPlugin(name = "in-process-dispatcher", description = "provides the dispatcher that runs a request through the server's handler chain in-process, over an XNIO pipe")
public class InProcessDispatcherProvider implements Provider<InProcessDispatcher> {
    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() throws ConfigurationException {
        int seconds = argOrDefault(config, "timeout-seconds", (int) InProcessDispatcher.DEFAULT_TIMEOUT.toSeconds());

        if (seconds <= 0) {
            throw new ConfigurationException("in-process-dispatcher: timeout-seconds must be positive, got " + seconds);
        }

        Bootstrapper.inProcessDispatcher().setTimeout(Duration.ofSeconds(seconds));
    }

    @Override
    public InProcessDispatcher get(PluginRecord<?> caller) {
        return Bootstrapper.inProcessDispatcher();
    }
}
