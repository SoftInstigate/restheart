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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uiam.Bootstrapper;
import io.uiam.cache.Cache;
import io.uiam.cache.CacheFactory;
import io.uiam.cache.LoadingCache;
import io.uiam.plugins.authentication.PluggableIdentityManager;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class IDMCacheSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(IDMCacheSingleton.class);

    private static final LoadingCache<String, PluggableIdentityManager> IDENTITY_MANAGERS_CACHE
            = CacheFactory.createLocalLoadingCache(
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER,
                    -1,
                    name -> {
                        var idmsConf = Bootstrapper.getConfiguration().getIdms();

                        var idmConf = idmsConf.stream()
                                .filter(idm -> name.equals(idm.get("name")))
                                .findFirst();

                        if (idmConf.isPresent()) {
                            try {
                                return PluginsFactory.getIdentityManager(idmConf.get());
                            } catch (PluginConfigurationException pcex) {
                                throw new IllegalStateException(pcex.getMessage(), 
                                        pcex);
                            }
                        } else {
                            var errorMsg = "Identity Manager " + name
                            + " not found.";
                            
                            throw new IllegalStateException(errorMsg,
                                    new PluginConfigurationException(errorMsg));
                        }
                    });

    private static IDMCacheSingleton HOLDER;

    public static synchronized IDMCacheSingleton getInstance() {
        if (HOLDER == null) {
            HOLDER = new IDMCacheSingleton();
        }

        return HOLDER;
    }

    @SuppressWarnings("unchecked")
    private IDMCacheSingleton() {
    }

    public PluggableIdentityManager getIdentityManager(String name) throws PluginConfigurationException {
        Optional<PluggableIdentityManager> op = IDENTITY_MANAGERS_CACHE.getLoading(name);

        if (op == null) {
            throw new PluginConfigurationException("No Identity Manager configured with name: " + name);
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new PluginConfigurationException("No Identity Manager configured with name: " + name);
        }
    }
}
