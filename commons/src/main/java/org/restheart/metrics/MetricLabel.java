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

package org.restheart.metrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a metric label as a name-value pair that can be serialized to and from string format.
 * 
 * <p>MetricLabel is an immutable record that encapsulates a label for metrics collection systems.
 * Labels are used to add dimensional data to metrics, allowing for more detailed analysis and
 * filtering of metric data. This implementation provides:</p>
 * <ul>
 *   <li>Automatic sanitization of label names and values (replacing '=' and '.' with '_')</li>
 *   <li>String serialization in the format "name=value"</li>
 *   <li>Deserialization from the string format</li>
 *   <li>Null-safety validation for both name and value</li>
 * </ul>
 * 
 * <p>Label sanitization ensures compatibility with various metrics systems by replacing
 * characters that might have special meaning:</p>
 * <ul>
 *   <li>'=' is replaced with '_' to avoid conflicts with the serialization format</li>
 *   <li>'.' is replaced with '_' to ensure compatibility with metric naming conventions</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create a label
 * MetricLabel label = new MetricLabel("environment", "production");
 * 
 * // Serialize to string
 * String serialized = label.toString(); // "environment=production"
 * 
 * // Deserialize from string
 * MetricLabel parsed = MetricLabel.from("environment=production");
 * 
 * // Create multiple labels
 * List<MetricLabel> labels = MetricLabel.collect(
 *     new MetricLabel("service", "api"),
 *     new MetricLabel("version", "1.0.0")
 * );
 * }</pre>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param name the label name, will be sanitized by replacing '=' and '.' with '_'
 * @param value the label value, will be sanitized by replacing '.' with '_'
 */
public record MetricLabel(String name, String value) {
    /**
     * The separator character used in string serialization format.
     * Labels are serialized as "name=value".
     */
    public static String SEPARATOR = "=";

    /**
     * Creates a new MetricLabel with sanitized name and value.
     * 
     * <p>Both name and value are sanitized to ensure compatibility with metrics systems:</p>
     * <ul>
     *   <li>In the name: '=' and '.' are replaced with '_'</li>
     *   <li>In the value: '.' is replaced with '_'</li>
     * </ul>
     * 
     * <p>This sanitization prevents issues with:</p>
     * <ul>
     *   <li>Serialization/deserialization (avoiding '=' in names)</li>
     *   <li>Metric system compatibility (avoiding '.' which might be interpreted as hierarchy)</li>
     * </ul>
     * 
     * @param name the label name, must not be null
     * @param value the label value, must not be null
     * @throws IllegalArgumentException if name or value is null
     */
    public MetricLabel(String name, String value) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }

        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }

        this.name = name.replaceAll("=", "_").replaceAll("\\.", "_");
        this.value = value.replaceAll("\\.", "_");
    }


    /**
     * Serializes this label to string format.
     * 
     * <p>The serialization format is "name=value", where name and value
     * are the sanitized versions of the original inputs.</p>
     * 
     * @return the string representation in format "name=value"
     */
    public String toString() {
        return name.concat(SEPARATOR).concat(value);
    }

    /**
     * Deserializes a MetricLabel from its string representation.
     * 
     * <p>Parses a string in the format "name=value" and creates a new MetricLabel.
     * The name and value will be sanitized according to the rules defined in the constructor.</p>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * MetricLabel label = MetricLabel.from("environment=production");
     * // label.name() returns "environment"
     * // label.value() returns "production"
     * }</pre>
     * 
     * <p>Note: The input string must contain exactly one '=' separator. The method
     * will use the first occurrence of '=' as the separator, so values can contain
     * additional '=' characters.</p>
     * 
     * @param raw the string representation in format "name=value"
     * @return a new MetricLabel instance with sanitized name and value
     * @throws StringIndexOutOfBoundsException if the string doesn't contain a separator
     */
    public static MetricLabel from(String raw) {
        var sepIdx = raw.indexOf(SEPARATOR);
        return new MetricLabel(raw.substring(0, sepIdx), raw.substring(sepIdx+1));
    }

    /**
     * Collects multiple MetricLabel instances into a list.
     * 
     * <p>This is a convenience method for creating a list of labels from varargs.
     * It's particularly useful when configuring metrics with multiple labels:</p>
     * 
     * <pre>{@code
     * List<MetricLabel> labels = MetricLabel.collect(
     *     new MetricLabel("service", "api"),
     *     new MetricLabel("environment", "prod"),
     *     new MetricLabel("region", "us-east-1")
     * );
     * }</pre>
     * 
     * <p>The returned list is a new mutable ArrayList containing all provided labels
     * in the order they were passed.</p>
     * 
     * @param labels varargs of MetricLabel instances to collect
     * @return a new ArrayList containing all provided labels
     */
    public static List<MetricLabel> collect(MetricLabel... labels) {
        var ret = new ArrayList<MetricLabel>();
        for (var label: labels) {
            ret.add(label);
        }
        return ret;
    }
}