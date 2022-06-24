/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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
package org.restheart.utils;

import com.codahale.metrics.MetricRegistry;
import com.google.common.net.HttpHeaders;

import org.restheart.plugins.security.Authenticator;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Utility class for metrics
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MetricsUtils {
    private static final HttpString _X_FORWARDED_FOR = HttpString.tryFromString(HttpHeaders.X_FORWARDED_FOR);

    public enum FAILED_AUTH_KEY { REMOTE_IP, X_FORWARDED_FOR }

    private static FAILED_AUTH_KEY collectFailedAuthBy = FAILED_AUTH_KEY.REMOTE_IP;
    private static int xffReverseIndex = 0;

     /**
     *
     * @param exchange
     * @param useXForwardedFor true to use the X-Forwarded-For header, false to use the remote ip
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
     * we want to take into account only the last value to avoid
     * metrics to be flooded with values from the client
     *
     * NOTE: this is the behavior of AWS ALB
     *
     * @param xff
     * @param rindex @see useXForwaderdedElement()
     * @return the last element of a comma separated list
     */
    public static String xffValue(String xff, int rindex) {
        if (xff == null) {
            return null;
        } else {
            xff = xff.trim();

            if (xff.startsWith("[")) {
                xff = xff.substring(1);
            }

            if (xff.endsWith("]")) {
                xff = xff.substring(0, xff.length()-1);
            }

            var elements = xff.split(",");

            if (elements.length >= rindex) {
                if (rindex >= elements.length) {
                    return elements[0].trim();
                } else {
                    return elements[elements.length - 1 - rindex].trim();
                }
            } else {
                return elements[elements.length - 1].trim();
            }
        }
    }

    /**
     * Choose the key used to collect failed auth requests, REMOTE_IP (default) or X_FORWARDED_FOR
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
}
