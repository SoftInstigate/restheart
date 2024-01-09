/*-
 * ========================LICENSE_START=================================
 * restheart-metrics
 * %%
 * Copyright (C) 2023 - 2024 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
