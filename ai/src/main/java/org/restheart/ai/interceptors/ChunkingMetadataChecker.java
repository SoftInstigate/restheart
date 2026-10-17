/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.interceptors;

import org.restheart.ai.util.BucketChunkingConfig;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

/**
 * Refuses malformed {@code chunking} bucket metadata when it is written, rather than at the first
 * upload that meets it: a rule with no {@code target-collection}, an unknown key in a rule or in
 * its filter, a filter value of the wrong kind. The answer is a {@code 400} naming the rule. See
 * {@link BucketChunkingConfig#invalidReason}.
 */
@RegisterPlugin(
        name = "chunkingMetadataChecker",
        description = "validates the 'chunking' metadata of a GridFS bucket when it is written",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
        requiresContent = true)
public class ChunkingMetadataChecker implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var reason = BucketChunkingConfig.invalidReason(request.getContent().asDocument().get(BucketChunkingConfig.CHUNKING));
        if (reason != null) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, reason);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && (request.isPut() || request.isPatch())
                && request.isFilesBucket()
                && request.getContent() != null
                && request.getContent().isDocument()
                && request.getContent().asDocument().containsKey(BucketChunkingConfig.CHUNKING);
    }
}
