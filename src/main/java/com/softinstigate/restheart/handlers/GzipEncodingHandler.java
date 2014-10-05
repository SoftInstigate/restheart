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

package com.softinstigate.restheart.handlers;

import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class GzipEncodingHandler extends EncodingHandler
{
    private final Logger logger = LoggerFactory.getLogger(GzipEncodingHandler.class);
    
    private boolean forceCompression = false;
    
    /**
     * Creates a new instance of GzipEncodingHandler
     *
     * @param next
     * @param forceCompression if true requests without gzip encoding in Accept-Encoding header will be rejected
     */
    public GzipEncodingHandler(HttpHandler next, boolean forceCompression)
    {
        super(next, new ContentEncodingRepository().addEncodingHandler("gzip", new GzipEncodingProvider(), 50));
        
        this.forceCompression = forceCompression;
    }
    
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        if (forceCompression)
        {
            HeaderValues acceptedEncodings = exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING_STRING);

            for(String values : acceptedEncodings) {
                if (Arrays.stream(values.split(",")).anyMatch((v) -> Headers.GZIP.toString().equals(v)))
                {
                    super.handleRequest(exchange);
                    return;
                }
            }
            
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "Accept-Encoding header must include gzip");
        }
        else
            super.handleRequest(exchange);
    }
}
