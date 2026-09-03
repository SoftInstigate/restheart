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

package org.restheart.mqtt.pipeline;

import java.util.Optional;

import org.restheart.mqtt.model.MqttMessage;

/**
 * Functional interface for processing MQTT messages in a pipeline.
 *
 * Each stage in the pipeline can:
 * - Pass the message through unchanged
 * - Transform the message (return a new instance)
 * - Drop the message (return Optional.empty())
 *
 * When a stage returns empty, the pipeline stops and no further stages execute.
 * This allows for efficient filtering and short-circuit evaluation.
 *
 * Example stages:
 * - FilterStage: Drop messages that don't match criteria
 * - MapStage: Transform message payload
 * - ThrottleStage: Rate limit messages per connection
 * - AggregatorStage: Collect and aggregate messages over time windows
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@FunctionalInterface
public interface MqttEventStage {
    /**
     * Process one MQTT message.
     *
     * @param message The incoming message
     * @return Optional containing the processed message, or empty to drop it
     */
    Optional<MqttMessage> process(MqttMessage message);

    /**
     * Close the stage and flush any buffered messages.
     *
     * @return Optional containing the flushed message, or empty
     */
    default Optional<MqttMessage> close() {
        return Optional.empty();
    }
}