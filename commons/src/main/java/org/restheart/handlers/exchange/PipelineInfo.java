/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.exchange;

import org.restheart.handlers.exchange.PipelineBranchInfo.PIPELINE_BRANCH;
import org.restheart.utils.URLUtils;

/**
 * simple class to hold a mongo-mount configuration entry
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PipelineInfo {
    private final PIPELINE_BRANCH type;
    private final String uri;
    private final String name;

    public PipelineInfo(PIPELINE_BRANCH type, String uri, String name) {
        this.type = type;
        this.uri = URLUtils.removeTrailingSlashes(uri);
        this.name = name;
    }

    @Override
    public String toString() {
        return "ResourceInfo(type: " + type
                + ", uri: " + uri
                + ", name: " + name + ")";
    }
}
