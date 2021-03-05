/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

import com.mongodb.client.FindIterable;
import org.bson.BsonDocument;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SkippedFindIterable {
    private final FindIterable<BsonDocument> findIterable;
    private final int alreadySkipped;

    /**
     *
     * @param findIterable
     * @param alreadySkipped
     */
    public SkippedFindIterable(
            FindIterable<BsonDocument> findIterable,
            int alreadySkipped) {
        this.findIterable = findIterable;
        this.alreadySkipped = alreadySkipped;
    }

    /**
     * @return the alreadySkipped
     */
    public int getAlreadySkipped() {
        return alreadySkipped;
    }

    /**
     * @return the findIterable
     */
    public FindIterable<BsonDocument> getFindIterable() {
        return findIterable;
    }
}
