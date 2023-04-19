/*-
 * ========================LICENSE_START=================================
 * instance-name
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.examples;

import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.Inject;
import org.restheart.configuration.Configuration;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import io.undertow.util.HttpString;

@RegisterPlugin(name = "instanceNameInterceptor",
                description = "Add the X-Restheart-Instance response header",
                enabledByDefault = true)
public class InstanceNameInterceptor implements MongoInterceptor {
    public static final HttpString X_RESTHEART_INSTANCE_HEADER = HttpString.tryFromString("X-Restheart-Instance");

    private String instanceName;

    @Inject("rh-config")
    private Configuration config;

    @OnInit
    public void init() {
        this.instanceName = config.coreModule().name();
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) {
        response.getHeaders().put(X_RESTHEART_INSTANCE_HEADER, this.instanceName);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return true;
    }
}
