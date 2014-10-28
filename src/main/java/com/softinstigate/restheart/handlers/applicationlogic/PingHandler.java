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
package com.softinstigate.restheart.handlers.applicationlogic;

import com.softinstigate.restheart.handlers.*;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import java.util.Map;

/**
 *
 * @author uji
 */
public class PingHandler extends ApplicationLogicHandler
{
    private String msg;
    
    
    
    public PingHandler(PipedHttpHandler next, Map<String, Object> args)
    {
        super(next, args);
        
        this.msg = (String) args.get("msg");
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_OK, msg);
    }
}