/*-
 * ========================LICENSE_START=================================
 * custom-logging-field
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

package org.restheart.examples;

import java.util.HashMap;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name = "customLoggingFieldInteceptor",
    interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH,
    description = "add a custom logging filed to log messages",
    priority = Integer.MIN_VALUE)
public class CustomLoggingFieldInteceptor implements WildcardInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomLoggingFieldInteceptor.class);
    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) {
        // The MDC context is put in the thread context and
        // a thread switch in the request handling pipeline loses the MDC context.

        // RESTHeart allows to attach it to the request
        var mdcCtx = response.getMDCContext();

        // attach the MDC context to the response if not done by other plugins
        if (mdcCtx == null) {
            mdcCtx = MDC.getCopyOfContextMap() == null ? new HashMap<String, String>(): MDC.getCopyOfContextMap();
            response.setMDCContext(mdcCtx);
        }

        mdcCtx.put("timestamp", "" + System.currentTimeMillis());

        // restore the MDC context
        MDC.setContextMap(mdcCtx);

        LOGGER.info("This log message includes the timestamp variable from the MDC context");
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        return true;
    }
}
