/*-
 * ========================LICENSE_START=================================
 * x-powered-by-interceptor
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

import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.ProxyInterceptor;
import org.restheart.plugins.RegisterPlugin;

import com.google.common.net.HttpHeaders;

@RegisterPlugin(name = "xPoweredByProxyRemover",
    interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH,
    description = "removes the X-Powered-By response from proxied responses")
public class XPoweredByProxyRemover implements ProxyInterceptor {
    @Override
    public void handle(ByteArrayProxyRequest request, ByteArrayProxyResponse response) {
        response.getHeaders().remove(HttpHeaders.X_POWERED_BY);
    }

    @Override
    public boolean resolve(ByteArrayProxyRequest request, ByteArrayProxyResponse response) {
        return true;
    }
}
