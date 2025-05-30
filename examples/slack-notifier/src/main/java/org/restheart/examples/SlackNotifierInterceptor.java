/*-
 * ========================LICENSE_START=================================
 * slack-notifier
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

import static org.restheart.utils.GsonUtils.object;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import org.bson.json.JsonWriterSettings;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(
        name = "slackNotifierInterceptor",
        description = "sends a message to a slack channel upon document creation",
        interceptPoint = InterceptPoint.RESPONSE_ASYNC,
        enabledByDefault = true)
public class SlackNotifierInterceptor implements MongoInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlackNotifierInterceptor.class);

    private String channel = null;
    private String oauthToken = null;
    private String db = null;
    private String collection = null;

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() {
        this.channel = arg(config, "channel");
        this.oauthToken = arg(config, "oauth-token");
        this.db = arg(config, "db");
        this.collection = arg(config, "collection");

        LOGGER.info("SlackNotifierInterceptor initialized with db: {}, collection: {}", db, collection);
        LOGGER.debug("   Slack channel: {}, oauthToken: {}", channel, oauthToken);
    }

    @Override
    public void handle(MongoRequest mongoRequest, MongoResponse mongoResponse) throws IOException, InterruptedException {
        final var doc = mongoResponse.getDbOperationResult().getNewData();

        final var jsonObject = object()
                .put("text",
                        ":tada: New document\n```"
                        + doc.toJson(JsonWriterSettings.builder().indent(true).build())
                        + "```")
                .put("channel", this.channel)
                .get();

        final var json = jsonObject.toString();
        LOGGER.info("Body: \n{}", json);

        var httpClient = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

        var httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://slack.com/api/chat.postMessage"))
                .header("Authorization", "Bearer " + this.oauthToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(BodyPublishers.ofString(json))
                .build();

        final var httpResponse = httpClient.send(httpRequest, BodyHandlers.ofString());

        LOGGER.info("SlackNotifierInterceptor sent message to Slack channel: {} with status {}",
                        httpResponse.body(), httpResponse.statusCode());
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isPost() && request.isCollection()
                && this.collection.equals(request.getCollectionName())
                && this.db.equals(request.getDBName())
                && response.getDbOperationResult() != null
                && response.getDbOperationResult().getNewData() != null;
    }
}
