/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

import com.codahale.metrics.MetricRegistry;
import com.google.common.net.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import org.restheart.exchange.Request;
import org.restheart.plugins.security.Authenticator;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HttpString;

/**
 * Utility class for metrics
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Metrics {
    private static final HttpString _X_FORWARDED_FOR = HttpString.tryFromString(HttpHeaders.X_FORWARDED_FOR);

    public enum FAILED_AUTH_KEY { REMOTE_IP, X_FORWARDED_FOR }

    private static FAILED_AUTH_KEY collectFailedAuthBy = FAILED_AUTH_KEY.REMOTE_IP;
    private static int xffReverseIndex = 0;

     /**
     *
     * @param exchange
     * @return the name of the histogram that stores the percentage of failed auth requests in the last 10 seconds
     */
    public static String failedAuthHistogramName(HttpServerExchange exchange) {
        return switch(collectFailedAuthBy) {
            case REMOTE_IP -> MetricRegistry.name(Authenticator.class, "failed-auth-remote-ip", ExchangeAttributes.remoteIp().readAttribute(exchange));
            case X_FORWARDED_FOR -> {
                var xff = ExchangeAttributes.requestHeader(_X_FORWARDED_FOR).readAttribute(exchange);
                yield xff == null
                    ? MetricRegistry.name(Authenticator.class, "failed-auth-x-forwarded-for", "not-set")
                    : MetricRegistry.name(Authenticator.class, "failed-auth-x-forwarded-for", xffValue(xff, xffReverseIndex));
            }
        };
    }

    /**
     *
     * handles the case where the X_Forwarded_For header
     * is set as "<client-suppied-value>, ..., <proxy-supplied-value>"
     *
     * @param xff
     * @param rindex @see useXForwaderdedElement()
     * @return the rindex-th element of a comma separated list
     */
    public static String xffValue(String xff, int rindex) {
        if (xff == null) {
            return null;
        } else {
            xff = xff.strip();

            if (xff.startsWith("[")) {
                xff = xff.substring(1);
            }

            if (xff.endsWith("]")) {
                xff = xff.substring(0, xff.length()-1);
            }

            var elements = xff.split(",");

            if (elements.length >= rindex) {
                if (rindex >= elements.length) {
                    return elements[0].strip();
                } else {
                    return elements[elements.length - 1 - rindex].strip();
                }
            } else {
                return elements[elements.length - 1].strip();
            }
        }
    }

    /**
     * Set the key used to collect failed auth requests, REMOTE_IP (default) or X_FORWARDED_FOR
     *
     * @param key the key to use
     */
    public static void collectFailedAuthBy(FAILED_AUTH_KEY key) {
        collectFailedAuthBy = key;
    }

    /**
     * Set the xffReverseIndex, i.e. if X-ForwardedFor header has multiple values,
     * take into account the n-th value from last
     * e.g. with [x.x.x.x, y.y.y.y., z.z.z.z, k.k.k.k]
     * 0 -> k.k.k.k
     * 2 -> y.y.y.y
     *
     * @param ridx the reverse index (element from last)
     */
    public static void xffValueRIndex(int ridx) {
        xffReverseIndex = ridx;
    }


    private static final AttachmentKey<List<MetricLabel>> CUSTOM_METRIC_LABELS = AttachmentKey.create(List.class);

    /**
     * attach metrics labels to request
     *
     * RequestsMetricsCollector adds labels to the collected metrics
     *
     * @param request
     * @param labels
     */
    public static void attachMetricLabels(Request<?> request, List<MetricLabel> labels) {
        request.getExchange().putAttachment(CUSTOM_METRIC_LABELS, labels);
    }

    /**
     * attach metrics labels to request
     *
     * RequestsMetricsCollector adds labels to the collected metrics
     *
     * @param request
     * @param label
     */
    public static void attachMetricLabel(Request<?> request, MetricLabel label) {
        var labels = new ArrayList<MetricLabel>();
        labels.add(label);
        request.getExchange().putAttachment(CUSTOM_METRIC_LABELS, labels);
    }

    /**
     * retrives the metrics labels attached to request
     *
     * @param request
     * @return
     */
    public static List<MetricLabel> getMetricLabels(Request<?> request) {
        return request.getExchange().getAttachment(CUSTOM_METRIC_LABELS);
    }
}
