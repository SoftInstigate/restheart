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
 * Interface for plugins that consume and process data of a specific type.
 * 
 * <p>ConsumingPlugin represents a plugin that acts as a consumer of data, allowing
 * other components to register consumers that will be notified when data is available.
 * This pattern is useful for implementing event-driven architectures, data pipelines,
 * and reactive processing within RESTHeart.</p>
 * 
 * <h2>Purpose</h2>
 * <p>This interface enables plugins to:</p>
 * <ul>
 *   <li>Act as data sinks in processing pipelines</li>
 *   <li>Implement observer patterns for data changes</li>
 *   <li>Build event-driven architectures</li>
 *   <li>Create data transformation chains</li>
 *   <li>Enable asynchronous data processing</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @RegisterPlugin(name = "data-processor")
 * public class DataProcessor implements ConsumingPlugin<DataEvent>, Provider<DataProcessor> {
 *     private final List<Consumer<DataEvent>> consumers = new ArrayList<>();
 *     
 *     @Override
 *     public void addConsumer(Consumer<DataEvent> consumer) {
 *         consumers.add(consumer);
 *     }
 *     
 *     public void processData(DataEvent event) {
 *         // Process the event
 *         DataEvent processed = transform(event);
 *         
 *         // Notify all registered consumers
 *         consumers.forEach(c -> c.accept(processed));
 *     }
 *     
 *     @Override
 *     public DataProcessor get(PluginsRegistry registry) {
 *         return this;
 *     }
 * }
 * 
 * // Registering a consumer
 * dataProcessor.addConsumer(event -> {
 *     logger.info("Received event: {}", event);
 *     // Handle the event
 * });
 * }</pre>
 * 
 * <h2>Implementation Considerations</h2>
 * <ul>
 *   <li>Implementations should handle thread safety if consumers may be added concurrently</li>
 *   <li>Consider using {@link java.util.concurrent.CopyOnWriteArrayList} for thread-safe consumer lists</li>
 *   <li>Handle exceptions in consumers to prevent one consumer from affecting others</li>
 *   <li>Consider providing methods to remove consumers if needed</li>
 *   <li>Document whether consumers are called synchronously or asynchronously</li>
 * </ul>
 * 
 * <h2>Common Use Cases</h2>
 * <ul>
 *   <li><strong>Event Broadcasting:</strong> Notify multiple listeners of data changes</li>
 *   <li><strong>Data Pipeline:</strong> Chain multiple processing stages</li>
 *   <li><strong>Monitoring:</strong> Collect metrics or logs from data flow</li>
 *   <li><strong>Caching:</strong> Update caches when data changes</li>
 *   <li><strong>Integration:</strong> Send data to external systems</li>
 * </ul>
 * 
 * @param <T> the type of data this plugin consumes
 * @see Consumer
 * @see Provider
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface ConsumingPlugin<T> {
    /**
     * Registers a consumer that will be notified when data of type T is available.
     * 
     * <p>The registered consumer will be invoked whenever this plugin has data to process.
     * Multiple consumers can be registered, and they will typically be notified in the
     * order they were registered, though this may vary by implementation.</p>
     * 
     * @param consumer the consumer to register for receiving data notifications
     * @throws NullPointerException if the consumer is null (implementation dependent)
     */
    public void addConsumer(Consumer<T> consumer);
}
