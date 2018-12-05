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

import io.uiam.Configuration;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.uiam.plugins.authentication.PluggableIdentityManager;
import io.uiam.plugins.authorization.PluggableAccessManager;
import io.uiam.plugins.service.PluggableService;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PluginsFactory {
    /**
     * getAutenticationMechanism
     *
     * @param conf the configuration Map
     * @return the PluggableAuthenticationMechanism
     */
    public static PluggableAuthenticationMechanism getAutenticationMechanism(Map<String, Object> conf)
            throws PluginConfigurationException {
        Object _name = conf.get(Configuration.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new PluginConfigurationException("Error configuring Authentication Mechanism "
                    + "missing "
                    + Configuration.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(Configuration.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new PluginConfigurationException("Error configuring Authentication Mechanism "
                    + (String) _name
                    + " missing "
                    + Configuration.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(Configuration.ARGS_KEY);

        try {
            if (_args == null) {
                return (PluggableAuthenticationMechanism) Class
                        .forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map)) {
                    throw new PluginConfigurationException("Error configuring Authentication Mechanism "
                            + (String) _name
                            + Configuration.ARGS_KEY
                            + " property"
                            + " is not a map");
                } else {
                    return (PluggableAuthenticationMechanism) Class
                            .forName((String) _clazz)
                            .getDeclaredConstructor(String.class, Map.class)
                            .newInstance((String) _name, (Map) _args);
                }
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new PluginConfigurationException("Error configuring Authentication Mechanism "
                    + (_name != null ? _name : ""),
                    ex);
        }
    }

    /**
     * getIdentityManager
     *
     * @param conf the configuration Map
     * @return the PluggableIdentityManager
     */
    public static PluggableIdentityManager getIdentityManager(Map<String, Object> conf)
            throws PluginConfigurationException {
        Object _name = conf.get(Configuration.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new PluginConfigurationException("Error configuring Identity Manager "
                    + "missing "
                    + Configuration.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(Configuration.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new PluginConfigurationException("Error configuring Identity Manager "
                    + (String) _name
                    + " missing "
                    + Configuration.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(Configuration.ARGS_KEY);

        try {
            if (_args == null) {
                return (PluggableIdentityManager) Class
                        .forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map)) {
                    throw new PluginConfigurationException("Error configuring Identity Manager "
                            + (String) _name
                            + Configuration.ARGS_KEY
                            + " property"
                            + " is not a map");
                } else {
                    return (PluggableIdentityManager) Class
                            .forName((String) _clazz)
                            .getDeclaredConstructor(String.class, Map.class)
                            .newInstance((String) _name, (Map) _args);
                }
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new PluginConfigurationException("Error configuring Identity Manager " + "", ex);
        }
    }

    /**
     * getAccessManager
     *
     * @return the PluggableAccessManager
     */
    public static PluggableAccessManager getAccessManager(Map<String, Object> conf)
            throws PluginConfigurationException {
        Object _name = conf.get(Configuration.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new PluginConfigurationException("Error configuring Access Manager "
                    + "missing "
                    + Configuration.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(Configuration.CLASS_KEY);

        if (!(_clazz instanceof String)) {
            throw new PluginConfigurationException("Error configuring Access Manager "
                    + Configuration.CLASS_KEY
                    + " property is not a String");
        }

        Object _args = conf.get(Configuration.ARGS_KEY);

        try {
            if (_args == null) {
                return (PluggableAccessManager) Class
                        .forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map)) {
                    throw new PluginConfigurationException("Error configuring Access Manager "
                            + Configuration.ARGS_KEY
                            + " property"
                            + " is not a map");
                } else {
                    return (PluggableAccessManager) Class
                            .forName((String) _clazz)
                            .getDeclaredConstructor(String.class, Map.class)
                            .newInstance((String) _name, (Map) _args);
                }
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new PluginConfigurationException("Error configuring Access Manager " + "", ex);
        }
    }

    /**
     * getService
     *
     * @return the PluggableService
     */
    public static PluggableService getService(Map<String, Object> conf)
            throws PluginConfigurationException {
        Object _name = conf.get(Configuration.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new PluginConfigurationException("Error configuring Service "
                    + "missing "
                    + Configuration.NAME_KEY
                    + " property");
        }

        Object _secured = conf.get(Configuration.SERVICE_SECURED_KEY);

        if (_secured == null || !(_secured instanceof Boolean)) {
            throw new PluginConfigurationException("Error configuring Service "
                    + "missing "
                    + Configuration.SERVICE_SECURED_KEY
                    + " property");
        }

        Object _uri = conf.get(Configuration.SERVICE_URI_KEY);

        if (_uri == null
                || !(_uri instanceof String)
                || !((String) _uri).startsWith("/")) {
            throw new PluginConfigurationException("Error configuring Service "
                    + "missing "
                    + Configuration.SERVICE_URI_KEY
                    + " property");
        }

        Object _clazz = conf.get(Configuration.CLASS_KEY);

        if (!(_clazz instanceof String)) {
            throw new PluginConfigurationException("Error configuring Service "
                    + Configuration.CLASS_KEY
                    + " property is not a String");
        }

        Object _args = conf.get(Configuration.ARGS_KEY);

        try {
            if (_args == null) {
                return (PluggableService) Class
                        .forName((String) _clazz)
                        .getDeclaredConstructor(
                                PipedHttpHandler.class,
                                String.class,
                                String.class,
                                Boolean.class
                        )
                        .newInstance(null,
                                (String) _name,
                                (String) _uri,
                                (Boolean) _secured);
            } else {
                if (!(_args instanceof Map)) {
                    throw new PluginConfigurationException("Error configuring Service "
                            + Configuration.ARGS_KEY
                            + " property"
                            + " is not a map");
                } else {
                    return (PluggableService) Class
                            .forName((String) _clazz)
                            .getDeclaredConstructor(
                                    PipedHttpHandler.class,
                                    String.class,
                                    String.class,
                                    Boolean.class,
                                    Map.class)
                            .newInstance(null,
                                    (String) _name,
                                    (String) _uri,
                                    (Boolean) _secured,
                                    (Map) _args);
                }
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new PluginConfigurationException("Error configuring Service " + "", ex);
        }
    }
}
