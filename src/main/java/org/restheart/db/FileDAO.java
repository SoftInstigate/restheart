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

import com.mongodb.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public class FileDAO {
    
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentDAO.class);
    
}
