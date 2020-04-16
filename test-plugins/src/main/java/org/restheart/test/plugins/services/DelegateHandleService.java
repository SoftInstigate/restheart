/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package org.restheart.test.plugins.services;

import java.util.function.BiConsumer;
import org.restheart.handlers.exchange.ByteArrayRequest;
import org.restheart.handlers.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.RegisterPlugin;

/**
 * An idea to further simplify service definition. TBC
 * 
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */

public abstract class DelegateHandleService implements ByteArrayService  {
    BiConsumer<ByteArrayRequest, ByteArrayResponse> handle;

    public DelegateHandleService() {
        this.handle = (r,s) -> {};
    }
    
    public final void delegate(BiConsumer<ByteArrayRequest, ByteArrayResponse> handle) {
        this.handle = handle;
    }

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        this.handle.accept(request, response);
    }
}

@RegisterPlugin(name = "helloWorld", description = "")
class MyService extends DelegateHandleService {
    MyService() {
        delegate((r, s) -> s.setContent("Hello World".getBytes()));  
    }
}