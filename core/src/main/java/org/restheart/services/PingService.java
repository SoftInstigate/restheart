/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
package org.restheart.services;

import java.util.Map;
import org.restheart.ConfigurationException;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "ping", 
    description = "simple ping service",
    secure = false,
    enabledByDefault = true,
    defaultURI = "/ping")
public class PingService implements ByteArrayService {

    private String msg = null;

    @InjectConfiguration
    public void init(Map<String, Object> args) throws ConfigurationException {
        this.msg = argValue(args, "msg");
    }

    /**
     *
     * @throws Exception
     */
    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        if (request.isGet()) {
            var accept = request.getHeader("Accept");

            if (accept != null && accept.startsWith("text/html")) {
                var content = "<div><h2>" + msg + "</h2></div>";
                response.setContent(content.getBytes());
                response.setContentType("text/html");
            } else {
                response.setContentType("text/plain");
                response.setContent(msg.getBytes());
            }
        } else if (request.isOptions()) {
            handleOptions(request);
        } else {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        }
    }
}
