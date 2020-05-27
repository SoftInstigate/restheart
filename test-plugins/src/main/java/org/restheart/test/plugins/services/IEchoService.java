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

package org.restheart.test.plugins.services;

import org.restheart.plugins.RegisterPlugin;

/**
 * used for testing
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "iecho",
        description = "only used for automatic testing purposes",
        enabledByDefault = false,
        defaultURI = "/iecho")
public class IEchoService extends EchoService {

}
