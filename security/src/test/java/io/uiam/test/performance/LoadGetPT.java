/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
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
package io.uiam.test.performance;

/**
 * install ldt from https://github.com/bazhenov/load-test-tool run it from
 * target/class directory (current directory is added to classpath) as follows:
 * <PATH_TO_ldt-assembly-1.1>/bin/ldt.sh -z
 * io.uiam.test.performance.LoadGetPT#get -c 20 -n 500 -w 5 -p
 * "url=http://127.0.0.1:8080/testdb/testcoll?page=10&pagesize=5,id=a,pwd=a"
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LoadGetPT extends AbstractPT {

    private boolean printData = false;
    private String doc;

    private final ConcurrentHashMap<Long, Integer> threadPages
            = new ConcurrentHashMap<>();

    /**
     *
     */
    public void get() throws IOException {
        URLConnection connection = new URL(url).openConnection();

        //connection.setRequestProperty("Accept-Encoding", "gzip");
        InputStream stream = connection.getInputStream();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String data = in.readLine();

            while (data != null) {
                if (printData) {
                }

                data = in.readLine();
            }
        }
    }

    /**
     * @param printData the printData to set
     */
    public void setPrintData(String printData) {
        this.printData = Boolean.valueOf(printData);
    }

    /**
    /**
     * @return the doc
     */
    public String getDoc() {
        return doc;
    }

    /**
     * @param doc the doc to set
     */
    public void setDoc(String doc) {
        this.doc = doc;
    }

}
