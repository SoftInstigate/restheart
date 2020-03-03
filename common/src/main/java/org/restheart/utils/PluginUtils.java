/*
 * RESTHeart Common
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
package org.restheart.utils;

import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.InterceptPoint;
import org.restheart.plugins.security.Interceptor;
import org.restheart.plugins.security.Service;

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
    
    public static boolean requiresContent(Interceptor interceptor) {
        var a = interceptor.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);
        
        if (a == null) {
            return false;
        } else {
            return a.requiresContent();
        }
    }
    
    public static String defaultURI(Service service) {
        var a = service.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);
        
        return a == null ? null : a.defaultURI();
    }
}
