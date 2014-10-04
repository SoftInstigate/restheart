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
package com.softinstigate.restheart.handlers.metadata;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author uji
 */
public class MetadataRetrieverHandler extends PipedHttpHandler
{
    private final LoadingCache<String, Map<String, Object>> dbMetadataCache;
    private final LoadingCache<String, Map<String, Object>> collectionMetadataCache;

    /**
     * Creates a new instance of GetCollectionHandler
     *
     * @param next
     * @param metadataLocalCacheEnabled
     * @param metadataLocalCacheTtl
     */
    public MetadataRetrieverHandler(PipedHttpHandler next, boolean metadataLocalCacheEnabled, int metadataLocalCacheTtl)
    {
        super(next);

        if (metadataLocalCacheEnabled)
        {
            this.dbMetadataCache = CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(metadataLocalCacheTtl, TimeUnit.MILLISECONDS).build(
                            new CacheLoader<String, Map<String, Object>>()
                            {

                                @Override
                                public Map<String, Object> load(String key) throws Exception
                                {
                                    return DBDAO.getDbMetaData(key);
                                }
                            });

            this.collectionMetadataCache = CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(metadataLocalCacheTtl, TimeUnit.MILLISECONDS).build(
                            new CacheLoader<String, Map<String, Object>>()
                            {
                                @Override
                                public Map<String, Object> load(String key) throws Exception
                                {
                                    String[] dbNameAndCollectionName = key.split("@@@@");
                                    return CollectionDAO.getCollectionMetadata(dbNameAndCollectionName[0], dbNameAndCollectionName[1]);
                                }
                            });
        }
        else
        {
            this.dbMetadataCache = null;
            this.collectionMetadataCache = null;
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        if (context.getDBName() != null)
        {
            Map<String, Object> dbMetadata = null;
            
            if (RequestContext.METHOD.GET != context.getMethod() || dbMetadataCache == null)
            {
                dbMetadata = DBDAO.getDbMetaData(context.getDBName());
                dbMetadata.put("@metadata-cached", false);
            }
            else
            {
                boolean cached = false;
                
                if (dbMetadataCache.getIfPresent(context.getDBName()) != null)
                {
                   cached = true;
                }
                
                dbMetadata =  dbMetadataCache.get(context.getDBName());
                dbMetadata.put("@metadata-cached", cached);
            }
            
            context.setDbMetadata(dbMetadata);
        }

        if (context.getDBName() != null && context.getCollectionName() != null)
        {
            Map<String, Object> collMetadata = null;

            if (RequestContext.METHOD.GET != context.getMethod() || collectionMetadataCache == null)
            {
                collMetadata = CollectionDAO.getCollectionMetadata(context.getDBName(), context.getCollectionName());
                collMetadata.put("@metadata-cached", false);
            }
            else
            {
                boolean cached = false;
                
                if (collectionMetadataCache.getIfPresent(context.getDBName() + "@@@@" + context.getCollectionName()) != null)
                {
                   cached = true;
                }
                
                collMetadata = collectionMetadataCache.get(context.getDBName() + "@@@@" + context.getCollectionName());
                collMetadata.put("@metadata-cached", cached);
            }
            
            context.setCollectionMetadata(collMetadata);
        }

        next.handleRequest(exchange, context);
    }
}