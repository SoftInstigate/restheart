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
package org.restheart.security.plugins.services;

import java.util.Map;
import org.restheart.plugins.RegisterPlugin;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "secho",
        description = "only used for automatic testing purposes",
        enabledByDefault = false)
public class SEchoService extends EchoService {

    /**
     *
     * @param args
     */
    public SEchoService(Map<String, Object> args) {
        super(args);
    }

    @Override
    public String defaultUri() {
        return "secho";
    }
}
