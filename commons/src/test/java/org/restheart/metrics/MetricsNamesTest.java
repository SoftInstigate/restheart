/*-
 * ========================LICENSE_START=================================
 * restheart-metrics
 * %%
 * Copyright (C) 2023 - 2026 SoftInstigate
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetricsNamesTest {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsNamesTest.class);

    @Test
    public void testMetricLabelSerialization() {
        var labelFromConstructor = new MetricLabel("method", "GET");

        var labelAsString = labelFromConstructor.toString();

        LOG.debug("string representation {}", labelAsString);

        var labelFromString = MetricLabel.from(labelAsString);

        assertEquals(labelFromConstructor, labelFromString);
    }

    @Test
    public void testMetricNameAndLabelsSerialization() {
        var labels = new ArrayList<MetricLabel>();
        labels.add(new MetricLabel("method", "GET"));
        labels.add(new MetricLabel("status", "200"));

        var nameAndLabelsFromConstructor = new MetricNameAndLabels("foo", labels);

        var nameAndLabelsAsString = nameAndLabelsFromConstructor.toString();

        LOG.debug("string representation {}", nameAndLabelsAsString);

        var nameAndLabelsFromString = MetricNameAndLabels.from(nameAndLabelsAsString);

        assertEquals(nameAndLabelsFromConstructor, nameAndLabelsFromString);
    }
}
