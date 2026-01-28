/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.ArrayList;
import java.util.List;
import org.restheart.Bootstrapper;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.slf4j.MDC;

public class TracingInstrumentationHandler extends PipelinedHandler {

  private final List<String> traceHeaders;
  private final boolean emptyTraceHeaders;
  // Cache HttpString instances to avoid repeated parsing
  private final List<HttpString> traceHeaderHttpStrings;

  public TracingInstrumentationHandler() {
    var _th = Bootstrapper.getConfiguration().logging().tracingHeaders();
    this.traceHeaders = _th == null ? new ArrayList<>() : _th;
    this.emptyTraceHeaders = this.traceHeaders.isEmpty();
    this.traceHeaderHttpStrings = emptyTraceHeaders ? null :
      traceHeaders.stream().map(HttpString::tryFromString).toList();
  }

  public TracingInstrumentationHandler(final PipelinedHandler next) {
    super(next);
    var _th = Bootstrapper.getConfiguration().logging().tracingHeaders();
    this.traceHeaders = _th == null ? new ArrayList<>() : _th;
    this.emptyTraceHeaders = this.traceHeaders.isEmpty();
    this.traceHeaderHttpStrings = emptyTraceHeaders ? null :
      traceHeaders.stream().map(HttpString::tryFromString).toList();
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    // Set trace ID: use first configured tracing header if available, otherwise use auto-generated ID
    if (emptyTraceHeaders) {
      // No tracing headers configured - use auto-generated trace ID (last 4 chars of requestId)
      var requestId = exchange.getRequestId();
      MDC.put(
        "traceId",
        requestId.substring(Math.max(0, requestId.length() - 4))
      );

      // Save the MDC context (including traceId) for async operations
      ByteArrayProxyResponse.of(exchange).setMDCContext(
        MDC.getCopyOfContextMap()
      );
      next(exchange);
    } else {
      var requestHeaders = exchange.getRequestHeaders();
      var responseHeaders = exchange.getResponseHeaders();

      // Tracing headers configured - try to use first header's value as trace ID
      var firstHeaderHttpString = traceHeaderHttpStrings.get(0);
      var firstHeaderKey = traceHeaders.get(0);
      var firstHeaderValues = requestHeaders.get(firstHeaderHttpString);
      var traceIdFromHeader = (firstHeaderValues != null) ? firstHeaderValues.peekFirst() : null;

      if (traceIdFromHeader != null) {
        // Use the first tracing header's value as the trace ID
        MDC.put("traceId", traceIdFromHeader);
        // Also add it with its actual header name so it shows in logs
        MDC.put(firstHeaderKey, traceIdFromHeader);
        // Echo it back in response
        responseHeaders.put(firstHeaderHttpString, traceIdFromHeader);
      } else {
        // Fallback to auto-generated trace ID if header not present
        var requestId = exchange.getRequestId();
        MDC.put(
          "traceId",
          requestId.substring(Math.max(0, requestId.length() - 4))
        );
      }

      // Process remaining tracing headers (if any)
      for (int i = 1; i < traceHeaderHttpStrings.size(); i++) {
        var headerHttpString = traceHeaderHttpStrings.get(i);
        var headerKey = traceHeaders.get(i);
        var headerValues = requestHeaders.get(headerHttpString);
        if (headerValues != null) {
          var value = headerValues.peekFirst();
          if (value != null) {
            MDC.put(headerKey, value);
            responseHeaders.put(headerHttpString, value);
          }
        }
      }

      // Save the MDC context (including traceId and custom headers) for async operations
      ByteArrayProxyResponse.of(exchange).setMDCContext(MDC.getCopyOfContextMap());

      if (!exchange.isResponseComplete() && getNext() != null) {
        next(exchange);
      }

      this.traceHeaders.forEach(MDC::remove);
    }
  }
}
