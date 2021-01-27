package org.restheart.graphql.cache;

import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.graphql.GraphQLAppDefNotFoundException;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.utils.LambdaUtils;

import java.util.Optional;

public class AppDefinitionLoadingCache {

    private static AppDefinitionLoadingCache instance = null;
    private LoadingCache<String, GraphQLApp> appLoadingCache;
    private static long ttl= 1_00000;
    private static final long MAX_CACHE_SIZE = 1_000;

    private AppDefinitionLoadingCache(){
        this.appLoadingCache = CacheFactory.createLocalLoadingCache(MAX_CACHE_SIZE,
                Cache.EXPIRE_POLICY.AFTER_WRITE, ttl,
                (String key) -> {
                    try {
                        return AppDefinitionLoader.loadAppDefinition(key);
                    } catch (GraphQLIllegalAppDefinitionException e) {
                        LambdaUtils.throwsSneakyExcpetion(e);
                        return null;
                    }
                });
    }

    public static AppDefinitionLoadingCache getInstance(){
        if (instance == null){
            instance = new AppDefinitionLoadingCache();
        }
        return instance;
    }

    public GraphQLApp get(String appName) throws GraphQLAppDefNotFoundException, GraphQLIllegalAppDefinitionException {

        Optional<GraphQLApp> _app = this.appLoadingCache.get(appName);

        if (_app != null && _app.isPresent()){
            return _app.get();
        }
        else{
            try {
                _app = this.appLoadingCache.getLoading(appName);
            } catch (Exception e){
                throw new GraphQLIllegalAppDefinitionException( e.getMessage());
            }

            if(_app != null && _app.isPresent()){
                return _app.get();
            }
            throw new GraphQLAppDefNotFoundException(
                    "Valid configuration for " + appName + " not found. "
            );
        }
    }




}
