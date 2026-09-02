/*-
 * ========================LICENSE_START=================================
 * restheart-mqtt
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

package org.restheart.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

/**
 * Singleton router that bridges an underlying MQTT broker connection (MQTT 3 or 5, via the
 * HiveMQ client) to in-process listeners.
 * <p>
 * Consumers register interest in a topic filter via {@link #subscribe(String, MqttQos, Consumer)};
 * the router lazily subscribes to the broker the first time a topic filter gains a listener, and
 * unsubscribes from the broker once the last listener for a topic filter is removed. Incoming
 * broker messages are converted to {@link MqttMessage} instances, optionally cached for late
 * joiners, and dispatched to all matching listeners on a virtual thread.
 * <p>
 * The router also applies a simple per-second rate limit to protect against message floods, and
 * exposes basic {@link RouterStats runtime statistics}.
 * <p>
 * This class is thread-safe: internal state is held in concurrent collections and atomic
 * counters, and it is accessed as a process-wide singleton via {@link #getInstance()}.
 * 
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 */
public class MqttMessageRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttMessageRouter.class);

    // Topic filter -> list of message consumers
    private final Map<String, List<Consumer<MqttMessage>>> listeners = new ConcurrentHashMap<>();

    // Topic -> last message cache (for late joiners)
    private final Map<String, MqttMessage> lastMessageCache = new ConcurrentHashMap<>();

    // Rate Limiting
    private final AtomicLong messageCount = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private volatile long lastResetTime = System.currentTimeMillis();
    private volatile int maxMessagePerSecond = 5000; // Default, configurable

    // Configuration
    private volatile boolean cacheEnabled = true;
    private volatile int maxCacheSize = 1000;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Returns the process-wide singleton instance of the router.
     * <p>
     * The instance is created lazily and stashed in a system property keyed by the class name so
     * that a single instance is shared even across multiple classloaders (see
     * {@link MqttMessageRouterHolder}).
     *
     * @return the singleton {@code MqttMessageRouter} instance
     */
    public static MqttMessageRouter getInstance() {
        return MqttMessageRouterHolder.INSTANCE;
    }

    private MqttMessageRouter() {
        // Private constructor for singleton
    }

    /**
     * Initializes the router's configuration. This must be called once before the router is
     * used; subsequent calls are ignored (and logged as a warning) so that configuration cannot
     * be silently changed after startup.
     *
     * @param maxMessagePerSecond the maximum number of messages accepted per second before
     *                            further messages are dropped; a value {@code <= 0} disables the
     *                            rate limit
     * @param cacheEnabled        whether the last message received per topic should be cached for
     *                            late-joining subscribers
     * @param maxCacheSize        the maximum number of topics to retain in the last-message cache
     *                            when caching is enabled
     */
    public void init(int maxMessagePerSecond, boolean cacheEnabled, int maxCacheSize) {
        if (!initialized.compareAndSet(false, true)) {
            LOGGER.warn("MqttMessageRouter already initialized, ignoring duplicate init call");
            return;
        }
        this.maxMessagePerSecond = maxMessagePerSecond;
        this.maxCacheSize = maxCacheSize;
        this.cacheEnabled = cacheEnabled;

        LOGGER.info("MqttMessageRouter initialized: maxRate={}/s, cache={}, maxCacheSize={}", 
        maxMessagePerSecond, cacheEnabled, maxCacheSize);
    }

    /**
     * Registers a listener for messages published on topics matching the given topic filter.
     * <p>
     * If this is the first listener registered for {@code topicFilter}, the router subscribes to
     * the filter on the underlying broker connection with the given QoS.
     *
     * @param topicFilter the MQTT topic filter to listen on (may contain {@code +} and
     *                    {@code #} wildcards)
     * @param qos         the QoS level to use when subscribing to the broker
     * @param listener    the callback invoked with each {@link MqttMessage} matching the filter
     */
    public void subscribe(String topicFilter, MqttQos qos, Consumer<MqttMessage> listener) {
        listeners.computeIfAbsent(topicFilter, k -> new CopyOnWriteArrayList<>()).add(listener);

        // Subscribe to broker if this is the first listener for this topic
        if (listeners.get(topicFilter).size() == 1) {
            subscribeOnBroker(topicFilter, qos);
        }

        LOGGER.debug("Added listener for topic filter: {}, total listeners: {}", 
        topicFilter, listeners.get(topicFilter).size());
    }

    /**
     * Removes a previously registered listener for the given topic filter.
     * <p>
     * If this was the last listener registered for {@code topicFilter}, the router unsubscribes
     * from the filter on the underlying broker connection.
     *
     * @param topicFilter the MQTT topic filter the listener was registered on
     * @param listener    the listener instance to remove
     */
    public void unsubscribe(String topicFilter, Consumer<MqttMessage> listener) {
        List<Consumer<MqttMessage>> topicListeners = listeners.get(topicFilter);
        if (topicListeners != null) {
            topicListeners.remove(listener);

            // If no more listeners, unsubscribe from broker
            if (topicListeners.isEmpty()) {
                listeners.remove(topicFilter);
                unsubscribeFromBroker(topicFilter);
            }

            LOGGER.debug("Removed listener for topic filter: {}, remaining: {}"
                , topicFilter, topicListeners.size());
        }
    }

    /**
     * Issues a subscribe request for the given topic filter against the underlying MQTT client,
     * dispatching to the MQTT 5 or MQTT 3 API depending on the client type in use. The
     * appropriate {@code handleMqtt5Message}/{@code handleMqtt3Message} callback is wired in as
     * the message handler for the subscription.
     *
     * @param topicFilter the MQTT topic filter to subscribe to
     * @param qos         the QoS level to subscribe with
     */
    private void subscribeOnBroker(String topicFilter, MqttQos qos) {
        MqttClient client = MqttClientSingleton.getInstance().getClient();

        if (client instanceof Mqtt5AsyncClient) {
            ((Mqtt5AsyncClient)client).subscribeWith()
                .topicFilter(topicFilter)
                .qos(qos)
                .callback(this::handleMqtt5Message)
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Failed to subscribe to topic filter: {}", topicFilter, throwable);
                    } else {
                        LOGGER.info("Subscribed to topic filter: {} with QoS {}", topicFilter, qos);
                    }
            });
        } else if (client instanceof Mqtt3AsyncClient) {
            ((Mqtt3AsyncClient) client).subscribeWith()
                .topicFilter(topicFilter)
                .qos(qos)
                .callback(this::handleMqtt3Message)
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Failed to subscribe to topic filter: {}", topicFilter, throwable);
                    } else {
                        LOGGER.info("Subscribed to topic filter: {} with QoS {}", topicFilter, qos);
                    }
                });
        }
    }

    /**
     * Issues an unsubscribe request for the given topic filter against the underlying MQTT
     * client, dispatching to the MQTT 5 or MQTT 3 API depending on the client type in use.
     *
     * @param topicFilter the MQTT topic filter to unsubscribe from
     */
    private void unsubscribeFromBroker(String topicFilter) {
        MqttClient client = MqttClientSingleton.getInstance().getClient();

        if (client instanceof Mqtt5AsyncClient) {
            ((Mqtt5AsyncClient) client).unsubscribeWith()
                .topicFilter(topicFilter)
                .send()
                .whenComplete((unsubAck, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Failed to unsubscribe to topic filter: {}", topicFilter, throwable);
                    } else {
                        LOGGER.info("Unsubscribed to topic filter: {} with QoS {}", topicFilter);
                    }
                });
        } else if (client instanceof Mqtt3AsyncClient) {
            ((Mqtt3AsyncClient) client).unsubscribeWith()
                .topicFilter(topicFilter)
                .send()
                .whenComplete((unsubAck, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Failed to unsubscribe to topic filter: {}", topicFilter, throwable);
                    } else {
                        LOGGER.info("Unsubscribed to topic filter: {} with QoS {}", topicFilter);
                    }
                });
        }
    }

    /**
     * Handles an incoming MQTT 5 publish: applies the rate limit, converts the publish to an
     * internal {@link MqttMessage}, updates the last-message cache if enabled, and dispatches the
     * message to matching listeners on a new virtual thread.
     *
     * @param publish the MQTT 5 publish received from the broker
     */
    private void handleMqtt5Message(Mqtt5Publish publish) {
        // Check rate limit
        if (!checkRateLimit()) {
            droppedCount.incrementAndGet();
            if (droppedCount.get() % 1000 == 0) {
                LOGGER.warn("Rate limit exceeded, dropped {} messages", droppedCount.get());
            }
            return;
        }

        // Convert to internal message format
        MqttMessage message = new MqttMessage(
            publish.getTopic().toString(),
            new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8),
            publish.getQos().getCode(),
            Instant.now()
        );

        // Update cache
        if (cacheEnabled) {
            updateCache(message);
        }

        // Dispatch on virtual thread
        Thread.ofVirtual().start(() -> dispatchMessage(message));

    }

    /**
     * Handles an incoming MQTT 3 publish: applies the rate limit, converts the publish to an
     * internal {@link MqttMessage}, updates the last-message cache if enabled, and dispatches the
     * message to matching listeners on a new virtual thread.
     *
     * @param publish the MQTT 3 publish received from the broker
     */
    private void handleMqtt3Message(Mqtt3Publish publish) {
        // Check rate limit
        if (!checkRateLimit()) {
            droppedCount.incrementAndGet();
            if (droppedCount.get() % 1000 == 0) {
                LOGGER.warn("Rate limit exceeded, dropped {} messages", droppedCount.get());
            }
            return;
        }

        // Convert to internal message format
        MqttMessage message = new MqttMessage(
            publish.getTopic().toString(),
            new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8),
            publish.getQos().getCode(),
            Instant.now()
        );

        // Update cache
        if (cacheEnabled) {
            updateCache(message);
        }

        // Dispatch on virtual thread
        Thread.ofVirtual().start(() -> dispatchMessage(message));

    }

    /**
     * Enforces the configured per-second message rate limit, resetting the counter once a second
     * has elapsed since the last reset.
     *
     * @return {@code true} if the current message is within the allowed rate (or rate limiting is
     *         disabled because {@code maxMessagePerSecond <= 0}), {@code false} if it should be
     *         dropped
     */
    private boolean checkRateLimit() {
        if (maxMessagePerSecond <= 0) {
            return true; // No limit
        }

        long now = System.currentTimeMillis();
        long elapsed = now - lastResetTime;

        // Reset counter every second
        if (elapsed >= 1000) {
            synchronized (this) {
                if (elapsed >= 1000) {
                    messageCount.set(0);
                    lastResetTime = now;
                }
            }
        }

        return messageCount.incrementAndGet() <= maxMessagePerSecond;
    }

    /**
     * Stores the given message as the last received message for its topic, evicting an arbitrary
     * existing entry first if the cache is already at {@link #maxCacheSize}.
     * <p>
     * Note: eviction is not LRU-based; it simply removes whichever entry the map's iterator
     * returns first.
     *
     * @param message the message to cache, keyed by {@link MqttMessage#getTopic()}
     */
    private void updateCache(MqttMessage message) {
        if (lastMessageCache.size() >= maxCacheSize) {
            // Simmple eviction: remove first entry (not LRU, but good enough)
            String firstKey = lastMessageCache.keySet().iterator().next();
            lastMessageCache.remove(firstKey);
        }

        lastMessageCache.put(message.getTopic(), message);
    }

    /**
     * Dispatches a received message to every registered listener whose topic filter matches the
     * message's topic. Exceptions thrown by individual listeners are caught and logged so that a
     * failing listener does not prevent delivery to other listeners.
     *
     * @param message the message to dispatch
     */
    private void dispatchMessage(MqttMessage message) {
        for (Map.Entry<String, List<Consumer<MqttMessage>>> entry : listeners.entrySet()) {
            String topicFilter = entry.getKey();

            // Check if message topic matches the filter
            if (topicMatches(message.getTopic(), topicFilter)) {
                for (Consumer<MqttMessage> listener : entry.getValue()) {
                    try {
                        listener.accept(message);
                    } catch (Exception e) {
                        LOGGER.error("Error dispatching message to listener for topic {}", 
                            message.getTopic());
                    }
                }
            }
        }
    }

    /**
     * Determines whether a concrete MQTT topic matches a topic filter, following standard MQTT
     * matching rules: exact matches, the single-level wildcard {@code +}, and the multi-level
     * wildcard {@code #} (which must appear as the final level of the filter).
     *
     * @param topic       the concrete topic a message was published on
     * @param topicFilter the topic filter (possibly containing {@code +}/{@code #} wildcards) to
     *                    match against
     * @return {@code true} if {@code topic} matches {@code topicFilter}, {@code false} otherwise
     */
    private boolean topicMatches(String topic, String topicFilter) {
        // Exact match
        if (topic.equals(topicFilter)) {
            return true;
        }

        String[] topicLevels = topic.split("/");
        String[] topicFilterLevels = topicFilter.split("/");

        // Multi-level wildcard (#) must be last
        if (topicFilter.endsWith("/#")) {
            String prefix = topicFilter.substring(0, topicFilter.length() - 2);
            return topic.startsWith(prefix);
        }

        // Different number of level (and no multi-level wildcard)
        if (topicLevels.length != topicFilterLevels.length) {
            return false;
        }

        // Check each level
        for (int i = 0; i < topicFilterLevels.length; i++) {
            String filterLevel = topicFilterLevels[i];
            String topicLevel = topicLevels[i];

            // Single-level wildcard (+) matches any single level
            if (filterLevel.equals("+")) {
                continue;
            }

            // Exact match required
            if (!filterLevel.equals(topicLevel)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the last message received for the given topic, if caching is enabled and a message
     * has been received and retained for it.
     *
     * @param topic the exact topic to look up
     * @return the last cached {@link MqttMessage} for {@code topic}, or {@code null} if none is
     *         cached
     */
    public MqttMessage getLastMessage(String topic) {
        return lastMessageCache.get(topic);
    }

    /**
     * Re-issues broker subscriptions for every topic filter that currently has registered
     * listeners. Intended to be called after the underlying MQTT client reconnects, since broker
     * subscriptions do not survive a disconnect.
     * <p>
     * All re-subscriptions are made with QoS {@link MqttQos#AT_LEAST_ONCE}, regardless of the QoS
     * originally requested by each listener.
     */
    public void resubscribeAll() {
        LOGGER.info("Re-subscribing to {} topic filters after reconnect", listeners.size());

        for (String topicFilter : listeners.keySet()) {
            // Use default QoS 1 for re-subscription
            subscribeOnBroker(topicFilter, MqttQos.AT_LEAST_ONCE);
        }
    }

    /**
     * Get statistics
     *
     * @return a snapshot of the router's current runtime statistics
     */
    public RouterStats getStats() {
        return new RouterStats(
            listeners.size(),
            listeners.values().stream().mapToInt(List::size).sum(),
            lastMessageCache.size(),
            messageCount.get(),
            droppedCount.get()
        );
    }

    /**
     * Statistics holder
     */
    public static class RouterStats {
        private final int topicFilters;
        private final int totalListeners;
        private final int cachedMessages;
        private final long messagesReceived;
        private final long messagesDropped;

        /**
         * Creates a new immutable statistics snapshot.
         *
         * @param topicFilters     the number of distinct topic filters currently subscribed
         * @param totalListeners   the total number of listeners registered across all topic filters
         * @param cachedMessages   the number of topics currently present in the last-message cache
         * @param messagesReceived the number of messages counted toward the current rate-limit window
         * @param messagesDropped  the cumulative number of messages dropped due to rate limiting
         */
        public RouterStats(int topicFilters, int totalListeners, int cachedMessages,
                          long messagesReceived, long messagesDropped) {
            this.topicFilters = topicFilters;
            this.totalListeners = totalListeners;
            this.cachedMessages = cachedMessages;
            this.messagesReceived = messagesReceived;
            this.messagesDropped = messagesDropped;
        }

        /**
         * @return the number of distinct topic filters currently subscribed
         */
        public int getTopicFilters() { return topicFilters; }

        /**
         * @return the total number of listeners registered across all topic filters
         */
        public int getTotalListeners() { return totalListeners; }

        /**
         * @return the number of topics currently present in the last-message cache
         */
        public int getCachedMessages() { return cachedMessages; }

        /**
         * @return the number of messages counted toward the current rate-limit window
         */
        public long getMessagesReceived() { return messagesReceived; }

        /**
         * @return the cumulative number of messages dropped due to rate limiting
         */
        public long getMessagesDropped() { return messagesDropped; }
    }

    /**
     * Lazy holder that creates (or reuses) the {@link MqttMessageRouter} singleton.
     * <p>
     * The instance is stored in a system property keyed by the class name so that if this class
     * is loaded by more than one classloader, all loads share the same underlying router
     * instance rather than creating independent singletons.
     */
    private static class MqttMessageRouterHolder {
        private static final MqttMessageRouter INSTANCE;

        static {
            synchronized (ClassLoader.getSystemClassLoader()) {
                final var sysProps = System.getProperties();
                final var singleton = (MqttMessageRouter) sysProps.get(MqttMessageRouter.class.getName());

                if (singleton != null) {
                    INSTANCE = singleton;
                } else {
                    INSTANCE = new MqttMessageRouter();
                    System.getProperties().put(MqttMessageRouter.class.getName(), INSTANCE);
                }
            }
        }
        
    }
}