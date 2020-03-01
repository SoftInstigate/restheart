/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.handlers;

import org.restheart.handlers.PipelinedHandler;

/**
 * @deprecated use PipelinedHandler
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@Deprecated
public abstract class PipedHttpHandler extends PipelinedHandler {

    /**
     * Creates a default instance of PipedHttpHandler with next = null
     */
    public PipedHttpHandler() {
        super(null);
    }

    /**
     * Creates an instance of PipedHttpHandler with specified next handler
     * 
     * @param next the next handler in this chain
     */
    public PipedHttpHandler(PipelinedHandler next) {
        super(next);
    }
    
    /**
     * set the next PipedHttpHandler
     * @param next
     */
    protected void setNext(PipedHttpHandler next) {
        super.setNext(next);
    }
}
