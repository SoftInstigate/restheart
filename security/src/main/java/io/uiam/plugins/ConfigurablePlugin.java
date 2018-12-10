/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.plugins;

import java.util.Map;

/**
 * 
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface ConfigurablePlugin {
     /**
     *
     * @param args
     * @param argKey
     * @return the string arg value of argKey from args
     * @throws PluginConfigurationException
     */
    public static <V extends Object> V argValue(final Map<String, Object> args,
            final String argKey)
            throws PluginConfigurationException {
        if (args == null
                || !args.containsKey(argKey)) {
            throw new PluginConfigurationException(
                    "The AuthenticationMechanism"
                    + " requires the argument '" + argKey + "'");
        } else {
            try {
                return (V) args.get(argKey);
            } catch (ClassCastException cce) {
                throw new PluginConfigurationException(
                        "Wrong type for AuthenticationMechanism"
                        + " argument '" + argKey + "'", cce);
            }
        }
    }
    
}
