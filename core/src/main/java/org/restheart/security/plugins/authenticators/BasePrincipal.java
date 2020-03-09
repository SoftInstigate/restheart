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
package org.restheart.security.plugins.authenticators;

import java.security.Principal;

/**
 * Base concrete Principal implmentation
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BasePrincipal implements Principal {

    private String name;

    /**
     *
     * @param name
     */
    public BasePrincipal(String name) {
        if (name == null) {
            throw new IllegalArgumentException("argument name cannot be null");
        }

        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
