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

import org.restheart.ai.util.CollectionEmbeddingConfig;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

/**
 * Refuses malformed {@code vectorSearch} collection metadata when it is written, rather than when
 * a document first meets it.
 *
 * <p>A rule that quietly embeds nothing, or two rules that overwrite each other's vector field,
 * would surface as a search that finds nothing, long after the metadata was written by the one
 * person who could fix it. See {@link CollectionEmbeddingConfig#invalidReason} for what is
 * refused; the answer is a {@code 400} naming the rule.
 */
@RegisterPlugin(
        name = "vectorSearchMetadataChecker",
        description = "validates the 'vectorSearch' collection metadata when it is written",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
        requiresContent = true)
public class VectorSearchMetadataChecker implements MongoInterceptor {

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var reason = CollectionEmbeddingConfig.invalidReason(request.getContent().asDocument().get(CollectionEmbeddingConfig.VECTOR_SEARCH));
        if (reason != null) {
            response.setInError(HttpStatus.SC_BAD_REQUEST, reason);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && (request.isPut() || request.isPatch())
                && request.isCollection()
                && request.getContent() != null
                && request.getContent().isDocument()
                && request.getContent().asDocument().containsKey(CollectionEmbeddingConfig.VECTOR_SEARCH);
    }
}
