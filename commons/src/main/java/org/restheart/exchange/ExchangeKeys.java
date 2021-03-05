/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.exchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface ExchangeKeys {
    // query parameters

    /**
     *
     */
    public static final String PAGE_QPARAM_KEY = "page";

    /**
     *
     */
    public static final String PAGESIZE_QPARAM_KEY = "pagesize";

    /**
     *
     */
    public static final String COUNT_QPARAM_KEY = "count";

    /**
     *
     */
    public static final String SORT_BY_QPARAM_KEY = "sort_by";

    /**
     *
     */
    public static final String SORT_QPARAM_KEY = "sort";

    /**
     *
     */
    public static final String FILTER_QPARAM_KEY = "filter";

    /**
     *
     */
    public static final String HINT_QPARAM_KEY = "hint";

    /**
     *
     */
    public static final String AGGREGATION_VARIABLES_QPARAM_KEY = "avars";

    /**
     *
     */
    public static final String KEYS_QPARAM_KEY = "keys";

    /**
     *
     */
    public static final String EAGER_CURSOR_ALLOCATION_POLICY_QPARAM_KEY = "eager";

    /**
     *
     */
    public static final String HAL_QPARAM_KEY = "hal";

    /**
     *
     */
    public static final String DOC_ID_TYPE_QPARAM_KEY = "id_type";

    /**
     *
     */
    public static final String ETAG_CHECK_QPARAM_KEY = "checkEtag";

    /**
     *
     */
    public static final String SHARDKEY_QPARAM_KEY = "shardkey";

    /**
     *
     */
    public static final String NO_PROPS_KEY = "np";

    /**
     *
     */
    public static final String REPRESENTATION_FORMAT_KEY = "rep";

    /**
     *
     */
    public static final String CLIENT_SESSION_KEY = "sid";

    /**
     *
     */
    public static final String TXNID_KEY = "txn";

    /**
     *
     */
    public static final String JSON_MODE_QPARAM_KEY = "jsonMode";

    /**
     *
     */
    public static final String NO_CACHE_QPARAM_KEY = "nocache";

    /**
     *
     */
    public static final String WRITE_MODE_QPARAM_KEY = "writeMode";

    /**
     *
     */
    public static final String WRITE_MODE_SHORT_QPARAM_KEY = "wm";

    // matadata

    /**
     *
     */
    public static final String ETAG_DOC_POLICY_METADATA_KEY = "etagDocPolicy";

    /**
     *
     */
    public static final String ETAG_POLICY_METADATA_KEY = "etagPolicy";

    // special resource names

    /**
     *
     */
    public static final String SYSTEM = "system.";

    /**
     *
     */
    public static final String LOCAL = "local";

    /**
     *
     */
    public static final String ADMIN = "admin";

    /**
     *
     */
    public static final String CONFIG = "config";

    /**
     *
     */
    public static final String _METRICS = "_metrics";

    /**
     *
     */
    public static final String _SIZE = "_size";

    /**
     *
     */
    public static final String _META = "_meta";

    /**
     *
     */
    public static final String _SESSIONS = "_sessions";

    /**
     *
     */
    public static final String _TRANSACTIONS = "_txns";

    /**
     *
     */
    public static final String FS_CHUNKS_SUFFIX = ".chunks";

    /**
     *
     */
    public static final String FS_FILES_SUFFIX = ".files";

    /**
     *
     */
    public static final String META_COLLNAME = "_properties";

    /**
     *
     */
    public static final String DB_META_DOCID = "_properties";

    /**
     *
     */
    public static final String COLL_META_DOCID_PREFIX = "_properties.";

    /**
     *
     */
    public static final String RESOURCES_WILDCARD_KEY = "*";

    /**
     *
     */
    public static final String _INDEXES = "_indexes";

    /**
     *
     */
    public static final String _SCHEMAS = "_schemas";

    /**
     *
     */
    public static final String _AGGREGATIONS = "_aggrs";

    /**
     *
     */
    public static final String _STREAMS = "_streams";

    /**
     *
     */
    public static final String BINARY_CONTENT = "binary";

    /**
     *
     */
    public static final String MAX_KEY_ID = "_MaxKey";

    /**
     *
     */
    public static final String MIN_KEY_ID = "_MinKey";

    /**
     *
     */
    public static final String NULL_KEY_ID = "_null";

    /**
     *
     */
    public static final String TRUE_KEY_ID = "_true";

    /**
     *
     */
    public static final String FALSE_KEY_ID = "_false";

    // other constants

    /**
     *
     */
    public static final String SLASH = "/";

    /**
     *
     */
    public static final String PATCH = "PATCH";

    /**
     *
     */
    public static final String UNDERSCORE = "_";

    /**
     *
     */
    public static final String PROPERTIES = "properties";

    /**
     *
     */
    public static final String FILE_METADATA = "metadata";

    /**
     *
     */
    public static final String _ID = "_id";

    /**
     *
     */
    public static final String CONTENT_TYPE = "contentType";

    /**
     *
     */
    public static final String FILENAME = "filename";

    /**
     *
     */
    public static final String NUL = Character.toString('\0');

    /**
     *
     */
    public enum TYPE {

        /**
         *
         */
        INVALID,

        /**
         *
         */
        ROOT,

        /**
         *
         */
        ROOT_SIZE,

        /**
         *
         */
        DB,

        /**
         *
         */
        DB_SIZE,

        /**
         *
         */
        DB_META,

        /**
         *
         */
        CHANGE_STREAM,

        /**
         *
         */
        COLLECTION,

        /**
         *
         */
        COLLECTION_SIZE,

        /**
         *
         */
        COLLECTION_META,

        /**
         *
         */
        DOCUMENT,

        /**
         *
         */
        COLLECTION_INDEXES,

        /**
         *
         */
        INDEX,

        /**
         *
         */
        FILES_BUCKET,

        /**
         *
         */
        FILES_BUCKET_SIZE,

        /**
         *
         */
        FILES_BUCKET_META,

        /**
         *
         */
        FILE,

        /**
         *
         */
        FILE_BINARY,

        /**
         *
         */
        AGGREGATION,

        /**
         *
         */
        SCHEMA,

        /**
         *
         */
        SCHEMA_STORE,

        /**
         *
         */
        SCHEMA_STORE_SIZE,

        /**
         *
         */
        SCHEMA_STORE_META,

        /**
         *
         */
        BULK_DOCUMENTS,

        /**
         *
         */
        METRICS,

        /**
         *
         */
        SESSION,

        /**
         *
         */
        SESSIONS,

        /**
         *
         */
        TRANSACTIONS,

        /**
         *
         */
        TRANSACTION
    }

    /**
     *
     */
    public enum METHOD {
        /**
         *
         */
        GET,

        /**
         *
         */
        POST,

        /**
         *
         */
        PUT,

        /**
         *
         */
        DELETE,

        /**
         *
         */
        PATCH,

        /**
         *
         */
        OPTIONS,

        /**
         *
         */
        OTHER
    }

    /**
     *
     */
    public enum DOC_ID_TYPE {

        /**
         * ObjectId
         */
        OID,

        /**
         * String eventually converted to ObjectId in case ObjectId.isValid() is true
         */
        STRING_OID,

        /**
         * String
         */
        STRING,

        /**
         * any Number (including mongodb NumberLong)
         */
        NUMBER,

        /**
         * Date
         */
        DATE,

        /**
         * org.bson.types.MinKey;
         */
        MINKEY,

        /**
         * org.bson.types.MaxKey
         */
        MAXKEY,

        /**
         * null
         */
        NULL,

        /**
         * boolean
         */
        BOOLEAN
    }

    /**
     *
     */
    public enum HAL_MODE {

        /**
         * full mode
         */
        FULL,

        /**
         * alias for full
         */
        F,

        /**
         * compact mode
         */
        COMPACT,

        /**
         * alias for compact
         */
        C
    }

    /**
     *
     */
    public enum ETAG_CHECK_POLICY {

        /**
         * always requires the etag, return PRECONDITION FAILED if missing
         */
        REQUIRED,

        /**
         * only requires the etag for DELETE, return PRECONDITION FAILED if missing
         */
        REQUIRED_FOR_DELETE,

        /**
         * checks the etag only if provided by client via If-Match header
         */
        OPTIONAL
    }

    /**
     *
     */
    public enum REPRESENTATION_FORMAT {

        /**
         *
         */
        HAL, // Hypertext Application Language

        /**
         *
         */
        SHAL, // Simplified HAL with children as direct elements of _embedded array

        // root and dbs represeted as an array of children's ids,
        // collection as arrays of document objects
        // documents as objects

        /**
         *
         */
        STANDARD,

        /**
         *
         */
        S, // Alias for STANDARD

        /**
         *
         */
        PLAIN_JSON,

        /**
         *
         */
        PJ, // Aliases for SHAL
    }

    /**
     *
     */
    public enum EAGER_CURSOR_ALLOCATION_POLICY {

        /**
         *
         */
        LINEAR,

        /**
         *
         */
        RANDOM,

        /**
         *
         */
        NONE
    }

    /**
     *
     */
    public enum WRITE_MODE {

        /**
         *
         */
        UPSERT,

        /**
         *
         */
        INSERT,

        /**
         *
         */
        UPDATE
    }
}
