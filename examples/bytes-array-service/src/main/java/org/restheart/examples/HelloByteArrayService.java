/*-
 * ========================LICENSE_START=================================
 * bytes-array-service
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

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(
        name = "hello",
        description = "Hello world example",
        enabledByDefault = true,
        defaultURI = "/hello")
public class HelloByteArrayService implements ByteArrayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HelloByteArrayService.class);

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        String responseMessage = "Hello, Anonymous";

        if (request.isGet()) {
            // If it's a GET look for a "name" query string parameter
            LOGGER.debug("### QueryParameters: '{}'", request.getQueryParameters());
            if (request.getQueryParameters().get("name") != null) {
                responseMessage = "Hello, " + request.getQueryParameters().get("name").getFirst();
            }
            response.setStatusCode(HttpStatus.SC_OK);
        } else if (request.isPost()) {
            // If it's a POST look into the request body
            String requestBody = new String(request.getContent());
            LOGGER.debug("### requestBody: '{}'", requestBody);
            if (!requestBody.isBlank()) {
                responseMessage = "Hello, " + requestBody;
            }
            response.setStatusCode(HttpStatus.SC_OK);
        } else {
            // Any other HTTP verb is a bad request
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
        LOGGER.debug("### responseMessage: '{}'", responseMessage);
        response.setContentType("text/plain; charset=utf-8");
        response.setContent(responseMessage.getBytes());
    }

}
