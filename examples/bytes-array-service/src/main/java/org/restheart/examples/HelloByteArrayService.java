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
        name = "hello",
        description = "Hello world example",
        enabledByDefault = true,
        defaultURI = "/hello")
public class HelloByteArrayService implements ByteArrayService {
    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        response.setContentType("text/plain; charset=utf-8");

        switch (request.getMethod()) {
            case OPTIONS -> handleOptions(request);
            case GET -> {
                var name = request.getQueryParameters().get("name");
                response.setContent("Hello, " + (name == null ? "Anonymous" : name.getFirst()));
            }

            case POST -> {
                var content = request.getContent();
                response.setContent("Hello, " + (content == null ? "Anonymous" : new String(content)));
            }

            default -> response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
