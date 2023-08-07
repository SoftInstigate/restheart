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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * record for metric name and labels that can be serialized/deserialized to/from string
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public record MetricNameAndLabels(String name, List<MetricLabel> lables) {
    public static String SEPARATOR = ".";
    private static String SEPARATOR_REGEX = "\\.";

    public MetricNameAndLabels(String name, List<MetricLabel> lables) {
        this.name = name.replaceAll("SEPARATOR_REGEX", "_");
        this.lables = lables;
    }

    public static MetricNameAndLabels from(String raw) {
        var name = raw.substring(0, raw.indexOf("."));

        var labels = Arrays.stream(raw.split(SEPARATOR_REGEX))
            .skip(1)
            .map(l -> MetricLabel.from(l))
            .collect(Collectors.toList());

        return new MetricNameAndLabels(name, labels);
    }

    public String toString() {
        var sb = new StringBuilder();
        sb.append(name).append(SEPARATOR);

        sb.append(lables.stream().map(l -> l.toString()).collect(Collectors.joining(SEPARATOR)));
        return sb.toString();
    }
}
