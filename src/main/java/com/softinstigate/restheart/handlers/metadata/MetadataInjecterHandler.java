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
import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import io.undertow.server.HttpServerExchange;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author uji
 */
public class MetadataInjecterHandler extends PipedHttpHandler
{
    private final LoadingCache<String, Optional<DBObject>> dbMetadataCache;
    private final LoadingCache<String, Optional<DBObject>> collectionMetadataCache;

    /**
     * Creates a new instance of MetadataInjecterHandler
     *
     * @param next
     * @param metadataLocalCacheEnabled
     * @param metadataLocalCacheTtl
     */
    public MetadataInjecterHandler(PipedHttpHandler next, boolean metadataLocalCacheEnabled, long metadataLocalCacheTtl)
    {
        super(next);

        if (metadataLocalCacheEnabled)
        {
            CacheBuilder builder = CacheBuilder.newBuilder();

            builder.maximumSize(1000);

            if (metadataLocalCacheTtl > 0)
            {
                builder.expireAfterWrite(metadataLocalCacheTtl, TimeUnit.MILLISECONDS);
            }

            this.dbMetadataCache = builder.build(
                    new CacheLoader<String, Optional<DBObject>>()
                    {
                        @Override
                        public Optional<DBObject> load(String key) throws Exception
                        {
                            return Optional.ofNullable(DBDAO.getDbMetaData(key));
                        }
                    });

            this.collectionMetadataCache = builder.build(
                    new CacheLoader<String, Optional<DBObject>>()
                    {
                        @Override
                        public Optional<DBObject> load(String key) throws Exception
                        {
                            String[] dbNameAndCollectionName = key.split("@@@@");
                            return Optional.ofNullable(CollectionDAO.getCollectionMetadata(dbNameAndCollectionName[0], dbNameAndCollectionName[1]));
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
            DBObject dbMetadata = null;

            if (dbMetadataCache == null)
            {
                dbMetadata = DBDAO.getDbMetaData(context.getDBName());
                
                if (dbMetadata != null)
                    dbMetadata.put("@metadata-cached", false);
            }
            else
            {
                Optional<DBObject> _dbMetadata = dbMetadataCache.getIfPresent(context.getDBName());
                
                if (_dbMetadata != null)
                {
                    if (_dbMetadata.isPresent())
                    {
                        dbMetadata = _dbMetadata.get();
                        dbMetadata.put("@metadata-cached", true);
                    }
                    else
                        dbMetadata = null;
                }
                else
                {
                    _dbMetadata = dbMetadataCache.get(context.getDBName());
                    
                    if (_dbMetadata.isPresent())
                    {
                        dbMetadata = _dbMetadata.get();
                        dbMetadata.put("@metadata-cached", false);
                    }
                    else
                        dbMetadata = null;
                }
            }

            context.setDbProps(dbMetadata);
        }

        if (context.getDBName() != null && context.getCollectionName() != null)
        {
            DBObject collMetadata = null;

            if (collectionMetadataCache == null)
            {
                collMetadata = CollectionDAO.getCollectionMetadata(context.getDBName(), context.getCollectionName());
                
                if (collMetadata != null)
                    collMetadata.put("@metadata-cached", false);
            }
            else
            {
                Optional<DBObject> _collMetadata = collectionMetadataCache.getIfPresent(context.getDBName() + "@@@@" + context.getCollectionName());
                
                if (_collMetadata != null)
                {
                    if (_collMetadata.isPresent())
                    {
                        collMetadata = _collMetadata.get();
                        collMetadata.put("@metadata-cached", true);
                    }
                    else
                        collMetadata = null;
                }
                else
                {
                    _collMetadata = collectionMetadataCache.get(context.getDBName() + "@@@@" + context.getCollectionName());
                    
                    if (_collMetadata.isPresent())
                    {
                        collMetadata = _collMetadata.get();
                        collMetadata.put("@metadata-cached", false);
                    }
                    else
                        collMetadata = null;
                }
            }

            context.setCollectionProps(collMetadata);
        }

        next.handleRequest(exchange, context);
    }
}