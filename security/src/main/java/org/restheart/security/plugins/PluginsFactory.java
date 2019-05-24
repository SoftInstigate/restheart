/*
 * RESTHeart Security
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
package org.restheart.security.plugins;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.restheart.security.ConfigurationException;
import org.restheart.security.ConfigurationKeys;
import org.restheart.security.handlers.PipedHttpHandler;

/**
 * 
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PluginsFactory {
    /**
     * getAutenticationMechanism
     *
     * @param conf the configuration Map
     * @return the AuthenticatorMechanism
     */
    static AuthMechanism createAutenticationMechanism(
            Map<String, Object> conf)
            throws ConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new ConfigurationException(
                    "Error configuring Authentication Mechanism,"
                    + " missing configuration");
        }

        Object _name = conf.get(ConfigurationKeys.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Authentication Mechanism, missing "
                    + ConfigurationKeys.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(ConfigurationKeys.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Authentication Mechanism "
                    + (String) _name
                    + ", missing "
                    + ConfigurationKeys.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(ConfigurationKeys.ARGS_KEY);

        try {
            if (_args == null) {
                return (AuthMechanism) Class
                        .forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map)) {
                    throw new ConfigurationException(
                            "Error configuring Authentication Mechanism "
                            + (String) _name
                            + ", "
                            + ConfigurationKeys.ARGS_KEY
                            + " property is not a map");
                } else {
                    return (AuthMechanism) Class
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
            throw new ConfigurationException(
                    "Error configuring Authentication Mechanism "
                    + (_name != null ? _name : ""), ex);
        }
    }

    /**
     * createAuthenticator
     *
     * @param conf the configuration Map
     * @return the Authenticator
     */
    static Authenticator createAuthenticator(
            Map<String, Object> conf)
            throws ConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new ConfigurationException(
                    "Error configuring Authenticator, missing configuration");
        }

        Object _name = conf.get(ConfigurationKeys.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Authenticator, missing "
                    + ConfigurationKeys.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(ConfigurationKeys.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Authenticator "
                    + (String) _name
                    + ", missing "
                    + ConfigurationKeys.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(ConfigurationKeys.ARGS_KEY);

        try {
            if (_args == null) {
                return (Authenticator) Class.forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map<?, ?>)) {
                    throw new ConfigurationException(
                            "Error configuring Authenticator "
                            + (String) _name
                            + ", "
                            + ConfigurationKeys.ARGS_KEY
                            + " property is not a map");
                } else {
                    return (Authenticator) Class.forName(
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
            throw new ConfigurationException(
                    "Error configuring Authenticator "
                    + (_name != null ? _name : ""), ex);

        }
    }

    /**
     * getAccessManager
     *
     * @return the Authorizer
     */
    static Authorizer createAuthorizer(
            Map<String, Object> conf)
            throws ConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new ConfigurationException(
                    "Error configuring Authorizer, missing configuration");
        }

        Object _name = conf.get(ConfigurationKeys.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Authorizer, missing "
                    + ConfigurationKeys.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(ConfigurationKeys.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Authorizer "
                    + (String) _name
                    + ", missing "
                    + ConfigurationKeys.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(ConfigurationKeys.ARGS_KEY);

        try {
            if (_args == null) {
                return (Authorizer) Class.forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map)) {
                    throw new ConfigurationException(
                            "Error configuring Authorizer "
                            + (String) _name
                            + ", property "
                            + ConfigurationKeys.ARGS_KEY + " is not a map");
                } else {
                    return (Authorizer) Class.forName((String) _clazz)
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
            throw new ConfigurationException(
                    "Error configuring Authorizer "
                    + (_name != null ? _name : ""), ex);
        }
    }

    /**
     * createTokenManager
     *
     * @return the TokenManager
     */
    static TokenManager createTokenManager(
            Map<String, Object> conf)
            throws ConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new ConfigurationException(
                    "Error configuring Token Manager, missing configuration");
        }

        Object _name = conf.get(ConfigurationKeys.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Token Manager, missing "
                    + ConfigurationKeys.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(ConfigurationKeys.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Token Manager "
                    + (String) _name
                    + ", missing "
                    + ConfigurationKeys.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(ConfigurationKeys.ARGS_KEY);

        try {
            if (_args == null) {
                return (TokenManager) Class.forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map)) {
                    throw new ConfigurationException(
                            "Error configuring Token Manager "
                            + (String) _name
                            + ", property "
                            + ConfigurationKeys.ARGS_KEY + " is not a map");
                } else {
                    return (TokenManager) Class.forName((String) _clazz)
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
            throw new ConfigurationException(
                    "Error configuring Token Manager "
                    + (_name != null ? _name : ""), ex);
        }
    }

    /**
     * getService
     *
     * @return the Service
     */
    static Service createService(Map<String, Object> conf)
            throws ConfigurationException {
        Object _name = conf.get(ConfigurationKeys.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Service, missing "
                    + ConfigurationKeys.NAME_KEY
                    + " property");
        }

        Object _secured = conf.get(ConfigurationKeys.SERVICE_SECURED_KEY);

        if (_secured == null || !(_secured instanceof Boolean)) {
            throw new ConfigurationException(
                    "Error configuring Service "
                    + (String) _name
                    + ", missing "
                    + ConfigurationKeys.SERVICE_SECURED_KEY
                    + " property");
        }

        Object _uri = conf.get(ConfigurationKeys.SERVICE_URI_KEY);

        if (_uri == null || !(_uri instanceof String)
                || !((String) _uri).startsWith("/")) {
            throw new ConfigurationException(
                    "Error configuring Service "
                    + (String) _name
                    + ", missing "
                    + ConfigurationKeys.SERVICE_URI_KEY
                    + " property");
        }

        Object _clazz = conf.get(ConfigurationKeys.CLASS_KEY);

        if (!(_clazz instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Service "
                    + (String) _name
                    + ", "
                    + ConfigurationKeys.CLASS_KEY
                    + " property is not a String");
        }

        Object _args = conf.get(ConfigurationKeys.ARGS_KEY);

        try {
            if (_args == null) {
                return (Service) Class.forName((String) _clazz)
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
                    throw new ConfigurationException(
                            "Error configuring Service "
                            + (String) _name
                            + ", property "
                            + ConfigurationKeys.ARGS_KEY + " is not a map");
                } else {
                    return (Service) Class.forName((String) _clazz)
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
            throw new ConfigurationException("Error configuring Service "
                    + (_name != null ? _name : ""), ex);
        }
    }

    /**
     * TODO move to PluginFactory
     *
     * @param configuration
     */
    static Initializer createInitializer(String initializerClass)
            throws ConfigurationException {
        try {
            Object o = Class.forName(initializerClass)
                    .getDeclaredConstructor().newInstance();

            if (o instanceof Initializer) {
                return ((Initializer) o);
            } else throw new ConfigurationException(
                    "Error configuring Initializer "
                    + initializerClass 
                    + " it does not implement Initializer interface");
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | InvocationTargetException
                | InstantiationException
                | IllegalAccessException ex) {
            throw new ConfigurationException(
                    "Error configuring Initializer "
                    + initializerClass, ex);
        }
    }
}
