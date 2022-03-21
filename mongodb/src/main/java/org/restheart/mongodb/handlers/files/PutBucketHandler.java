/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb.handlers.files;

import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.handlers.collection.PutCollectionHandler;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PutBucketHandler extends PutCollectionHandler {
    /**
     * Creates a new instance of PutBucketHandler
     *
     */
    public PutBucketHandler() {
        super();
    }

    /**
     * Creates a new instance of PutBucketHandler
     *
     * @param next
     */
    public PutBucketHandler(PipelinedHandler next) {
        super(next);
    }
}
