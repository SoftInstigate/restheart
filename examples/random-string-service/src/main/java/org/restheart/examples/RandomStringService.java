/*-
 * ========================LICENSE_START=================================
 * random-string-service
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

import org.apache.commons.lang3.RandomStringUtils;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

@RegisterPlugin(
        name = "randomStringService",
        description = "returns a random string",
        enabledByDefault = true,
        defaultURI = "/rndStr")
public class RandomStringService implements ByteArrayService {

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        if (request.isGet()) {
            var rnd = RandomStringUtils.randomAlphabetic(10);

            response.setContent(rnd.getBytes());
            response.setContentType("application/txt");
            response.setStatusCode(HttpStatus.SC_OK);
        } else {
            // Any other HTTP verb is a bad request
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
