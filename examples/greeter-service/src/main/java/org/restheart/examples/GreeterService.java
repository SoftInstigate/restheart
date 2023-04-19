/*-
 * ========================LICENSE_START=================================
 * greeter-service
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

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import static org.restheart.utils.GsonUtils.object;

@RegisterPlugin(name = "greetings", description = "just another Hello World")
public class GreeterService implements JsonService {
    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        switch(req.getMethod()) {
            case GET -> res.setContent(object().put("message", "Hello World!"));
            case OPTIONS -> handleOptions(req);
            default -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }
}
