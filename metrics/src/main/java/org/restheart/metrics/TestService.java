/*-
 * ========================LICENSE_START=================================
 * restheart-metrics
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.RegisterPlugin;
import static org.restheart.utils.BsonUtils.document;
import java.util.ArrayDeque;

//TODO delete me
@RegisterPlugin(name="testService", description = "a simple service with custom metrics", enabledByDefault = true)
public class TestService implements BsonService {
    public void handle(final BsonRequest request, final BsonResponse response) {
        response.setContent(document().put("foo", "bar"));

        var labels = new ArrayDeque<MetricLabel>();
        labels.add(new MetricLabel("message", "i'm alive and i want you"));
        Metrics.attachMetricLabels(request, labels);
    }
}
