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
package com.softinstigate.restheart;

/**
 * install ldt from https://github.com/bazhenov/load-test-tool
 * run it from target/class directory (current directory is added to classpath) as follows:
 * <PATH_TO_ldt-assembly-1.1>/bin/ldt.sh -z com.softinstigate.restheart.LoadTestRestHeartTask#get -c 20 -n 500 -w 5 -p "url=http://user:user@127.0.0.1:8080/testdb/testcoll?page=10&pagesize=5"
 * @author uji
 */
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

public class LoadTestRestHeartTask
{
    private URL url;
    
    private MongoClient client = null;

    public void setUrl(String url) throws MalformedURLException
    {
        this.url = new URL(url);
    }

    public void prepare()
    {
        Authenticator.setDefault(new Authenticator()
        {
            @Override
            protected PasswordAuthentication getPasswordAuthentication()
            {
                return new PasswordAuthentication("user", "user".toCharArray());
            }
        });
        
        MongoDBClientSingleton.init("127.0.0.1", 27017, "admin", "adminadmin");
        
        this.client = MongoDBClientSingleton.getInstance().getClient();
    }

    public void get() throws IOException
    {
        URLConnection connection = url.openConnection();
        
        connection.setRequestProperty("Accept-Encoding", "gzip");

        InputStream stream = connection.getInputStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        
        try
        {
            while (in.readLine() != null);
        }
        finally
        {
            in.close();
        }
    }
    
    public void dbdirect() throws IOException
    {
        DBCollection coll = CollectionDAO.getCollection("testdb", "testcoll");
        
        CollectionDAO.getCollectionSize(coll);
        CollectionDAO.getCollectionMetadata(coll);
        List<Map<String, Object>> data = CollectionDAO.getCollectionData(coll, 2, 5, null, null, null);
    }
}