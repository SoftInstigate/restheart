/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
package org.restheart.polyglot;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A singleton queue implementation for communication between RESTHeart and Node.js polyglot environments.
 * 
 * <p>This class provides a thread-safe, blocking queue that facilitates data exchange between
 * the Java-based RESTHeart server and JavaScript code running in a Node.js environment via
 * GraalVM's polyglot capabilities.</p>
 * 
 * <p>The queue is implemented as a singleton to ensure a single shared communication channel
 * across the entire application. It uses a {@link LinkedBlockingDeque} internally to provide
 * thread-safe operations without explicit synchronization.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Get the singleton instance
 * NodeQueue queue = NodeQueue.instance();
 * 
 * // Add data to the queue
 * queue.queue().offer(someData);
 * 
 * // Check if running in Node.js context
 * if (queue.isRunningOnNode()) {
 *     // Perform Node.js specific operations
 * }
 * }</pre>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 8.0.0
 */
public class NodeQueue {
    /**
     * The concurrent queue used for communication with Node.js.
     * This queue can safely be accessed from multiple threads.
     */
    private final Queue<Object> queue;
    
    /**
     * Flag indicating whether the current execution context is within a Node.js environment.
     * This is set to true when RESTHeart is running with Node.js polyglot support enabled.
     */
    private boolean runningOnNode = false;

    /**
     * Private constructor to enforce singleton pattern.
     * Initializes the internal queue with a thread-safe {@link LinkedBlockingDeque}.
     */
    private NodeQueue() {
      this.queue = new LinkedBlockingDeque<>();
    }

    /**
     * The singleton instance of NodeQueue.
     */
    private static NodeQueue instance;

    /**
     * Returns the singleton instance of NodeQueue.
     * 
     * <p>This method lazily initializes the queue on first access. While not strictly
     * thread-safe, the race condition is benign as multiple threads creating instances
     * would create functionally identical objects, and only one would ultimately be used.</p>
     * 
     * @return the singleton NodeQueue instance
     */
    public static NodeQueue instance() {
        if (instance == null) {
            instance = new NodeQueue();
        }

        return instance;
    }

    /**
     * Returns the underlying queue for direct manipulation.
     * 
     * <p>The returned queue is thread-safe and supports all standard {@link Queue} operations.
     * Common operations include:</p>
     * <ul>
     *   <li>{@code offer(Object)} - adds an element to the queue</li>
     *   <li>{@code poll()} - retrieves and removes the head element, or returns null if empty</li>
     *   <li>{@code peek()} - retrieves but does not remove the head element</li>
     * </ul>
     * 
     * @return the concurrent queue instance
     */
    public Queue<Object> queue() {
        return queue;
    }

    /**
     * Checks whether the current execution context is within a Node.js environment.
     * 
     * <p>This method is typically used to conditionally execute code based on whether
     * RESTHeart is running with Node.js polyglot support enabled.</p>
     * 
     * @return {@code true} if running in a Node.js context, {@code false} otherwise
     */
    public boolean isRunningOnNode() {
        return runningOnNode;
    }

    /**
     * Marks this instance as running within a Node.js environment.
     * 
     * <p>This method should only be called by the polyglot initialization code when
     * setting up the Node.js execution context. Once set, this flag cannot be unset.</p>
     * 
     * <p><strong>Note:</strong> This method is not thread-safe and should only be called
     * during application initialization.</p>
     */
    public void setAsRunningOnNode() {
        this.runningOnNode = true;
    }
  }
