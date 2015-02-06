/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import com.mongodb.DBCursor;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SkippedDBCursor {
    private final DBCursor cursor;
    private final int alreadySkipped;

    public SkippedDBCursor(DBCursor cursor, int alreadySkipped) {
        this.cursor = cursor;
        this.alreadySkipped = alreadySkipped;
    }

    /**
     * @return the alreadySkipped
     */
    public int getAlreadySkipped() {
        return alreadySkipped;
    }

    /**
     * @return the cursor
     */
    public DBCursor getCursor() {
        return cursor;
    }
}
