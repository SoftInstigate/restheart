package org.restheart.graphql.models;

import org.bson.BsonValue;
import org.dataloader.DataLoader;

import java.util.Map;

public interface Batchable {

    public DataLoader<BsonValue, BsonValue> getDataloader();

}
