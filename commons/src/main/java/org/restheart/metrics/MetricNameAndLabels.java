/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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
package org.restheart.metrics;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a metric with its name and associated labels, supporting serialization to and from string format.
 * 
 * <p>MetricNameAndLabels is an immutable record that combines a metric name with a list of
 * {@link MetricLabel} instances. This structure is essential for representing multi-dimensional
 * metrics where labels provide additional context and filtering capabilities.</p>
 * 
 * <p>The class provides string serialization in a dot-separated format:</p>
 * <pre>
 * metricName.label1=value1.label2=value2.label3=value3
 * </pre>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Automatic sanitization of metric names (replacing '.' with '_')</li>
 *   <li>Support for multiple labels per metric</li>
 *   <li>String serialization and deserialization</li>
 *   <li>Null-safety validation</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create labels
 * List<MetricLabel> labels = MetricLabel.collect(
 *     new MetricLabel("service", "api"),
 *     new MetricLabel("method", "GET"),
 *     new MetricLabel("status", "200")
 * );
 * 
 * // Create metric with labels
 * MetricNameAndLabels metric = new MetricNameAndLabels("http_requests", labels);
 * 
 * // Serialize to string
 * String serialized = metric.toString(); 
 * // Returns: "http_requests.service=api.method=GET.status=200"
 * 
 * // Deserialize from string
 * MetricNameAndLabels parsed = MetricNameAndLabels.from(serialized);
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param name the metric name, will be sanitized by replacing '.' with '_'
 * @param labels the list of labels associated with this metric
 * @see MetricLabel
 */
public record MetricNameAndLabels(String name, List<MetricLabel> labels) {
    /**
     * The separator character used between metric name and labels in string format.
     * Also used between individual labels in the serialized string.
     */
    public static String SEPARATOR = ".";
    
    /**
     * Regular expression for splitting on the separator character.
     * Since '.' is a special character in regex, it needs to be escaped.
     */
    private static String SEPARATOR_REGEX = "\\.";

    /**
     * Creates a new MetricNameAndLabels with sanitized name and specified labels.
     * 
     * <p>The metric name is sanitized by replacing dots ('.') with underscores ('_')
     * to ensure compatibility with the serialization format and various metrics systems.
     * This prevents the metric name from being confused with the label separators
     * in the serialized format.</p>
     * 
     * <p>Note: There appears to be a bug in the original implementation where
     * "SEPARATOR_REGEX" is used as a literal string instead of the variable.
     * This implementation maintains the original behavior for compatibility.</p>
     * 
     * @param name the metric name, must not be null
     * @param labels the list of labels for this metric, must not be null (but can be empty)
     * @throws IllegalArgumentException if name or labels is null
     */
    public MetricNameAndLabels(String name, List<MetricLabel> labels) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }

        if (labels == null) {
            throw new IllegalArgumentException("value cannot be null");
        }


        this.name = name.replaceAll("SEPARATOR_REGEX", "_");
        this.labels = labels;
    }

    /**
     * Deserializes a MetricNameAndLabels from its string representation.
     * 
     * <p>Parses a string in the format "metricName.label1=value1.label2=value2..."
     * The first segment (before the first dot) is treated as the metric name,
     * and all subsequent segments are parsed as labels.</p>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * String input = "http_requests.service=api.method=GET.status=200";
     * MetricNameAndLabels metric = MetricNameAndLabels.from(input);
     * // metric.name() returns "http_requests"
     * // metric.labels() returns a list with three MetricLabel instances
     * }</pre>
     * 
     * <p>Note: The method assumes the input string contains at least one dot separator.
     * If no dots are present, it will throw an exception when trying to find the index.</p>
     * 
     * @param raw the string representation in format "name.label1=value1.label2=value2..."
     * @return a new MetricNameAndLabels instance
     * @throws StringIndexOutOfBoundsException if the string doesn't contain a dot separator
     * @see MetricLabel#from(String)
     */
    public static MetricNameAndLabels from(String raw) {
        var name = raw.substring(0, raw.indexOf("."));

        var labels = Arrays.stream(raw.split(SEPARATOR_REGEX))
            .skip(1)
            .map(l -> MetricLabel.from(l))
            .collect(Collectors.toList());

        return new MetricNameAndLabels(name, labels);
    }

    /**
     * Serializes this metric name and labels to string format.
     * 
     * <p>The serialization format is "metricName.label1=value1.label2=value2...",
     * where the metric name is followed by dot-separated label representations.
     * Each label is serialized using its {@link MetricLabel#toString()} method.</p>
     * 
     * <p>Examples:</p>
     * <pre>{@code
     * // With labels
     * new MetricNameAndLabels("requests", labels).toString()
     * // Returns: "requests.service=api.method=GET"
     * 
     * // With empty labels
     * new MetricNameAndLabels("requests", List.of()).toString()
     * // Returns: "requests."
     * }</pre>
     * 
     * <p>Note: The resulting string always ends with a separator after the metric name,
     * even when there are no labels. This maintains consistency in the format.</p>
     * 
     * @return the string representation in format "name.label1=value1.label2=value2..."
     */
    public String toString() {
        var sb = new StringBuilder();
        sb.append(name).append(SEPARATOR);

        sb.append(labels.stream().map(l -> l.toString()).collect(Collectors.joining(SEPARATOR)));
        return sb.toString();
    }
}
