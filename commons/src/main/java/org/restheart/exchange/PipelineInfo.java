/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
 * Stores information about the pipeline that handles the request. For instance,
 * if the request is handled by MongoService, pipeline info is:
 *
 * PipelineInfo(type=SERVICE, uri="/", name="mongo")
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PipelineInfo {
    public enum PIPELINE_TYPE {
        SERVICE, PROXY, STATIC_RESOURCE
    };

    private final PIPELINE_TYPE type;
    private final String uri;
    private final MATCH_POLICY matchPolicy;
    private final String name;

    public PipelineInfo(PIPELINE_TYPE type, String uri, MATCH_POLICY matchPolicy, String name) {
        Objects.requireNonNull(type, "argument 'branch' cannot be null");
        Objects.requireNonNull(uri, "argument 'uri' cannot be null");

        this.type = type;
        this.uri = URLUtils.removeTrailingSlashes(uri);
        this.matchPolicy = matchPolicy;
        this.name = name;
    }

    public PipelineInfo(PIPELINE_TYPE type, String uri, String name) {
        Objects.requireNonNull(type, "argument 'branch' cannot be null");
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
     * @return the type
     */
    public PIPELINE_TYPE getType() {
        return type;
    }

    /**
     * @return the uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * @return the uri
     */
    public MATCH_POLICY getUriMatchPolicy() {
        return matchPolicy;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
}
