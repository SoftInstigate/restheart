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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

/**
 * Router that bridges an underlying MQTT broker connection (MQTT 3 or 5, via the HiveMQ client)
 * to in-process listeners.
 * <p>
 * Consumers register interest in a topic filter via {@link #subscribe(String, MqttQos, Consumer)};
 * the router lazily subscribes to the broker the first time a topic filter gains a listener, and
 * unsubscribes from the broker once the last listener for a topic filter is removed — unless the
 * filter was established via {@link #subscribeFromConfig(String, MqttQos)}, in which case it
 * survives listener churn. A single global publish consumer is registered once, at construction
 * time, and every received publish is fanned out in-process (in {@link #dispatchMessage}) to every
 * listener whose topic filter matches, exactly once per listener.
 * <p>
 * The router also applies a per-second token-bucket rate limit to protect against message floods,
 * and exposes basic {@link RouterStats runtime statistics}.
 * <p>
 * This class is thread-safe: internal state is held in concurrent collections and atomic
 * counters. It is not a singleton and touches no statics: a single instance is built and owned
 * by {@code MqttRouterProvider}, which also wires {@link #resubscribeAll()} into
 * {@link MqttClientSingleton#addOnNewSessionListener(Runnable)} and injects the router into the
 * plugins that need it.
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMessageRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttMessageRouter.class);

    /** The underlying MQTT client used to (un)subscribe on the broker. */
    private final MqttClient client;

    // Topic filter -> list of message consumers
    private final Map<String, List<Consumer<MqttMessage>>> listeners = new ConcurrentHashMap<>();

    // Topic filter -> effective (highest requested) QoS currently subscribed on the broker
    private final Map<String, MqttQos> filterQos = new ConcurrentHashMap<>();

    // Topic filters established via configuration: these survive listener churn
    private final Set<String> configuredFilters = ConcurrentHashMap.newKeySet();

    // Topic -> last message cache (for late joiners); access-ordered and bounded for LRU eviction
    private final Map<String, MqttMessage> lastMessageCache;

    // Rate limiting: token bucket
    private double availableTokens;
    private long lastRefillNanos = System.nanoTime();
    private final AtomicLong messagesReceivedCount = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final int maxMessagePerSecond;

    // Configuration
    private final boolean cacheEnabled;
    private final int maxCacheSize;

    /**
     * Creates the router bound to the given MQTT client and configuration.
     * <p>
     * The router itself has no knowledge of {@link MqttClientSingleton}: wiring
     * {@link #resubscribeAll()} into {@link MqttClientSingleton#addOnNewSessionListener(Runnable)}
     * is the responsibility of the caller (in production, {@code MqttRouterProvider}), so that a
     * router can be constructed from its collaborators alone.
     * <p>
     * A single global publish consumer is registered against {@code client} here, once, so that
     * in-process fanout (in {@link #dispatchMessage}) is the only place a received message is
     * delivered to listeners — per-subscription callbacks are never used.
     *
     * @param client               the underlying MQTT client used to (un)subscribe on the broker
     * @param maxMessagesPerSecond the maximum number of messages accepted per second before
     *                             further messages are dropped; a value {@code <= 0} disables the
     *                             rate limit
     * @param cacheEnabled         whether the last message received per topic should be cached for
     *                             late-joining subscribers
     * @param maxCacheSize         the maximum number of topics to retain in the last-message cache
     *                             when caching is enabled
     */
    public MqttMessageRouter(MqttClient client, int maxMessagesPerSecond, boolean cacheEnabled, int maxCacheSize) {
        this.client = client;
        this.maxMessagePerSecond = maxMessagesPerSecond;
        this.availableTokens = maxMessagesPerSecond > 0 ? maxMessagesPerSecond : 0;
        this.cacheEnabled = cacheEnabled;
        this.maxCacheSize = maxCacheSize;
        this.lastMessageCache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, MqttMessage> eldest) {
                return size() > maxCacheSize;
            }
        });

        registerGlobalPublishConsumer();

        LOGGER.info("MqttMessageRouter initialized: maxRate={}/s, cache={}, maxCacheSize={}",
            maxMessagesPerSecond, cacheEnabled, maxCacheSize);
    }

    /**
     * Registers the single global publish consumer that receives every publish delivered by the
     * broker, regardless of which topic filter(s) it was matched against. This is what makes
     * in-process fanout (in {@link #dispatchMessage}) the sole delivery path: broker subscriptions
     * are issued without a per-subscription callback (see {@link #subscribeOnBroker}), so a
     * message overlapping two subscribed filters is still received by this consumer exactly once.
     */
    private void registerGlobalPublishConsumer() {
        if (client instanceof Mqtt5AsyncClient mqtt5Client) {
            mqtt5Client.publishes(MqttGlobalPublishFilter.ALL, this::handleMqtt5Message);
        } else if (client instanceof Mqtt3AsyncClient mqtt3Client) {
            mqtt3Client.publishes(MqttGlobalPublishFilter.ALL, this::handleMqtt3Message);
        } else {
            LOGGER.error("Unsupported MQTT client type: {}; unable to register publish consumer",
                client == null ? "null" : client.getClass().getName());
        }
    }

    /**
     * Registers a listener for messages published on topics matching the given topic filter.
     * <p>
     * If this is the first listener registered for {@code topicFilter}, the router subscribes to
     * the filter on the underlying broker connection with the given QoS. If listeners for
     * {@code topicFilter} already exist but requested a lower QoS, and {@code qos} is higher, the
     * broker subscription is re-issued at the new, higher QoS: the filter is always subscribed at
     * the highest QoS requested by any of its listeners.
     *
     * @param topicFilter the MQTT topic filter to listen on (may contain {@code +} and
     *                    {@code #} wildcards)
     * @param qos         the QoS level to use when subscribing to the broker
     * @param listener    the callback invoked with each {@link MqttMessage} matching the filter
     */
    public void subscribe(String topicFilter, MqttQos qos, Consumer<MqttMessage> listener) {
        listeners.computeIfAbsent(topicFilter, k -> new CopyOnWriteArrayList<>()).add(listener);

        ensureBrokerSubscription(topicFilter, qos);

        LOGGER.debug("Added listener for topic filter: {}, total listeners: {}",
            topicFilter, listeners.get(topicFilter).size());
    }

    /**
     * Establishes a broker subscription for a topic filter with no associated listener, so that
     * the last-message cache is populated for late joiners (e.g. {@code mqtt-rest} polling) even
     * with no SSE client connected.
     * <p>
     * Filters registered this way are marked as configured: unlike listener-driven subscriptions,
     * they are never unsubscribed from the broker as a side effect of listener churn (see
     * {@link #unsubscribe(String, Consumer)}).
     *
     * @param topicFilter the MQTT topic filter to subscribe to
     * @param qos         the QoS level to use when subscribing to the broker
     */
    void subscribeFromConfig(String topicFilter, MqttQos qos) {
        configuredFilters.add(topicFilter);
        ensureBrokerSubscription(topicFilter, qos);
    }

    /**
     * Subscribes {@code topicFilter} on the broker at {@code qos} if it is not currently
     * subscribed, or re-subscribes it at {@code qos} if that is higher than the QoS it is
     * currently tracked at.
     *
     * @param topicFilter the MQTT topic filter to subscribe to
     * @param qos         the QoS level requested
     */
    private void ensureBrokerSubscription(String topicFilter, MqttQos qos) {
        boolean needsBrokerSubscribe;
        synchronized (filterQos) {
            MqttQos current = filterQos.get(topicFilter);
            if (current == null || qos.getCode() > current.getCode()) {
                filterQos.put(topicFilter, qos);
                needsBrokerSubscribe = true;
            } else {
                needsBrokerSubscribe = false;
            }
        }

        if (needsBrokerSubscribe) {
            subscribeOnBroker(topicFilter, qos);
        }
    }

    /**
     * Removes a previously registered listener for the given topic filter.
     * <p>
     * If this was the last listener registered for {@code topicFilter}, the router unsubscribes
     * from the filter on the underlying broker connection — unless {@code topicFilter} was
     * established via {@link #subscribeFromConfig(String, MqttQos)}, in which case the broker
     * subscription is retained regardless of listener count.
     *
     * @param topicFilter the MQTT topic filter the listener was registered on
     * @param listener    the listener instance to remove
     */
    public void unsubscribe(String topicFilter, Consumer<MqttMessage> listener) {
        List<Consumer<MqttMessage>> topicListeners = listeners.get(topicFilter);
        if (topicListeners != null) {
            topicListeners.remove(listener);

            if (topicListeners.isEmpty()) {
                listeners.remove(topicFilter, topicListeners);

                if (!configuredFilters.contains(topicFilter)) {
                    synchronized (filterQos) {
                        filterQos.remove(topicFilter);
                    }
                    unsubscribeFromBroker(topicFilter);
                }
            }

            LOGGER.debug("Removed listener for topic filter: {}, remaining: {}",
                topicFilter, topicListeners.size());
        }
    }

    /**
     * Issues a subscribe request for the given topic filter against the underlying MQTT client,
     * dispatching to the MQTT 5 or MQTT 3 API depending on the client type in use. No
     * per-subscription callback is attached: all incoming publishes are received exclusively via
     * the single global publish consumer registered in the constructor, and delivered to matching
     * listeners by {@link #dispatchMessage}.
     *
     * @param topicFilter the MQTT topic filter to subscribe to
     * @param qos         the QoS level to subscribe with
     */
    private void subscribeOnBroker(String topicFilter, MqttQos qos) {
        if (client instanceof Mqtt5AsyncClient mqtt5Client) {
            mqtt5Client.subscribeWith()
                .topicFilter(topicFilter)
                .qos(qos)
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Failed to subscribe to topic filter: {}", topicFilter, throwable);
                    } else {
                        LOGGER.info("Subscribed to topic filter: {} with QoS {}", topicFilter, qos);
                    }
            });
        } else if (client instanceof Mqtt3AsyncClient mqtt3Client) {
            mqtt3Client.subscribeWith()
                .topicFilter(topicFilter)
                .qos(qos)
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Failed to subscribe to topic filter: {}", topicFilter, throwable);
                    } else {
                        LOGGER.info("Subscribed to topic filter: {} with QoS {}", topicFilter, qos);
                    }
                });
        } else {
            LOGGER.error("Unsupported MQTT client type: {}; unable to subscribe to topic filter: {}",
                client == null ? "null" : client.getClass().getName(), topicFilter);
        }
    }

    /**
     * Issues an unsubscribe request for the given topic filter against the underlying MQTT
     * client, dispatching to the MQTT 5 or MQTT 3 API depending on the client type in use.
     *
     * @param topicFilter the MQTT topic filter to unsubscribe from
     */
    private void unsubscribeFromBroker(String topicFilter) {
        if (client instanceof Mqtt5AsyncClient mqtt5Client) {
            mqtt5Client.unsubscribeWith()
                .topicFilter(topicFilter)
                .send()
                .whenComplete((unsubAck, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Failed to unsubscribe from topic filter: {}", topicFilter, throwable);
                    } else {
                        LOGGER.info("Unsubscribed from topic filter: {}", topicFilter);
                    }
                });
        } else if (client instanceof Mqtt3AsyncClient mqtt3Client) {
            mqtt3Client.unsubscribeWith()
                .topicFilter(topicFilter)
                .send()
                .whenComplete((unsubAck, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Failed to unsubscribe from topic filter: {}", topicFilter, throwable);
                    } else {
                        LOGGER.info("Unsubscribed from topic filter: {}", topicFilter);
                    }
                });
        } else {
            LOGGER.error("Unsupported MQTT client type: {}; unable to unsubscribe from topic filter: {}",
                client == null ? "null" : client.getClass().getName(), topicFilter);
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
        if (!checkRateLimit()) {
            recordDropped();
            return;
        }
        messagesReceivedCount.incrementAndGet();

        MqttMessage message = new MqttMessage(
            publish.getTopic().toString(),
            new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8),
            publish.getQos().getCode(),
            Instant.now()
        );

        if (cacheEnabled) {
            updateCache(message);
        }

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
        if (!checkRateLimit()) {
            recordDropped();
            return;
        }
        messagesReceivedCount.incrementAndGet();

        MqttMessage message = new MqttMessage(
            publish.getTopic().toString(),
            new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8),
            publish.getQos().getCode(),
            Instant.now()
        );

        if (cacheEnabled) {
            updateCache(message);
        }

        Thread.ofVirtual().start(() -> dispatchMessage(message));
    }

    /**
     * Records a rate-limited (dropped) message, logging a warning every 1000 drops.
     */
    private void recordDropped() {
        long dropped = droppedCount.incrementAndGet();
        if (dropped % 1000 == 0) {
            LOGGER.warn("Rate limit exceeded, dropped {} messages", dropped);
        }
    }

    /**
     * Enforces the configured per-second message rate limit using a token bucket: the bucket has
     * capacity {@code maxMessagePerSecond} and refills continuously at
     * {@code maxMessagePerSecond} tokens per second, based on {@link System#nanoTime()}.
     *
     * @return {@code true} if the current message is within the allowed rate (or rate limiting is
     *         disabled because {@code maxMessagePerSecond <= 0}), {@code false} if it should be
     *         dropped
     */
    private synchronized boolean checkRateLimit() {
        if (maxMessagePerSecond <= 0) {
            return true; // No limit
        }

        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos > 0) {
            double refill = (elapsedNanos / 1_000_000_000.0) * maxMessagePerSecond;
            availableTokens = Math.min(maxMessagePerSecond, availableTokens + refill);
            lastRefillNanos = now;
        }

        if (availableTokens >= 1.0) {
            availableTokens -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * Stores the given message as the last received message for its topic. The cache is an
     * access-ordered, size-bounded map: once {@link #maxCacheSize} is exceeded, the least
     * recently used entry is evicted automatically.
     *
     * @param message the message to cache, keyed by {@link MqttMessage#getTopic()}
     */
    private void updateCache(MqttMessage message) {
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

            if (MqttTopicMatcher.matches(message.getTopic(), topicFilter)) {
                for (Consumer<MqttMessage> listener : entry.getValue()) {
                    try {
                        listener.accept(message);
                    } catch (Exception e) {
                        LOGGER.error("Error dispatching message to listener for topic {}",
                            message.getTopic(), e);
                    }
                }
            }
        }
    }

    /**
     * Returns the last message received for the given exact topic, if caching is enabled and a
     * message has been received and retained for it.
     *
     * @param topic the exact topic to look up
     * @return the last cached {@link MqttMessage} for {@code topic}, or {@code null} if none is
     *         cached
     */
    public MqttMessage getLastMessage(String topic) {
        return lastMessageCache.get(topic);
    }

    /**
     * Returns every cached last message whose topic matches the given topic filter, ordered by
     * {@link MqttMessage#getReceivedAt()}. Unlike {@link #getLastMessage(String)}, which requires
     * an exact topic, this allows lookups by the same wildcard filter a consumer subscribed with
     * (e.g. {@code sensors/#}).
     *
     * @param topicFilter the topic filter (possibly containing {@code +}/{@code #} wildcards) to
     *                    match cached topics against
     * @return the matching cached messages, ordered by receive time; empty if none match
     */
    public List<MqttMessage> getLastMessages(String topicFilter) {
        List<MqttMessage> matches = new ArrayList<>();
        synchronized (lastMessageCache) {
            for (MqttMessage message : lastMessageCache.values()) {
                if (MqttTopicMatcher.matches(message.getTopic(), topicFilter)) {
                    matches.add(message);
                }
            }
        }
        matches.sort(Comparator.comparing(MqttMessage::getReceivedAt));
        return matches;
    }

    /**
     * Re-issues broker subscriptions for every topic filter currently tracked by the router
     * (whether it has active listeners, was established via
     * {@link #subscribeFromConfig(String, MqttQos)}, or both), each at the QoS it is currently
     * tracked at. Intended to be called after the underlying MQTT client reconnects, since broker
     * subscriptions do not survive a disconnect.
     */
    public void resubscribeAll() {
        Map<String, MqttQos> snapshot;
        synchronized (filterQos) {
            snapshot = new HashMap<>(filterQos);
        }

        LOGGER.info("Re-subscribing to {} topic filters after reconnect", snapshot.size());

        for (Map.Entry<String, MqttQos> entry : snapshot.entrySet()) {
            subscribeOnBroker(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Get statistics
     *
     * @return a snapshot of the router's current runtime statistics
     */
    public RouterStats getStats() {
        return new RouterStats(
            filterQos.size(),
            listeners.values().stream().mapToInt(List::size).sum(),
            lastMessageCache.size(),
            messagesReceivedCount.get(),
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
         * @param topicFilters     the number of distinct topic filters currently subscribed on the
         *                         broker
         * @param totalListeners   the total number of listeners registered across all topic filters
         * @param cachedMessages   the number of topics currently present in the last-message cache
         * @param messagesReceived the cumulative number of messages actually received (i.e. not
         *                         dropped by the rate limiter)
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
         * @return the number of distinct topic filters currently subscribed on the broker
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
         * @return the cumulative number of messages actually received (i.e. not dropped by the
         *         rate limiter)
         */
        public long getMessagesReceived() { return messagesReceived; }

        /**
         * @return the cumulative number of messages dropped due to rate limiting
         */
        public long getMessagesDropped() { return messagesDropped; }
    }
}
