package org.restheart.mongodb;

import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

import com.mongodb.client.MongoClient;

// TODO move this to own submodule
@RegisterPlugin(name = "mclient", description = "provides the MongoClient")
public class MongoClientProvider implements Provider<MongoClient> {
    @Override
    public MongoClient get(PluginRecord<?> caller) {
        return MongoClientSingleton.get().client();
    }
}
