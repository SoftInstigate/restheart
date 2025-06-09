/*-
 * ========================LICENSE_START=================================
 * form-handler
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.JsonResponse;
import org.restheart.exchange.ServiceRequest;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import static org.restheart.utils.GsonUtils.object;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormParserFactory;

@RegisterPlugin(name = "formHandler", description = "handle a form post using FormDataParser")
public class FormHandler implements Service<FormRequest, JsonResponse> {
    @Override
    public void handle(FormRequest req, JsonResponse res) {
        switch(req.getMethod()) {
            case POST ->  {
                var out = object();
                var formData = req.getContent();

                formData.forEach(field -> out.put(field, formData.getFirst(field).getValue()));

                res.setContent(out);
            }
            case OPTIONS -> handleOptions(req);
            default -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Override
    public Consumer<HttpServerExchange> requestInitializer() {
        return e -> FormRequest.init(e);
    }

    @Override
    public Consumer<HttpServerExchange> responseInitializer() {
        return e -> JsonResponse.init(e);
    }

    @Override
    public Function<HttpServerExchange, FormRequest> request() {
        return e -> FormRequest.of(e);
    }

    @Override
    public Function<HttpServerExchange, JsonResponse> response() {
        return e -> JsonResponse.of(e);
    }
}

class FormRequest extends ServiceRequest<FormData> {

    private FormRequest(HttpServerExchange exchange) {
        super(exchange);
    }

    public static FormRequest init(HttpServerExchange exchange) {
        return new FormRequest(exchange);
    }

    public static FormRequest of(HttpServerExchange exchange) {
        return of(exchange, FormRequest.class);
    }

    private static final FormParserFactory builder = FormParserFactory.builder().build();

    @Override
    public FormData parseContent() throws IOException, BadRequestException {
        var parser = builder.createParser(getExchange());

        if (parser == null) {
            throw new IOException("Not a form.");
        }

        return parser.parseBlocking();
    }
}
