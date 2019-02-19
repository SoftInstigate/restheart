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

import java.util.Optional;

import io.uiam.Bootstrapper;
import io.uiam.cache.Cache;
import io.uiam.cache.CacheFactory;
import io.uiam.cache.LoadingCache;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.uiam.plugins.authentication.PluggableIdentityManager;
import io.uiam.plugins.authentication.PluggableTokenManager;
import io.uiam.plugins.authorization.PluggableAccessManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import io.uiam.plugins.interceptors.PluggableRequestInterceptor;
import io.uiam.plugins.interceptors.PluggableResponseInterceptor;
import io.uiam.plugins.service.PluggableService;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginsRegistry {

    private static final String AUTH_TOKEN_MANAGER_NAME = "@@authTokenManager";
    private static final String ACCESS_MANAGER_NAME = "@@accessManager";

    private static final LoadingCache<String, PluggableIdentityManager> IDMS_CACHE
            = CacheFactory.createLocalLoadingCache(
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var idmsConf = Bootstrapper.getConfiguration().getIdms();

                        var idmConf = idmsConf.stream().filter(idm -> name
                        .equals(idm.get("name")))
                                .findFirst();

                        if (idmConf.isPresent()) {
                            try {
                                return PluginsFactory
                                        .createIdentityManager(idmConf.get());
                            } catch (PluginConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Identity Manager "
                            + name
                            + " not found.";

                            throw new IllegalStateException(errorMsg,
                                    new PluginConfigurationException(errorMsg));
                        }
                    });
    
    private static final LoadingCache<String, PluggableAuthenticationMechanism> AUTH_MECHANISMS_CACHE
            = CacheFactory.createLocalLoadingCache(
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var amsConf = Bootstrapper.getConfiguration().getAuthMechanisms();

                        var amConf = amsConf.stream().filter(idm -> name
                        .equals(idm.get("name")))
                                .findFirst();

                        if (amConf.isPresent()) {
                            try {
                                return PluginsFactory
                                        .createAutenticationMechanism(amConf.get());
                            } catch (PluginConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Authentication Mechanism "
                            + name
                            + " not found.";

                            throw new IllegalStateException(errorMsg,
                                    new PluginConfigurationException(errorMsg));
                        }
                    });
    
    private static final LoadingCache<String, PluggableAccessManager> ACCESS_MANAGERS_CACHE
            = CacheFactory.createLocalLoadingCache(
                    1,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var amConf = Bootstrapper.getConfiguration().getAccessManager();

                        if (amConf != null) {
                            try {
                                return PluginsFactory
                                        .createAccessManager(amConf);
                            } catch (PluginConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Access Manager "
                            + " not configured.";

                            throw new IllegalStateException(errorMsg,
                                    new PluginConfigurationException(errorMsg));
                        }
                    });
    
    private static final LoadingCache<String, PluggableService> SERVICES_CACHE
            = CacheFactory.createLocalLoadingCache(
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var srvsConf = Bootstrapper.getConfiguration()
                                .getServices();

                        var srvConf = srvsConf.stream().filter(idm -> name
                        .equals(idm.get("name")))
                                .findFirst();

                        if (srvConf.isPresent()) {
                            try {
                                return PluginsFactory
                                        .createService(srvConf.get());
                            } catch (PluginConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Service "
                            + name
                            + " not found.";

                            throw new IllegalStateException(errorMsg,
                                    new PluginConfigurationException(errorMsg));
                        }
                    });


    private static final List<PluggableRequestInterceptor> REQUEST_INTERCEPTORS
            = Collections.synchronizedList(new ArrayList<>());

    private static final List<PluggableResponseInterceptor> RESPONSE_INTECEPTORS
            = Collections.synchronizedList(new ArrayList<>());

    private static PluginsRegistry HOLDER;

    public static synchronized PluginsRegistry getInstance() {
        if (HOLDER == null) {
            HOLDER = new PluginsRegistry();
        }

        return HOLDER;
    }

    private PluginsRegistry() {
    }

    public PluggableIdentityManager getIdentityManager(String name)
            throws PluginConfigurationException {
        Optional<PluggableIdentityManager> op = IDMS_CACHE
                .getLoading(name);

        if (op == null) {
            throw new PluginConfigurationException(
                    "No Identity Manager configured with name: " + name);
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new PluginConfigurationException(
                    "No Identity Manager configured with name: " + name);
        }
    }
    
    public PluggableAuthenticationMechanism getAuthenticationMechanism(String name)
            throws PluginConfigurationException {
        Optional<PluggableAuthenticationMechanism> op = AUTH_MECHANISMS_CACHE
                .getLoading(name);

        if (op == null) {
            throw new PluginConfigurationException(
                    "No Authentication Mechanism configured with name: " + name);
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new PluginConfigurationException(
                    "No Authentication Mechanism configured with name: " + name);
        }
    }
    
    public PluggableService getService(String name)
            throws PluginConfigurationException {
        Optional<PluggableService> op = SERVICES_CACHE
                .getLoading(name);

        if (op == null) {
            throw new PluginConfigurationException(
                    "No Service configured with name: " + name);
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new PluginConfigurationException(
                    "No Service configured with name: " + name);
        }
    }
    
    public PluggableAccessManager getAccessManager()
            throws PluginConfigurationException {
        Optional<PluggableAccessManager> op = ACCESS_MANAGERS_CACHE
                .getLoading(ACCESS_MANAGER_NAME);

        if (op == null) {
            throw new PluginConfigurationException(
                    "No Access Manager configured");
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new PluginConfigurationException(
                    "No Access Mechanism configured");
        }
    }
    
    public PluggableTokenManager getTokenManager()
            throws PluginConfigurationException {
        Optional<PluggableIdentityManager> op = IDMS_CACHE
                .get(AUTH_TOKEN_MANAGER_NAME);

        if (op == null) {
            PluggableIdentityManager atm = PluginsFactory
                    .createTokenManager(Bootstrapper.getConfiguration()
                            .getTokenManager());

            IDMS_CACHE.put(AUTH_TOKEN_MANAGER_NAME, atm);
            return (PluggableTokenManager) atm;
        }

        if (op.isPresent()) {
            return (PluggableTokenManager) op.get();
        } else {
            throw new PluginConfigurationException(
                    "No Auth Token Manager configured");
        }
    }

    public List<PluggableRequestInterceptor> getRequestInterceptors() {
        return REQUEST_INTERCEPTORS;
    }

    public List<PluggableResponseInterceptor> getResponseInterceptors() {
        return RESPONSE_INTECEPTORS;
    }
}
