/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2020 SoftInstigate
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

package org.restheart.test.plugins.initializers;

import org.restheart.exchange.JsonProxyRequest;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import static org.restheart.utils.URLUtils.removeTrailingSlashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Just an example initializer. It is not enabledByDefault; to enable it add to
 * configuration file:<br>
 * <pre>
 *   testInitializer:
 *       enabled: true
 * </pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "testInitializer",
        priority = 100,
        description = "The initializer used to test interceptors and veto predicates",
        enabledByDefault = false)
public class TestInitializer implements Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestInitializer.class);

    @Inject("acl-registry")
    private ACLRegistry registry;

    @Override
    public void init() {
        LOGGER.info("Testing initializer");
        LOGGER.info("\tdenies GET /secho/foo using a veto predicate");
        LOGGER.info("\tadds a request and a response interceptors for /iecho and /siecho");

        // add a global security predicate
        this.registry.registerVeto(req -> {
                if (req instanceof JsonProxyRequest jreq) {
                    return (jreq.isGet() && "/secho/foo".equals(removeTrailingSlashes(jreq.getPath())));
                } else {
                    return false; // don't veto
                }
            });
    }
}
