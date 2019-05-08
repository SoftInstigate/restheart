/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.extensions;

import java.util.function.Consumer;
import org.bson.BsonDocument;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ExtensionRecord {
    private final String name;
    private final String description;
    private final String className;
    private final Consumer<BsonDocument> instance;
    private final BsonDocument confArgs;

    public ExtensionRecord(String name,
            String description,
            String className,
            Consumer<BsonDocument> instance,
            BsonDocument confArgs) {
        this.name = name;
        this.description = description;
        this.instance = instance;
        this.className = className;
        this.confArgs = confArgs;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the className
     */
    public String getClassName() {
        return className;
    }

    /**
     * @return the disabled
     */
    public boolean isDisabled() {
        return getConfArgs() == null 
                ? false 
                : getConfArgs().containsKey("disabled");
    }

    /**
     * @return the confArgs
     */
    public BsonDocument getConfArgs() {
        return confArgs;
    }

    /**
     * @return the instance
     */
    public Consumer<BsonDocument> getInstance() {
        return instance;
    }
}
