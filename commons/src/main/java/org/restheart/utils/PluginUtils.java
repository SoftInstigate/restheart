/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
package org.restheart.utils;

import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PluginUtils {
    public static InterceptPoint interceptPoint(Interceptor interceptor) {
        var a = interceptor.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return null;
        } else {
            return a.interceptPoint();
        }
    }

    public static InitPoint initPoint(Initializer initializer) {
        var a = initializer.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return null;
        } else {
            return a.initPoint();
        }
    }

    public static boolean requiresContent(Interceptor interceptor) {
        var a = interceptor.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return false;
        } else {
            return a.requiresContent();
        }
    }

    /**
     *
     * @param service
     * @return the service default URI. If not explicitly set via defaulUri
     * attribute, it is /[service-name]
     */
    public static String defaultURI(Service service) {
        var a = service.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);

        return a == null
                ? null
                : a.defaultURI() == null || "".equals(a.defaultURI())
                ? "/".concat(a.name())
                : a.defaultURI();
    }

    /**
     *
     * @param registry the PluginsRegistry
     * @param serviceName
     * @return the service default URI. If not explicitly set via defaulUri
     * attribute, it is /[service-name]
     */
    public static String defaultURI(PluginsRegistry registry,
            String serviceName) {
        var service = registry.getServices().stream()
                .filter(s -> serviceName.equals(s.getName()))
                .findFirst();

        if (service != null && service.isPresent()) {

            return defaultURI(service.get().getInstance());
        } else {
            return null;
        }
    }
}
