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

import java.util.ArrayList;
import java.util.List;

/**
 * record for metric labels that can be serialized/deserialized to/from string
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public record MetricLabel(String name, String value) {
    public static String SEPARATOR = "=";

    public MetricLabel(String name, String value) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }

        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }

        this.name = name.replaceAll("=", "_").replaceAll("\\.", "_");
        this.value = value.replaceAll("\\.", "_");
    }


    public String toString() {
        return name.concat(SEPARATOR).concat(value);
    }

    public static MetricLabel from(String raw) {
        var sepIdx = raw.indexOf(SEPARATOR);
        return new MetricLabel(raw.substring(0, sepIdx), raw.substring(sepIdx+1));
    }

    public static List<MetricLabel> collect(MetricLabel... labels) {
        var ret = new ArrayList<MetricLabel>();
        for (var label: labels) {
            ret.add(label);
        }
        return ret;
    }
}