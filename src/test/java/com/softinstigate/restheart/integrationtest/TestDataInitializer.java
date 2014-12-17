/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.softinstigate.restheart.integrationtest;

import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.db.DocumentDAO;
import com.softinstigate.restheart.db.IndexDAO;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import static com.softinstigate.restheart.integrationtest.AbstactIT.conf;
import java.util.List;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare
 */
public class TestDataInitializer extends AbstactIT {

    private static final Logger logger = LoggerFactory.getLogger(TestDataInitializer.class);

    public static void main(String[] args) throws Throwable {
        TestDataInitializer me = new TestDataInitializer();

        me.setUp();
        me.deleteExistingData();
        logger.info("existing data deleted");
        me.createTestData();
        logger.info("test data created");
    }

    private void deleteExistingData() {
        MongoDBClientSingleton.init(conf);

        List<String> databases = MongoDBClientSingleton.getInstance().getClient().getDatabaseNames();
        
        if (databases.contains(dbName)) {
            MongoDBClientSingleton.getInstance().getClient().dropDatabase(dbName);
        }

        if (databases.contains(dbTmpName)) {
            MongoDBClientSingleton.getInstance().getClient().dropDatabase(dbTmpName);
        }
    }

    private void createTestData() {
        DBDAO.upsertDB(dbName, dbProps, new ObjectId(), false);
        CollectionDAO.upsertCollection(dbName, collection1Name, coll1Props, new ObjectId(), false, false);
        CollectionDAO.upsertCollection(dbName, collection2Name, coll2Props, new ObjectId(), false, false);
        CollectionDAO.upsertCollection(dbName, docsCollectionName, docsCollectionProps, new ObjectId(), false, false);

        for (String index : docsCollectionIndexesStrings) {
            IndexDAO.createIndex(dbName, docsCollectionName, ((DBObject) JSON.parse(index)), null);
        }

        DocumentDAO.upsertDocument(dbName, collection1Name, document1Id, document1Props, new ObjectId(), false);
        DocumentDAO.upsertDocument(dbName, collection2Name, document2Id, document2Props, new ObjectId(), false);

        for (String doc : docsPropsStrings) {
            DocumentDAO.upsertDocument(dbName, docsCollectionName, new ObjectId().toString(), ((DBObject) JSON.parse(doc)), new ObjectId(), false);
        }
    }
}
