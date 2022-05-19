package org.restheart.examples;

import java.util.Map;
import com.mashape.unirest.http.Unirest;

import org.bson.json.JsonWriterSettings;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import static org.restheart.utils.GsonUtils.object;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
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

    @InjectConfiguration
    public void init(Map<String, Object> config) {
        this.channel = argValue(config, "channel");
        this.oauthToken = argValue(config, "oauth-token");
        this.db = argValue(config, "db");
        this.collection = argValue(config, "collection");

        LOGGER.debug("SlackNotifierInterceptor initialized with channel: {}, oauthToken: {}, db: {}, collection: {}", channel, oauthToken, db, collection);
    }
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var doc = response.getDbOperationResult().getNewData();

        var body = object()
            .put("text", ":tada: New document\n```" + doc.toJson(JsonWriterSettings.builder().indent(true).build()) + "```")
            .put("channel", this.channel);

        var resp = Unirest.post("https://slack.com/api/chat.postMessage")
            .header("Authorization", "Bearer " + this.oauthToken)
            .header("Content-Type", "application/json")
            .body(body)
            .asJson();

        LOGGER.debug("SlackNotifierInterceptor sent message to slack channel: {} with status {}", resp.getBody(), resp.getStatus());
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
