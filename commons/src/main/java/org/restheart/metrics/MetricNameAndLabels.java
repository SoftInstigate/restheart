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

import org.bson.BsonDocument;
import org.bson.json.JsonWriterSettings;
import org.restheart.utils.BsonUtils;

import static org.restheart.utils.BsonUtils.document;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Collectors;

import static org.restheart.utils.BsonUtils.array;

/**
 * record for metric name and labels that can be serialized/deserialized to/from string
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public record MetricNameAndLabels(String name, Deque<MetricLabel> lables) {
    private static JsonWriterSettings jsonWriterSettings =  JsonWriterSettings.builder().indent(false).build();

    public BsonDocument bson() {
        var _labels = array();
        var ret = document().put("l", _labels).put("n", name());

        lables().stream().map(MetricLabel::bson).forEachOrdered(_labels::add);

        return ret.get();
    }

    public static MetricNameAndLabels fromJson(BsonDocument raw) {
        var _labels = raw.getArray("l").stream()
            .map(v -> v.asDocument())
            .map(d -> MetricLabel.fromJson(d))
            .collect(Collectors.toList());

        var labels = new ArrayDeque<MetricLabel>(_labels);

        return new MetricNameAndLabels(raw.getString("n").getValue(), labels);
    }

    public String toString() {
        return BsonUtils.minify(bson().toJson(jsonWriterSettings));
    }

    public static MetricNameAndLabels fromString(String raw) {
        return fromJson(BsonUtils.parse(raw).asDocument());
    }
}
