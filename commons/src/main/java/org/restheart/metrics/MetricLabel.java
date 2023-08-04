/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
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

package org.restheart.metrics;

import java.util.ArrayDeque;
import java.util.Deque;
import org.bson.BsonDocument;
import org.bson.json.JsonWriterSettings;
import org.restheart.utils.BsonUtils;
import static org.restheart.utils.BsonUtils.document;

/**
 * record for metric labels that can be serialized/deserialized to/from string
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
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