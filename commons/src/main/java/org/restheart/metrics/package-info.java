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

/**
 * Provides comprehensive metrics collection and management utilities for RESTHeart.
 * 
 * <p>This package contains the infrastructure for collecting, labeling, and managing
 * metrics throughout the RESTHeart application. It provides a flexible system for
 * adding dimensional data to metrics through labels, tracking authentication failures,
 * and attaching custom metrics to requests.</p>
 * 
 * <h2>Core Components</h2>
 * 
 * <h3>Metric Labels</h3>
 * <p>The package provides a robust labeling system for metrics:</p>
 * <ul>
 *   <li><strong>{@link org.restheart.metrics.MetricLabel}:</strong> Represents a single
 *       name-value pair label with automatic sanitization</li>
 *   <li><strong>{@link org.restheart.metrics.MetricNameAndLabels}:</strong> Combines a
 *       metric name with multiple labels for multi-dimensional metrics</li>
 *   <li><strong>Serialization:</strong> Both types support string serialization for
 *       storage and transmission</li>
 * </ul>
 * 
 * <h3>Metrics Utilities</h3>
 * <p>The {@link org.restheart.metrics.Metrics} class provides utilities for:</p>
 * <ul>
 *   <li>Failed authentication tracking by IP address or proxy headers</li>
 *   <li>Attaching custom labels to requests for enhanced monitoring</li>
 *   <li>Configurable strategies for different deployment scenarios</li>
 * </ul>
 * 
 * <h2>Key Features</h2>
 * 
 * <h3>Label Sanitization</h3>
 * <p>All labels are automatically sanitized to ensure compatibility:</p>
 * <ul>
 *   <li>Equals signs (=) are replaced with underscores in names</li>
 *   <li>Dots (.) are replaced with underscores to avoid hierarchy conflicts</li>
 *   <li>Values are sanitized to maintain metric system compatibility</li>
 * </ul>
 * 
 * <h3>Failed Authentication Tracking</h3>
 * <p>The package supports sophisticated tracking of authentication failures:</p>
 * <ul>
 *   <li><strong>Direct IP:</strong> Track failures by immediate client connection</li>
 *   <li><strong>X-Forwarded-For:</strong> Track through proxy chains with configurable
 *       depth selection</li>
 *   <li><strong>Flexible Configuration:</strong> Adapt to different deployment architectures</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Creating and Using Labels</h3>
 * <pre>{@code
 * // Create individual labels
 * MetricLabel serviceLabel = new MetricLabel("service", "api");
 * MetricLabel versionLabel = new MetricLabel("version", "2.0");
 * 
 * // Collect multiple labels
 * List<MetricLabel> labels = MetricLabel.collect(
 *     serviceLabel,
 *     versionLabel,
 *     new MetricLabel("environment", "production")
 * );
 * 
 * // Create metric with labels
 * MetricNameAndLabels metric = new MetricNameAndLabels("http_requests", labels);
 * 
 * // Serialize for storage/transmission
 * String serialized = metric.toString();
 * // Result: "http_requests.service=api.version=2_0.environment=production"
 * }</pre>
 * 
 * <h3>Configuring Authentication Failure Tracking</h3>
 * <pre>{@code
 * // For direct client connections
 * Metrics.collectFailedAuthBy(Metrics.FAILED_AUTH_KEY.REMOTE_IP);
 * 
 * // For proxy deployments (using X-Forwarded-For)
 * Metrics.collectFailedAuthBy(Metrics.FAILED_AUTH_KEY.X_FORWARDED_FOR);
 * Metrics.xffValueRIndex(1); // Skip last proxy, use second-to-last IP
 * }</pre>
 * 
 * <h3>Attaching Custom Metrics to Requests</h3>
 * <pre>{@code
 * // In a request handler or interceptor
 * public void handle(Request<?> request) {
 *     // Add contextual labels to the request
 *     List<MetricLabel> labels = MetricLabel.collect(
 *         new MetricLabel("customer_tier", "premium"),
 *         new MetricLabel("api_version", request.getHeader("API-Version")),
 *         new MetricLabel("feature_flag", getFeatureFlag(request))
 *     );
 *     
 *     Metrics.attachMetricLabels(request, labels);
 * }
 * }</pre>
 * 
 * <h2>Serialization Format</h2>
 * <p>The package uses a consistent serialization format:</p>
 * <ul>
 *   <li><strong>MetricLabel:</strong> {@code "name=value"}</li>
 *   <li><strong>MetricNameAndLabels:</strong> {@code "metric_name.label1=value1.label2=value2"}</li>
 * </ul>
 * 
 * <h2>Integration with Metrics Systems</h2>
 * <p>The design is compatible with various metrics systems including:</p>
 * <ul>
 *   <li>Prometheus (labels as dimensions)</li>
 *   <li>Graphite (dot-separated hierarchies)</li>
 *   <li>StatsD (tags)</li>
 *   <li>Custom monitoring solutions</li>
 * </ul>
 * 
 * <h2>Best Practices</h2>
 * <ul>
 *   <li><strong>Label Cardinality:</strong> Avoid high-cardinality labels that could
 *       create too many metric series</li>
 *   <li><strong>Consistent Naming:</strong> Use consistent label names across your
 *       application for easier querying</li>
 *   <li><strong>Security:</strong> Don't include sensitive information in metric labels</li>
 *   <li><strong>Performance:</strong> Attach labels early in the request pipeline for
 *       complete coverage</li>
 * </ul>
 * 
 * @since 1.0
 * @see org.restheart.metrics.MetricLabel
 * @see org.restheart.metrics.MetricNameAndLabels
 * @see org.restheart.metrics.Metrics
 * @see com.codahale.metrics.MetricRegistry
 */
package org.restheart.metrics;