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
package com.softinstigate.restheart.hal.injectors;

import com.google.common.cache.LoadingCache;
import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.Optional;

/**
 *
 * @author uji
 */
public class DbPropsInjectorHandler extends PipedHttpHandler
{
    /**
     * Creates a new instance of MetadataInjecterHandler
     *
     * @param next
     * @param metadataLocalCacheEnabled
     */
    
    private static boolean cacheEnabled = false;
    
    public DbPropsInjectorHandler(PipedHttpHandler next, boolean metadataLocalCacheEnabled)
    {
        super(next);
        
        cacheEnabled = metadataLocalCacheEnabled;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        if (context.getDBName() != null)
        {
            DBObject dbProps = null;

            if (!cacheEnabled)
            {
                dbProps = DBDAO.getDbProps(context.getDBName());
                
                if (dbProps != null)
                    dbProps.put("@db-props-cached", false);
            }
            else
            {
                LoadingCache<String, Optional<DBObject>> dbPropsCache = LocalCachesSingleton.getInstance().getDbCache();
                
                Optional<DBObject> _dbMetadata = dbPropsCache.getIfPresent(context.getDBName());
                
                if (_dbMetadata != null)
                {
                    if (_dbMetadata.isPresent())
                    {
                        dbProps = _dbMetadata.get();
                        dbProps.put("@db-props-cached", true);
                    }
                    else
                        dbProps = null;
                }
                else
                {
                    _dbMetadata = dbPropsCache.get(context.getDBName());
                    
                    if (_dbMetadata.isPresent())
                    {
                        dbProps = _dbMetadata.get();
                        dbProps.put("@db-props-cached", false);
                    }
                    else
                        dbProps = null;
                }
            }

            context.setDbProps(dbProps);
        }
        
        next.handleRequest(exchange, context);
    }
}