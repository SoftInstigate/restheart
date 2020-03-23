/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
