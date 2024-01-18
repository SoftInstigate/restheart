/*-
 * ========================LICENSE_START=================================
 * greeter-service
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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
import static org.restheart.utils.GsonUtils.object;
import org.restheart.utils.HttpStatus;

import com.google.gson.JsonElement;

@RegisterPlugin(name = "jsonGreeter", description = "just another JSON Hello World")
public class JsonGreeterService implements JsonService {
    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        // JsonService defaults response Content-Type application/json, so no need to call res.setConentType()
        switch(req.getMethod()) {
            case OPTIONS -> handleOptions(req);

            case GET -> {
                var name = req.getQueryParameters().get("name");
                res.setContent((name == null
                    ? object().put("message", "Hello, World!")
                    : object().put("message", "Hello, " + name.getFirst())));
            }

            case POST -> {
                var name = nameFromRequest(req.getContent());
                res.setContent(name == null
                    ? object().put("message", "Hello, World!")
                    : object().put("message", "Hello, " + name));
            }

            default -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    /**
     *
     * @param content
     * @return the value of name propery or null if missing
     */
    private String nameFromRequest(JsonElement content) {
        return content == null || !content.isJsonObject() || !content.getAsJsonObject().has("name") | !content.getAsJsonObject().get("name").isJsonPrimitive()
         ? null
         : content.getAsJsonObject().get("name").getAsString();
    }
}
