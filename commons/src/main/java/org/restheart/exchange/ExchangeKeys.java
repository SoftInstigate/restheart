/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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
 * Interface defining constants used throughout the RESTHeart exchange system.
 * <p>
 * This interface provides a centralized location for all key constants used in
 * HTTP exchanges, including query parameter names, metadata keys, special resource
 * names, and various enumerations. These constants ensure consistency across the
 * RESTHeart framework and provide a single source of truth for string literals.
 * </p>
 * <p>
 * The constants are organized into logical groups:
 * <ul>
 *   <li>Query parameters for controlling request behavior</li>
 *   <li>Metadata keys for collection and database properties</li>
 *   <li>Special resource names used by MongoDB and RESTHeart</li>
 *   <li>Enumerations for types, methods, and other categorizations</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface ExchangeKeys {
    // Query parameters for controlling request behavior

    /** Query parameter for specifying the page number in paginated results. */
    public static final String PAGE_QPARAM_KEY = "page";

    /** Query parameter for specifying the number of items per page. */
    public static final String PAGESIZE_QPARAM_KEY = "pagesize";

    /** Query parameter for requesting document count information. */
    public static final String COUNT_QPARAM_KEY = "count";

    /** Query parameter for specifying sort criteria (deprecated, use 'sort'). */
    public static final String SORT_BY_QPARAM_KEY = "sort_by";

    /** Query parameter for specifying sort criteria in MongoDB format. */
    public static final String SORT_QPARAM_KEY = "sort";

    /** Query parameter for specifying query filter criteria. */
    public static final String FILTER_QPARAM_KEY = "filter";

    /** Query parameter for providing query optimization hints to MongoDB. */
    public static final String HINT_QPARAM_KEY = "hint";

    /** Query parameter for passing variables to aggregation pipelines. */
    public static final String AGGREGATION_VARIABLES_QPARAM_KEY = "avars";

    /** Query parameter for specifying which document fields to include/exclude. */
    public static final String KEYS_QPARAM_KEY = "keys";

    /** Query parameter for controlling response caching behavior. */
    public static final String CACHE_QPARAM_KEY = "cache";

    /** Query parameter for controlling HAL (Hypertext Application Language) formatting. */
    public static final String HAL_QPARAM_KEY = "hal";

    /** Query parameter for specifying the expected document ID type. */
    public static final String DOC_ID_TYPE_QPARAM_KEY = "id_type";

    /** Query parameter for controlling ETag validation behavior. */
    public static final String ETAG_CHECK_QPARAM_KEY = "checkEtag";

    /** Query parameter for specifying shard key values in sharded collections. */
    public static final String SHARDKEY_QPARAM_KEY = "shardkey";

    /** Query parameter for excluding properties from the response (no properties). */
    public static final String NO_PROPS_KEY = "np";

    /** Query parameter for controlling the response representation format. */
    public static final String REPRESENTATION_FORMAT_KEY = "rep";

    /** Query parameter for specifying client session ID for transaction support. */
    public static final String CLIENT_SESSION_KEY = "sid";

    /** Query parameter for specifying transaction ID. */
    public static final String TXNID_KEY = "txn";

    /** Query parameter for controlling JSON parsing mode. */
    public static final String JSON_MODE_QPARAM_KEY = "jsonMode";

    /** Query parameter for disabling caching on a per-request basis. */
    public static final String NO_CACHE_QPARAM_KEY = "nocache";

    /** Query parameter for specifying write operation mode (insert/update/upsert). */
    public static final String WRITE_MODE_QPARAM_KEY = "writeMode";

    /** Short form query parameter for write mode. */
    public static final String WRITE_MODE_SHORT_QPARAM_KEY = "wm";

    /** Query parameter for specifying MongoDB write concern level. */
    public static final String WRITE_CONCERN_QPARAM_KEY = "writeConcern";

    /** Query parameter for specifying MongoDB read concern level. */
    public static final String READ_CONCERN_QPARAM_KEY = "readConcern";

    /** Query parameter for specifying MongoDB read preference. */
    public static final String READ_PREFERENCE_QPARAM_KEY = "readPreference";

    // Metadata keys for collection and database configuration

    /** Metadata key for ETag policy configuration at the document level. */
    public static final String ETAG_DOC_POLICY_METADATA_KEY = "etagDocPolicy";

    /** Metadata key for ETag policy configuration at the collection level. */
    public static final String ETAG_POLICY_METADATA_KEY = "etagPolicy";

    // Special resource names used by MongoDB and RESTHeart

    /** Prefix for MongoDB system collections. */
    public static final String SYSTEM = "system.";

    /** Name of the MongoDB local database. */
    public static final String LOCAL = "local";

    /** Name of the MongoDB admin database. */
    public static final String ADMIN = "admin";

    /** Name of the MongoDB config database (used in sharded clusters). */
    public static final String CONFIG = "config";

    /** Special resource name for size operations. */
    public static final String _SIZE = "_size";

    /** Special resource name for metadata operations. */
    public static final String _META = "_meta";

    /** Special resource name for session management. */
    public static final String _SESSIONS = "_sessions";

    /** Special resource name for transaction management. */
    public static final String _TRANSACTIONS = "_txns";

    /** Suffix for GridFS chunks collections. */
    public static final String FS_CHUNKS_SUFFIX = ".chunks";

    /** Suffix for GridFS files collections. */
    public static final String FS_FILES_SUFFIX = ".files";

    /** Collection name for storing metadata properties. */
    public static final String META_COLLNAME = "_properties";

    /** Document ID for database-level metadata. */
    public static final String DB_META_DOCID = "_properties";

    /** Prefix for collection-level metadata document IDs. */
    public static final String COLL_META_DOCID_PREFIX = "_properties.";

    /** Wildcard key for matching all resources in ACL configurations. */
    public static final String RESOURCES_WILDCARD_KEY = "*";

    /** Special resource name for index operations. */
    public static final String _INDEXES = "_indexes";

    /** Special resource name for schema operations. */
    public static final String _SCHEMAS = "_schemas";

    /** Special resource name for aggregation operations. */
    public static final String _AGGREGATIONS = "_aggrs";

    /** Special resource name for change stream operations. */
    public static final String _STREAMS = "_streams";

    /** Content type identifier for binary content. */
    public static final String BINARY_CONTENT = "binary";

    /** Special document ID representing MongoDB MaxKey type. */
    public static final String MAX_KEY_ID = "_MaxKey";

    /** Special document ID representing MongoDB MinKey type. */
    public static final String MIN_KEY_ID = "_MinKey";

    /** Special document ID representing null values. */
    public static final String NULL_KEY_ID = "_null";

    /** Special document ID representing boolean true values. */
    public static final String TRUE_KEY_ID = "_true";

    /** Special document ID representing boolean false values. */
    public static final String FALSE_KEY_ID = "_false";

    // Other commonly used constants

    /** String constant for forward slash character. */
    public static final String SLASH = "/";

    /** String constant for HTTP PATCH method. */
    public static final String PATCH = "PATCH";

    /** String constant for underscore character. */
    public static final String UNDERSCORE = "_";

    /** String constant for properties field name. */
    public static final String PROPERTIES = "properties";

    /** String constant for file metadata field name. */
    public static final String FILE_METADATA = "metadata";

    /** String constant for MongoDB document ID field. */
    public static final String _ID = "_id";

    /** String constant for content type field name. */
    public static final String CONTENT_TYPE = "contentType";

    /** String constant for filename field name. */
    public static final String FILENAME = "filename";

    /** String constant for null character. */
    public static final String NUL = Character.toString('\0');

    /**
     * Enumeration of resource types handled by RESTHeart.
     * <p>
     * This enum categorizes the different types of MongoDB resources and operations
     * that can be accessed through the RESTHeart API. Each type corresponds to a
     * specific REST endpoint pattern and determines how requests are processed.
     * </p>
     */
    public enum TYPE {

        /** Invalid or unrecognized resource type. */
        INVALID,

        /** Root resource (/) - entry point showing available databases. */
        ROOT,

        /** Root size resource - provides count information for the root. */
        ROOT_SIZE,

        /** Database resource - represents a MongoDB database. */
        DB,

        /** Database size resource - provides count information for a database. */
        DB_SIZE,

        /** Database metadata resource - configuration and properties of a database. */
        DB_META,

        /** Change stream resource - MongoDB change stream operations. */
        CHANGE_STREAM,

        /** Collection resource - represents a MongoDB collection. */
        COLLECTION,

        /** Collection size resource - provides count information for a collection. */
        COLLECTION_SIZE,

        /** Collection metadata resource - configuration and properties of a collection. */
        COLLECTION_META,

        /** Document resource - represents a single MongoDB document. */
        DOCUMENT,

        /** Collection indexes resource - manages indexes for a collection. */
        COLLECTION_INDEXES,

        /** Individual index resource - represents a specific index. */
        INDEX,

        /** GridFS bucket resource - represents a GridFS file storage bucket. */
        FILES_BUCKET,

        /** GridFS bucket size resource - provides count information for a bucket. */
        FILES_BUCKET_SIZE,

        /** GridFS bucket metadata resource - configuration of a GridFS bucket. */
        FILES_BUCKET_META,

        /** GridFS file resource - represents a file stored in GridFS. */
        FILE,

        /** GridFS file binary content - the actual binary data of a GridFS file. */
        FILE_BINARY,

        /** Aggregation resource - represents a predefined aggregation pipeline. */
        AGGREGATION,

        /** JSON Schema resource - represents a document validation schema. */
        SCHEMA,

        /** Schema store resource - collection of schemas. */
        SCHEMA_STORE,

        /** Schema store size resource - count information for schema store. */
        SCHEMA_STORE_SIZE,

        /** Schema store metadata resource - configuration of schema store. */
        SCHEMA_STORE_META,

        /** Bulk documents resource - for bulk operations on multiple documents. */
        BULK_DOCUMENTS,

        /** Individual session resource - represents a client session. */
        SESSION,

        /** Sessions collection resource - manages client sessions. */
        SESSIONS,

        /** Transactions collection resource - manages transaction operations. */
        TRANSACTIONS,

        /** Individual transaction resource - represents a specific transaction. */
        TRANSACTION
    }

    /**
     * Enumeration of HTTP methods supported by RESTHeart.
     * <p>
     * This enum represents the standard HTTP methods that RESTHeart recognizes
     * and processes. Each method corresponds to different types of operations
     * on MongoDB resources.
     * </p>
     */
    public enum METHOD {
        /** HTTP HEAD method - retrieves headers without the response body. */
        HEAD,

        /** HTTP GET method - retrieves data without modifying it. */
        GET,

        /** HTTP POST method - creates new resources or performs operations. */
        POST,

        /** HTTP PUT method - creates or completely replaces a resource. */
        PUT,

        /** HTTP DELETE method - removes a resource. */
        DELETE,

        /** HTTP PATCH method - performs partial updates to a resource. */
        PATCH,

        /** HTTP OPTIONS method - describes communication options for the resource. */
        OPTIONS,

        /** Any other HTTP method not explicitly supported. */
        OTHER
    }

    /**
     * Enumeration of document ID types supported by MongoDB through RESTHeart.
     * <p>
     * This enum defines the various data types that can be used as MongoDB document
     * identifiers (_id field). Each type has specific parsing and validation rules
     * when processing HTTP requests.
     * </p>
     */
    public enum DOC_ID_TYPE {

        /** MongoDB ObjectId type - 12-byte binary identifier. */
        OID,

        /** String that may be converted to ObjectId if it represents a valid ObjectId. */
        STRING_OID,

        /** Plain string identifier without ObjectId conversion. */
        STRING,

        /** Numeric identifier including MongoDB NumberLong type. */
        NUMBER,

        /** Date/timestamp identifier. */
        DATE,

        /** MongoDB MinKey type - represents the smallest possible BSON value. */
        MINKEY,

        /** MongoDB MaxKey type - represents the largest possible BSON value. */
        MAXKEY,

        /** Null identifier value. */
        NULL,

        /** Boolean identifier (true or false). */
        BOOLEAN
    }

    /**
     * Enumeration of HAL (Hypertext Application Language) representation modes.
     * <p>
     * This enum controls how HAL-formatted responses are generated, affecting
     * the amount of metadata and hypermedia links included in the response.
     * </p>
     */
    public enum HAL_MODE {

        /** Full HAL mode - includes complete hypermedia links and embedded resources. */
        FULL,

        /** Alias for FULL mode - convenient shorthand. */
        F,

        /** Compact HAL mode - reduced metadata and links for smaller responses. */
        COMPACT,

        /** Alias for COMPACT mode - convenient shorthand. */
        C
    }

    /**
     * Enumeration of ETag validation policies for concurrency control.
     * <p>
     * This enum determines when and how ETag headers are validated to prevent
     * lost updates in concurrent modification scenarios. ETags provide optimistic
     * concurrency control by ensuring that modifications are based on the current
     * state of the resource.
     * </p>
     */
    public enum ETAG_CHECK_POLICY {

        /** Always requires ETag header - returns 412 Precondition Failed if missing. */
        REQUIRED,

        /** Requires ETag only for DELETE operations - returns 412 Precondition Failed if missing. */
        REQUIRED_FOR_DELETE,

        /** Validates ETag only when provided by client via If-Match header. */
        OPTIONAL
    }

    /**
     * Enumeration of response representation formats supported by RESTHeart.
     * <p>
     * This enum controls how JSON responses are structured and formatted,
     * allowing clients to choose between different levels of hypermedia
     * support and response complexity.
     * </p>
     */
    public enum REPRESENTATION_FORMAT {

        /** HAL (Hypertext Application Language) format with full hypermedia support. */
        HAL,

        /** Simplified HAL with children as direct elements of _embedded array. */
        SHAL,

        /** Standard format - root and dbs as arrays of children's ids, collections as document arrays. */
        STANDARD,

        /** Alias for STANDARD format - convenient shorthand. */
        S,

        /** Plain JSON format without HAL structure or hypermedia links. */
        PLAIN_JSON,

        /** Alias for PLAIN_JSON format - convenient shorthand. */
        PJ,
    }

    /**
     * Enumeration of write operation modes for document modifications.
     * <p>
     * This enum controls the behavior of write operations, determining
     * whether operations should insert, update, or perform upsert operations
     * based on the existence of the target document.
     * </p>
     */
    public enum WRITE_MODE {

        /** Upsert mode - insert if document doesn't exist, update if it does. */
        UPSERT,

        /** Insert mode - only insert new documents, fail if document exists. */
        INSERT,

        /** Update mode - only update existing documents, fail if document doesn't exist. */
        UPDATE
    }
}
