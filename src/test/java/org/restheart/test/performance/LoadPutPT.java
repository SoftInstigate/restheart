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
package org.restheart.test.performance;

/**
 * install ldt from https://github.com/bazhenov/load-test-tool run it from
 * target/class directory (current directory is added to classpath) as follows:
 * <PATH_TO_ldt-assembly-1.1>/bin/ldt.sh -z
 * org.restheart.perftest.LoadPutPT#put -c 20 -n 500 -w 5 -p
 * "url=http://127.0.0.1:8080/testdb/testcoll?page=10&pagesize=5,id=a,pwd=a"
 *
 * @author Andrea Di Cesare
 */
import com.mongodb.BasicDBObject;
import org.restheart.Configuration;
import org.restheart.db.DocumentDAO;
import org.restheart.db.MongoDBClientSingleton;
import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.file.Path;

/**
 *
 * @author Andrea Di Cesare
 */
public class LoadPutPT {
    private URL url;

    private String id;
    private String pwd;
    private boolean printData = false;
    private String db;
    private String coll;
    
    private final Path CONF_FILE = new File("./etc/restheart-integrationtest.yml").toPath();

    /**
     *
     * @param url
     * @throws MalformedURLException
     */
    public void setUrl(String url) throws MalformedURLException {
        this.url = new URL(url);
    }

    /**
     *
     */
    public void prepare() throws Exception {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(id, pwd.toCharArray());
            }
        });

        MongoDBClientSingleton.init(new Configuration(CONF_FILE));
    }

    /**
     *
     * @throws IOException
     */
    public void put() throws IOException {
        throw new RuntimeException("not yet implemented");
    }

    /**
     *
     * @throws IOException
     */
    public void dbdirect() throws IOException {
        BasicDBObject content = new BasicDBObject("random", Math.random()); 
        
        DocumentDAO.upsertDocument(db, coll, null, content, null, false);
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @param pwd the pwd to set
     */
    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    /**
     * @param printData the printData to set
     */
    public void setPrintData(String printData) {
        this.printData = Boolean.valueOf(printData);
    }

    /**
     * @param db the db to set
     */
    public void setDb(String db) {
        this.db = db;
    }

    /**
     * @param coll the coll to set
     */
    public void setColl(String coll) {
        this.coll = coll;
    }
}
