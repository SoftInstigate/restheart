/*
 * RESTHeart - the data REST API server
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
package org.restheart.db;

import java.io.InputStream;
import org.apache.tika.Tika;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class GridFsDAOTest {
    
    public static final String FILENAME = "RESTHeart_documentation.pdf";
    
    public GridFsDAOTest() {
    }

    @Test
    public void testDetectMediatype() throws Exception {
        System.out.println("testDetectMediatype");
        InputStream is = GridFsDAOTest.class.getResourceAsStream("/" + FILENAME);
        Tika tika = new Tika();
        assertEquals("application/pdf", tika.detect(is));
    }    
}
