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
package org.restheart.handlers.exchange;

import java.util.Objects;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PipelineBranchInfo {
    public enum PIPELINE_BRANCH {
        SERVICE, PROXY, STATIC_RESOURCE
    };
    
    private final PIPELINE_BRANCH branch;
    private final String name;
    private final String uri;
    
    public PipelineBranchInfo(PIPELINE_BRANCH branch, String name, String uri) {
        Objects.requireNonNull(branch, "argument 'branch' cannot be null");
        Objects.requireNonNull(uri, "argument 'uri' cannot be null");
        
        this.branch = branch;
        this.name = name;
        this.uri = uri;
    }

    /**
     * @return the branch
     */
    public PIPELINE_BRANCH getBranch() {
        return branch;
    }

    /**
     * @return the name, can be null
     */
    public String getName() {
        return name;
    }

    /**
     * @return the uri
     */
    public String getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return "PipelineBranchInfo("
                .concat("branch=").concat(this.branch.name())
                .concat(", uri=").concat(uri)
                .concat(", name=").concat(name == null ? "<unnamed>" : name)
                .concat(")");
    }
}
