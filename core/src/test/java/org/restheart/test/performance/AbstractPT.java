/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.test.performance;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Executor;
import org.restheart.ConfigurationException;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_URI_KEY;
import org.restheart.mongodb.db.MongoClientSingleton;
import static org.restheart.test.integration.AbstactIT.MONGO_URI;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class AbstractPT {

    /**
     *
     */
    protected String url;

    /**
     *
     */
    protected String mongoUri;

    /**
     *
     */
    protected String id;

    /**
     *
     */
    protected String pwd;

    /**
     *
     */
    protected String db;

    /**
     *
     */
    protected String coll;

    /**
     *
     */
    protected Executor httpExecutor;

    /**
     *
     */
    public void prepare() {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(id, pwd.toCharArray());
            }
        });

        StringBuilder ymlSB = new StringBuilder();

        if (mongoUri != null) {
            ymlSB.append(MONGO_URI_KEY)
                    .append(": ")
                    .append(mongoUri)
                    .append("\n");
        }

        try {
            MongoClientSingleton.init(MONGO_URI, null);
        } catch (ConfigurationException ex) {
            System.exit(-1);
        }

        httpExecutor = Executor.newInstance();

        // for perf test better to disable the restheart security
        if (url != null && id != null && pwd != null) {
            String host = "127.0.0.1";
            int port = 8080;
            String scheme = "http";

            try {
                URI uri = new URI(url);

                host = uri.getHost();
                port = uri.getPort();
                scheme = uri.getScheme();
            } catch (URISyntaxException ex) {
                Logger.getLogger(LoadPutPT.class.getName())
                        .log(Level.SEVERE, null, ex);
            }

            httpExecutor.authPreemptive(new HttpHost(host, port, scheme))
                    .auth(new HttpHost(host), id, pwd);
        }
    }

    /**
     * @param url the url to set
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @param mongoUri the mongoHost to set
     */
    public void setMongoUri(String mongoUri) {
        this.mongoUri = mongoUri;
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
