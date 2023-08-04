package org.restheart.metrics;

import java.util.ArrayDeque;
import java.util.Deque;
import org.bson.BsonDocument;
import org.bson.json.JsonWriterSettings;
import org.restheart.utils.BsonUtils;
import static org.restheart.utils.BsonUtils.document;

/**
 * utility record for metric labels that can be serialized/deserialized to string
 */
public record MetricLabel(String name, String value) {
    private static JsonWriterSettings jsonWriterSettings =  JsonWriterSettings.builder().indent(false).build();

    public BsonDocument bson() {
        return document().put("n", name).put("v", value).get();
    }

    public static MetricLabel fromJson(BsonDocument raw) {
        return new MetricLabel(raw.getString("n").getValue(), raw.getString("v").getValue());
    }

    public String toString() {
        return BsonUtils.minify(bson().toJson(jsonWriterSettings));
    }

    public static MetricLabel from(String raw) {
        return fromJson(BsonUtils.parse(raw).asDocument());
    }

    public static Deque<MetricLabel> from(MetricLabel... labels) {
        var ret = new ArrayDeque<MetricLabel>();
        for (var label: labels) {
            ret.add(label);
        }
        return ret;
    }
}