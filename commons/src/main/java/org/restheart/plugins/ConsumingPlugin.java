/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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
package org.restheart.plugins;

import java.util.function.Consumer;

/**
 * Interface for plugins that support consumer-based event handling and notification patterns.
 * <p>
 * ConsumingPlugin provides a mechanism for plugins to register consumer functions that will
 * be notified when specific events or data become available. This interface implements the
 * observer pattern, allowing multiple consumers to be registered and invoked when relevant
 * events occur within the plugin's lifecycle or processing.
 * </p>
 * <p>
 * This pattern is particularly useful for:
 * <ul>
 *   <li><strong>Event Broadcasting</strong> - Notifying multiple listeners about plugin events</li>
 *   <li><strong>Data Processing Pipelines</strong> - Chaining multiple processing steps</li>
 *   <li><strong>Monitoring and Logging</strong> - Registering observers for audit and monitoring</li>
 *   <li><strong>Plugin Integration</strong> - Enabling loose coupling between plugin components</li>
 *   <li><strong>Asynchronous Processing</strong> - Supporting non-blocking event handling</li>
 * </ul>
 * </p>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(name = "eventService", description = "Service with event notifications")
 * public class EventService implements Service&lt;JsonRequest, JsonResponse&gt;, ConsumingPlugin&lt;String&gt; {
 *     private final List&lt;Consumer&lt;String&gt;&gt; consumers = new ArrayList&lt;&gt;();
 *     
 *     &#64;Override
 *     public void addConsumer(Consumer&lt;String&gt; consumer) {
 *         consumers.add(consumer);
 *     }
 *     
 *     &#64;Override
 *     public void handle(JsonRequest request, JsonResponse response) throws Exception {
 *         // Process request
 *         String result = processRequest(request);
 *         
 *         // Notify all registered consumers
 *         consumers.forEach(consumer -&gt; consumer.accept(result));
 *         
 *         response.setContent(JsonObject.of("result", result));
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Consumer Registration:</strong><br>
 * Other plugins or framework components can register consumers to receive notifications:
 * <pre>
 * // Register a logging consumer
 * eventService.addConsumer(event -&gt; LOGGER.info("Event occurred: {}", event));
 * 
 * // Register a monitoring consumer
 * eventService.addConsumer(event -&gt; metricsCollector.recordEvent(event));
 * 
 * // Register a processing consumer
 * eventService.addConsumer(event -&gt; processEventAsync(event));
 * </pre>
 * </p>
 * <p>
 * <strong>Thread Safety:</strong><br>
 * Implementations should consider thread safety when managing consumer collections,
 * as consumers may be added from different threads and events may be fired concurrently.
 * Consider using thread-safe collections or synchronization mechanisms as appropriate.
 * </p>
 * <p>
 * <strong>Error Handling:</strong><br>
 * When invoking consumers, implementations should handle exceptions appropriately to
 * prevent one failing consumer from affecting others. Consider logging errors and
 * continuing with remaining consumers rather than propagating exceptions.
 * </p>
 *
 * @param <T> the type of object that consumers will receive and process
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see java.util.function.Consumer
 * @see Plugin
 */
public interface ConsumingPlugin<T> {
    /**
     * Registers a consumer function to receive notifications from this plugin.
     * <p>
     * This method allows external components to register {@link Consumer} functions
     * that will be invoked when this plugin generates events or data of type T.
     * The consumer will be called with the event data when relevant events occur
     * within the plugin's processing lifecycle.
     * </p>
     * <p>
     * Consumer functions should be:
     * <ul>
     *   <li><strong>Non-blocking</strong> - Avoid long-running operations that could impact plugin performance</li>
     *   <li><strong>Exception-safe</strong> - Handle their own exceptions to prevent affecting other consumers</li>
     *   <li><strong>Stateless or thread-safe</strong> - Multiple events may be processed concurrently</li>
     * </ul>
     * </p>
     * <p>
     * Example consumer registrations:
     * <pre>
     * // Simple logging consumer
     * plugin.addConsumer(data -&gt; LOGGER.debug("Received: {}", data));
     * 
     * // Processing consumer with error handling
     * plugin.addConsumer(data -&gt; {
     *     try {
     *         processData(data);
     *     } catch (Exception e) {
     *         LOGGER.error("Failed to process data: {}", data, e);
     *     }
     * });
     * 
     * // Asynchronous processing consumer
     * plugin.addConsumer(data -&gt; CompletableFuture.runAsync(() -&gt; heavyProcessing(data)));
     * </pre>
     * </p>
     * <p>
     * <strong>Implementation Notes:</strong>
     * <ul>
     *   <li>Consumers are typically stored in a collection (List, Set, etc.)</li>
     *   <li>Consider thread safety if consumers may be added concurrently</li>
     *   <li>Duplicate consumers may or may not be allowed depending on implementation</li>
     *   <li>Order of consumer invocation is implementation-dependent</li>
     * </ul>
     * </p>
     *
     * @param consumer the consumer function to register for event notifications
     * @throws IllegalArgumentException if the consumer is null or invalid
     */
    public void addConsumer(Consumer<T> consumer);
}
