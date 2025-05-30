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
package org.restheart.exchange;

import java.util.Objects;

import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.utils.URLUtils;

/**
 * Stores information about the request processing pipeline in RESTHeart.
 * <p>
 * This class encapsulates metadata about which pipeline component is handling
 * a specific HTTP request. RESTHeart uses different pipeline types to process
 * requests: services for dynamic content, proxies for forwarding requests,
 * and static resource handlers for file serving.
 * </p>
 * <p>
 * Pipeline information is essential for:
 * <ul>
 *   <li>Request routing and handler selection</li>
 *   <li>Security policy application</li>
 *   <li>Interceptor chain configuration</li>
 *   <li>Response processing decisions</li>
 *   <li>Logging and monitoring</li>
 * </ul>
 * </p>
 * <p>
 * Example pipeline configurations:
 * <ul>
 *   <li>MongoDB Service: {@code PipelineInfo(type=SERVICE, uri="/", name="mongo")}</li>
 *   <li>GraphQL Service: {@code PipelineInfo(type=SERVICE, uri="/graphql", name="graphql")}</li>
 *   <li>Static Files: {@code PipelineInfo(type=STATIC_RESOURCE, uri="/static", name="staticResourceHandler")}</li>
 *   <li>Proxy: {@code PipelineInfo(type=PROXY, uri="/api", name="apiProxy")}</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PipelineInfo {
    /**
     * Enumeration of pipeline types supported by RESTHeart.
     * <p>
     * Each type represents a different category of request processing:
     * </p>
     * <ul>
     *   <li><strong>SERVICE</strong> - Dynamic content generation (MongoDB, GraphQL, custom services)</li>
     *   <li><strong>PROXY</strong> - Request forwarding to backend services</li>
     *   <li><strong>STATIC_RESOURCE</strong> - Static file serving (HTML, CSS, JS, images)</li>
     * </ul>
     */
    public enum PIPELINE_TYPE {
        /** Service pipeline for dynamic content generation and API operations. */
        SERVICE, 
        
        /** Proxy pipeline for forwarding requests to backend services. */
        PROXY, 
        
        /** Static resource pipeline for serving files from the filesystem. */
        STATIC_RESOURCE
    };

    /** The type of pipeline handling this request. */
    private final PIPELINE_TYPE type;
    
    /** The URI pattern that this pipeline matches against. */
    private final String uri;
    
    /** The policy used for matching URIs against this pipeline. */
    private final MATCH_POLICY matchPolicy;
    
    /** The name identifier for this specific pipeline instance. */
    private final String name;

    /**
     * Constructs a new PipelineInfo with all parameters specified.
     * <p>
     * This constructor allows full control over all pipeline configuration aspects,
     * including the URI matching policy. The URI is automatically normalized by
     * removing trailing slashes for consistent matching behavior.
     * </p>
     *
     * @param type the pipeline type (SERVICE, PROXY, or STATIC_RESOURCE)
     * @param uri the URI pattern that this pipeline should match against
     * @param matchPolicy the policy for matching URIs (PREFIX, EXACT, etc.)
     * @param name the unique name identifier for this pipeline instance
     * @throws NullPointerException if type or uri is null
     */
    public PipelineInfo(PIPELINE_TYPE type, String uri, MATCH_POLICY matchPolicy, String name) {
        Objects.requireNonNull(type, "argument 'type' cannot be null");
        Objects.requireNonNull(uri, "argument 'uri' cannot be null");

        this.type = type;
        this.uri = URLUtils.removeTrailingSlashes(uri);
        this.matchPolicy = matchPolicy;
        this.name = name;
    }

    /**
     * Constructs a new PipelineInfo with default PREFIX matching policy.
     * <p>
     * This convenience constructor creates a pipeline with PREFIX matching policy,
     * which is the most common matching strategy. The URI is automatically normalized
     * by removing trailing slashes for consistent matching behavior.
     * </p>
     *
     * @param type the pipeline type (SERVICE, PROXY, or STATIC_RESOURCE)
     * @param uri the URI pattern that this pipeline should match against
     * @param name the unique name identifier for this pipeline instance
     * @throws NullPointerException if type or uri is null
     */
    public PipelineInfo(PIPELINE_TYPE type, String uri, String name) {
        Objects.requireNonNull(type, "argument 'type' cannot be null");
        Objects.requireNonNull(uri, "argument 'uri' cannot be null");

        this.type = type;
        this.uri = URLUtils.removeTrailingSlashes(uri);
        this.matchPolicy = MATCH_POLICY.PREFIX;
        this.name = name;
    }

    @Override
    public String toString() {
        return "PipelineInfo(type: " + getType()
                + ", uri: " + getUri()
                + ", matchPolicy: " + getUriMatchPolicy()
                + ", name: " + getName() + ")";
    }

    /**
     * Returns the pipeline type for this request handler.
     * <p>
     * The pipeline type determines the category of processing that will be
     * applied to requests matching this pipeline's URI pattern.
     * </p>
     *
     * @return the pipeline type (SERVICE, PROXY, or STATIC_RESOURCE)
     */
    public PIPELINE_TYPE getType() {
        return type;
    }

    /**
     * Returns the URI pattern that this pipeline matches against.
     * <p>
     * The URI pattern is used in conjunction with the match policy to determine
     * whether incoming requests should be handled by this pipeline. Trailing
     * slashes are automatically removed for consistent matching behavior.
     * </p>
     *
     * @return the normalized URI pattern for this pipeline
     */
    public String getUri() {
        return uri;
    }

    /**
     * Returns the URI matching policy for this pipeline.
     * <p>
     * The match policy determines how the URI pattern is compared against
     * incoming request paths. Common policies include:
     * <ul>
     *   <li>PREFIX - matches if request path starts with the URI pattern</li>
     *   <li>EXACT - matches only if request path exactly equals the URI pattern</li>
     *   <li>REGEX - matches using regular expression pattern matching</li>
     * </ul>
     * </p>
     *
     * @return the URI matching policy for this pipeline
     */
    public MATCH_POLICY getUriMatchPolicy() {
        return matchPolicy;
    }

    /**
     * Returns the unique name identifier for this pipeline instance.
     * <p>
     * The name provides a human-readable identifier for this specific pipeline
     * configuration. It's used for logging, monitoring, and configuration
     * management purposes. Names should be unique within the same pipeline type.
     * </p>
     *
     * @return the name identifier for this pipeline, or null if not specified
     */
    public String getName() {
        return name;
    }
}
