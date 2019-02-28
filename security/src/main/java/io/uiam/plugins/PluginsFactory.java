/*
 * uIAM - the IAM for microservices
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uiam.plugins;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import io.uiam.Configuration;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.uiam.plugins.authentication.PluggableIdentityManager;
import io.uiam.plugins.authentication.PluggableTokenManager;
import io.uiam.plugins.authorization.PluggableAccessManager;
import io.uiam.plugins.service.PluggableService;

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
    static PluggableAuthenticationMechanism createAutenticationMechanism(
            Map<String, Object> conf)
            throws PluginConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new PluginConfigurationException(
                    "Error configuring Authentication Mechanism,"
                    + " missing configuration");
        }

        Object _name = conf.get(Configuration.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new PluginConfigurationException(
                    "Error configuring Authentication Mechanism, missing "
                    + Configuration.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(Configuration.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new PluginConfigurationException(
                    "Error configuring Authentication Mechanism "
                    + (String) _name
                    + ", missing "
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
                    throw new PluginConfigurationException(
                            "Error configuring Authentication Mechanism "
                            + (String) _name
                            + ", "
                            + Configuration.ARGS_KEY
                            + " property is not a map");
                } else {
                    return (PluggableAuthenticationMechanism) Class
                            .forName((String) _clazz)
                            .getDeclaredConstructor(String.class, Map.class)
                            .newInstance((String) _name, (Map<?, ?>) _args);
                }
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new PluginConfigurationException(
                    "Error configuring Authentication Mechanism "
                    + (_name != null ? _name : ""), ex);
        }
    }

    /**
     * getIdentityManager
     *
     * @param conf the configuration Map
     * @return the PluggableIdentityManager
     */
    static PluggableIdentityManager createIdentityManager(
            Map<String, Object> conf)
            throws PluginConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new PluginConfigurationException(
                    "Error configuring Identity Manager, missing configuration");
        }

        Object _name = conf.get(Configuration.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new PluginConfigurationException(
                    "Error configuring Identity Manager, missing "
                    + Configuration.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(Configuration.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new PluginConfigurationException(
                    "Error configuring Identity Manager "
                    + (String) _name
                    + ", missing "
                    + Configuration.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(Configuration.ARGS_KEY);

        try {
            if (_args == null) {
                return (PluggableIdentityManager) Class.forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map<?, ?>)) {
                    throw new PluginConfigurationException(
                            "Error configuring Identity Manager "
                            + (String) _name
                            + ", "
                            + Configuration.ARGS_KEY
                            + " property is not a map");
                } else {
                    return (PluggableIdentityManager) Class.forName(
                            (String) _clazz)
                            .getDeclaredConstructor(String.class, Map.class)
                            .newInstance((String) _name, (Map<?, ?>) _args);
                }
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new PluginConfigurationException(
                    "Error configuring Identity Manager "
                    + (_name != null ? _name : ""), ex);

        }
    }

    /**
     * getAccessManager
     *
     * @return the PluggableAccessManager
     */
    static PluggableAccessManager createAccessManager(
            Map<String, Object> conf)
            throws PluginConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new PluginConfigurationException(
                    "Error configuring Access Manager, missing configuration");
        }

        Object _name = conf.get(Configuration.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new PluginConfigurationException(
                    "Error configuring Access Manager, missing "
                    + Configuration.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(Configuration.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new PluginConfigurationException(
                    "Error configuring Access Manager "
                    + (String) _name
                    + ", missing "
                    + Configuration.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(Configuration.ARGS_KEY);

        try {
            if (_args == null) {
                return (PluggableAccessManager) Class.forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map)) {
                    throw new PluginConfigurationException(
                            "Error configuring Access Manager "
                            + (String) _name
                            + ", property "
                            + Configuration.ARGS_KEY + " is not a map");
                } else {
                    return (PluggableAccessManager) Class.forName((String) _clazz)
                            .getDeclaredConstructor(String.class, Map.class)
                            .newInstance((String) _name, (Map<?, ?>) _args);
                }
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new PluginConfigurationException(
                    "Error configuring Access Manager"
                    + (_name != null ? _name : ""), ex);
        }
    }

    /**
     * getTokenManager
     *
     * @return the PluggableTokenManager
     */
    static PluggableTokenManager createTokenManager(
            Map<String, Object> conf)
            throws PluginConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new PluginConfigurationException(
                    "Error configuring Token Manager, missing configuration");
        }

        Object _name = conf.get(Configuration.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new PluginConfigurationException(
                    "Error configuring Token Manager, missing "
                    + Configuration.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(Configuration.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new PluginConfigurationException(
                    "Error configuring Token Manager "
                    + (String) _name
                    + ", missing "
                    + Configuration.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(Configuration.ARGS_KEY);

        try {
            if (_args == null) {
                return (PluggableTokenManager) Class.forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map)) {
                    throw new PluginConfigurationException(
                            "Error configuring Token Manager "
                            + (String) _name
                            + ", property "
                            + Configuration.ARGS_KEY + " is not a map");
                } else {
                    return (PluggableTokenManager) Class.forName((String) _clazz)
                            .getDeclaredConstructor(String.class, Map.class)
                            .newInstance((String) _name, (Map<?, ?>) _args);
                }
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new PluginConfigurationException(
                    "Error configuring Token Manager "
                    + (_name != null ? _name : ""), ex);
        }
    }

    /**
     * getService
     *
     * @return the PluggableService
     */
    static PluggableService createService(Map<String, Object> conf)
            throws PluginConfigurationException {
        Object _name = conf.get(Configuration.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new PluginConfigurationException(
                    "Error configuring Service, missing "
                    + Configuration.NAME_KEY
                    + " property");
        }

        Object _secured = conf.get(Configuration.SERVICE_SECURED_KEY);

        if (_secured == null || !(_secured instanceof Boolean)) {
            throw new PluginConfigurationException(
                    "Error configuring Service "
                    + (String) _name
                    + ", missing "
                    + Configuration.SERVICE_SECURED_KEY
                    + " property");
        }

        Object _uri = conf.get(Configuration.SERVICE_URI_KEY);

        if (_uri == null || !(_uri instanceof String)
                || !((String) _uri).startsWith("/")) {
            throw new PluginConfigurationException(
                    "Error configuring Service "
                    + (String) _name
                    + ", missing "
                    + Configuration.SERVICE_URI_KEY
                    + " property");
        }

        Object _clazz = conf.get(Configuration.CLASS_KEY);

        if (!(_clazz instanceof String)) {
            throw new PluginConfigurationException(
                    "Error configuring Service "
                    + (String) _name
                    + ", "
                    + Configuration.CLASS_KEY
                    + " property is not a String");
        }

        Object _args = conf.get(Configuration.ARGS_KEY);

        try {
            if (_args == null) {
                return (PluggableService) Class.forName((String) _clazz)
                        .getDeclaredConstructor(PipedHttpHandler.class,
                                String.class,
                                String.class,
                                Boolean.class)
                        .newInstance(null,
                                (String) _name,
                                (String) _uri,
                                (Boolean) _secured);
            } else {
                if (!(_args instanceof Map)) {
                    throw new PluginConfigurationException(
                            "Error configuring Service "
                            + (String) _name
                            + ", property "
                            + Configuration.ARGS_KEY + " is not a map");
                } else {
                    return (PluggableService) Class.forName((String) _clazz)
                            .getDeclaredConstructor(PipedHttpHandler.class,
                                    String.class,
                                    String.class,
                                    Boolean.class,
                                    Map.class)
                            .newInstance(null,
                                    (String) _name,
                                    (String) _uri,
                                    (Boolean) _secured,
                                    (Map<?, ?>) _args);
                }
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new PluginConfigurationException("Error configuring Service "
                    + (_name != null ? _name : ""), ex);
        }
    }
}
