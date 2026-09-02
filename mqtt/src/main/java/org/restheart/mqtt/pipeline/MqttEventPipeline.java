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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.restheart.mqtt.model.MqttMessage;

/**
 * Immutable ordered pipeline of MQTT event processing stages.
 *
 * Messages flow through stages sequentially. If any stage returns empty,
 * the pipeline stops and the message is dropped.
 *
 * Example usage:
 * <pre>
 * MqttEventPipeline pipeline = MqttEventPipeline.builder()
 *     .addStage(new FilterStage("$.temperature", "> 25"))
 *     .addStage(new ThrottleStage(10)) // max 10 msg/sec
 *     .addStage(new TumblingWindowAggregator(1000, "avg", "$.value"))
 *     .build();
 *
 * Optional<MqttMessage> result = pipeline.process(message);
 * </pre>
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 */
public class MqttEventPipeline {

    private final List<MqttEventStage> stages;

    /**
     * Create a pipeline with the given stages
     */
    private MqttEventPipeline(List<MqttEventStage> stages) {
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
    }

    /**
     * Create an identity pipeline (no-op, passes all messages through)
     */
    public static MqttEventPipeline identity() {
        return new MqttEventPipeline(Collections.emptyList());
    }

    /**
     * Create a new pipeline builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Process a message through all stages in order.
     *
     * @param message The message to process
     * @return Optional containing the final message, or empty if dropped by any stage
     */
    public Optional<MqttMessage> process(MqttMessage message) {
        Optional<MqttMessage> current = Optional.of(message);

        for (MqttEventStage stage : stages) {
            if (current.isEmpty()) {
                break; // Short-circuit: message was dropped
            }
            current = stage.process(current.get());
        }

        return current;
    }

    /**
     * @return The number of stages in this pipeline
     */
    public int size() {
        return stages.size();
    }

    /**
     * Close all stages in this pipeline and return any messages flushed during teardown,
     * routed through the remaining stages in the pipeline.
     *
     * @return List of flushed messages that successfully completed the pipeline
     */
    public List<MqttMessage> close() {
        List<MqttMessage> flushedMessages = new ArrayList<>();

        for (int i = 0; i < stages.size(); i++) {
            MqttEventStage stage = stages.get(i);
            Optional<MqttMessage> current = stage.close();

            if (current.isPresent()) {
                // Process the flushed message through the remaining stages
                for (int j = i + 1; j < stages.size(); j++) {
                    if (current.isEmpty()) {
                        break;
                    }
                    current = stages.get(j).process(current.get());
                }
                current.ifPresent(flushedMessages::add);
            }
        }

        return flushedMessages;
    }

    /**
     * @return True if this pipeline has no stages (identity pipeline)
     */
    public boolean isEmpty() {
        return stages.isEmpty();
    }

    /**
     * Builder for creating pipelines
     */
    public static class Builder {
        private final List<MqttEventStage> stages = new ArrayList<>();

        /**
         * Add a stage to the pipeline
         */
        public Builder addStage(MqttEventStage stage) {
            if (stage != null) {
                stages.add(stage);
            }
            return this;
        }

        /**
         * Add multiple stages to the pipeline
         */
        public Builder addStages(List<MqttEventStage> stages) {
            if (stages != null) {
                this.stages.addAll(stages);
            }
            return this;
        }

        /**
         * Build the immutable pipeline
         */
        public MqttEventPipeline build() {
            return new MqttEventPipeline(stages);
        }
    }
}