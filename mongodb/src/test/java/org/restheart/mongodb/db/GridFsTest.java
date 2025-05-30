/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
package org.restheart.mongodb.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class GridFsTest {

    private static final Logger LOG = LoggerFactory.getLogger(GridFsTest.class);

    /**
     *
     */
    public static final String FILENAME = "sample.pdf";

    /**
     *
     */
    // @Rule
    // public TestRule watcher = new TestWatcher() {
    // @Override
    // protected void starting(Description description) {
    // LOG.info("executing test {}", description.toString());
    // }
    // };

    /**
     *
     */
    public GridFsTest() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testDetectMediatype() throws Exception {
        InputStream is = GridFsTest.class.getResourceAsStream("/" + FILENAME);
        Tika tika = new Tika();
        assertEquals(tika.detect(is), "application/pdf");
    }

    @Test
    public void testExtractBucket() {
        assertEquals(GridFs.extractBucketName("mybucket.files"), "mybucket");
    }

    /**
    *
    */
    @Test
    public void testExtractBucketWithDots() {
        assertEquals(GridFs.extractBucketName("mybucket.foo.files"), "mybucket.foo");
        assertEquals(GridFs.extractBucketName("mybucket.foo.bar.files"), "mybucket.foo.bar");
    }
}
