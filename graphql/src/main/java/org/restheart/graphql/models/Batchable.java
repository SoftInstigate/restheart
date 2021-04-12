package org.restheart.graphql.models;

import org.bson.BsonValue;
import org.dataloader.DataLoader;

public interface Batchable {

    public DataLoader<BsonValue, BsonValue> getDataloader();

}
