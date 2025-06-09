/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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
package org.restheart.exchange;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GraphQLResponse extends MongoResponse {
    public static final String GRAPHQL_RESPONSE_CONTENT_TYPE = "application/graphql-response+json";

    protected GraphQLResponse(HttpServerExchange exchange) {
        super(exchange);
        // set response content type as application/graphql-response+json
        setContentType(GRAPHQL_RESPONSE_CONTENT_TYPE);
    }

    public static GraphQLResponse init(HttpServerExchange exchange) {
        return new GraphQLResponse(exchange);
    }

    public static GraphQLResponse of(HttpServerExchange exchange) {
        return GraphQLResponse.of(exchange, GraphQLResponse.class);
    }
}
