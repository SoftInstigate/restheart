/*
 * RESTHeart - the data REST API server
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
package org.restheart.handlers.metadata;

import org.restheart.hal.metadata.RepresentationTransformer;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ResponseScriptMetadataHandler extends AbstractScriptMetadataHandler {
    /**
     * Creates a new instance of ResponseScriptMetadataHandler
     *
     * @param next
     */
    public ResponseScriptMetadataHandler(PipedHttpHandler next) {
        super(next);
        MYPHASE = RepresentationTransformer.PHASE.RESPONSE;
    }
    
    @Override
    boolean canCollRepresentationTransformersAppy(RequestContext context) {
        return (context.getMethod() == RequestContext.METHOD.GET
                && (context.getType() == RequestContext.TYPE.DOCUMENT || context.getType() == RequestContext.TYPE.COLLECTION)
                && context.getCollectionProps().containsField(RepresentationTransformer.RTLS_ELEMENT_NAME));
    }

    @Override
    boolean canDBRepresentationTransformersAppy(RequestContext context) {
        return (context.getMethod() == RequestContext.METHOD.GET
                && (context.getType() == RequestContext.TYPE.DB || context.getType() == RequestContext.TYPE.COLLECTION)
                && context.getDbProps().containsField(RepresentationTransformer.RTLS_ELEMENT_NAME));
    }
}
