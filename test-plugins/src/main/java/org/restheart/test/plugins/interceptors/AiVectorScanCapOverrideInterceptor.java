/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.test.plugins.interceptors;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;

/**
 * Test interceptor that stands in for the Cloud deployment layer, which caps what a
 * {@code $vectorScan} stage may ask for according to the tenant's plan (#755).
 *
 * <p>Activates only when the request carries {@code _ai-max-candidates-cap-override=<n>} and/or
 * {@code _ai-max-limit-cap-override=<n>}, attaching {@code override-ai-max-candidates-cap} and/or
 * {@code override-ai-max-limit-cap} as integers. Every other request is untouched, so no other
 * test is affected. A value that is not an integer is ignored.
 *
 * <p>Used by karate/ai/vector-scan.feature. Like the other {@code *OverrideInterceptor}s in this
 * package it reads a query parameter, because the integration suite runs a single RESTHeart on a
 * single hostname and cannot tell tenants apart any other way.
 */
@RegisterPlugin(
        name = "aiVectorScanCapOverrideInterceptor",
        description = "Attaches override-ai-max-candidates-cap / override-ai-max-limit-cap per-request when ?_ai-max-candidates-cap-override=<n> / ?_ai-max-limit-cap-override=<n> is present",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
        priority = 20,
        enabledByDefault = false)
public class AiVectorScanCapOverrideInterceptor implements WildcardInterceptor {

    private static final String CANDIDATES_QPARAM = "_ai-max-candidates-cap-override";
    private static final String LIMIT_QPARAM = "_ai-max-limit-cap-override";

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        attach(req, CANDIDATES_QPARAM, "override-ai-max-candidates-cap");
        attach(req, LIMIT_QPARAM, "override-ai-max-limit-cap");
    }

    private static void attach(ServiceRequest<?> req, String qparam, String overrideKey) {
        var values = req.getExchange().getQueryParameters().get(qparam);
        var value = values == null ? null : values.peekFirst();
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            req.attachParam(overrideKey, Integer.parseInt(value.strip()));
        } catch (NumberFormatException nfe) {
            // not a cap: leave the request as it is
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        var params = req.getExchange().getQueryParameters();
        return (params.containsKey(CANDIDATES_QPARAM) && !params.get(CANDIDATES_QPARAM).isEmpty())
                || (params.containsKey(LIMIT_QPARAM) && !params.get(LIMIT_QPARAM).isEmpty());
    }
}
