/*-
 * ========================LICENSE_START=================================
 * bytes-array-service
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

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

@RegisterPlugin(
        name = "textGreeter",
        description = "just another text/plain Hello World")
public class TextGreeterService implements ByteArrayService {
    @Override
    public void handle(ByteArrayRequest req, ByteArrayResponse res) {
        res.setContentType("text/plain; charset=utf-8");

        switch (req.getMethod()) {
            case OPTIONS -> handleOptions(req);

            case GET -> {
                var name = req.getQueryParameters().get("name");
                res.setContent("Hello, " + (name == null ? "World" : name.getFirst()));
            }

            case POST -> {
                var content = req.getContent();
                res.setContent("Hello, " + (content == null ? "World" : new String(content)));
            }

            default -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }
}
