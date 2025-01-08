/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInvalidOperationException;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonMode;
import org.bson.json.JsonParseException;
import static org.restheart.exchange.ExchangeKeys.ADMIN;
import static org.restheart.exchange.ExchangeKeys.BINARY_CONTENT;
import static org.restheart.exchange.ExchangeKeys.CACHE_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.COLL_META_DOCID_PREFIX;
import static org.restheart.exchange.ExchangeKeys.CONFIG;
import static org.restheart.exchange.ExchangeKeys.DB_META_DOCID;
import org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE;
import static org.restheart.exchange.ExchangeKeys.ETAG_CHECK_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.FS_CHUNKS_SUFFIX;
import static org.restheart.exchange.ExchangeKeys.FS_FILES_SUFFIX;
import org.restheart.exchange.ExchangeKeys.HAL_MODE;
import static org.restheart.exchange.ExchangeKeys.JSON_MODE_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.LOCAL;
import static org.restheart.exchange.ExchangeKeys.META_COLLNAME;
import static org.restheart.exchange.ExchangeKeys.NO_CACHE_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.NO_PROPS_KEY;
import static org.restheart.exchange.ExchangeKeys.NUL;
import static org.restheart.exchange.ExchangeKeys.READ_CONCERN_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.READ_PREFERENCE_QPARAM_KEY;
import org.restheart.exchange.ExchangeKeys.REPRESENTATION_FORMAT;
import static org.restheart.exchange.ExchangeKeys.RESOURCES_WILDCARD_KEY;
import static org.restheart.exchange.ExchangeKeys.SYSTEM;
import org.restheart.exchange.ExchangeKeys.TYPE;
import static org.restheart.exchange.ExchangeKeys.WRITE_CONCERN_QPARAM_KEY;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;
import static org.restheart.exchange.ExchangeKeys.WRITE_MODE_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys.WRITE_MODE_SHORT_QPARAM_KEY;
import static org.restheart.exchange.ExchangeKeys._AGGREGATIONS;
import static org.restheart.exchange.ExchangeKeys._INDEXES;
import static org.restheart.exchange.ExchangeKeys._META;
import static org.restheart.exchange.ExchangeKeys._SCHEMAS;
import static org.restheart.exchange.ExchangeKeys._SESSIONS;
import static org.restheart.exchange.ExchangeKeys._SIZE;
import static org.restheart.exchange.ExchangeKeys._STREAMS;
import static org.restheart.exchange.ExchangeKeys._TRANSACTIONS;
import org.restheart.mongodb.RSOps;
import org.restheart.mongodb.db.sessions.ClientSessionImpl;
import static org.restheart.utils.BsonUtils.array;
import static org.restheart.utils.BsonUtils.document;
import org.restheart.utils.MongoServiceAttachments;
import static org.restheart.utils.URLUtils.removeTrailingSlashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatch;

/**
 *
 * Request implementation used by MongoService and backed by BsonValue that
 * provides simplified methods to deal with headers and query parameters
 * specific to mongo requests
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoRequest extends BsonRequest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoRequest.class);

    private final String whereUri;
    private final String whatUri;

    private final String[] pathTokens;

    private BsonDocument dbProps;
    private BsonDocument collectionProps;

    private InputStream fileInputStream;

    private int page = 1;
    private int pagesize = 100;
    private boolean count = false;
    private boolean etagCheckRequired = false;
    private WRITE_MODE writeMode = null;
    private boolean cache;
    private Deque<String> filter = null;
    private BsonDocument aggregationVars = null; // aggregation vars
    private Deque<String> keys = null;
    private Deque<String> sortBy = null;
    private Deque<String> hint = null;
    private DOC_ID_TYPE docIdType = DOC_ID_TYPE.STRING_OID;
    private final TYPE type;

    private REPRESENTATION_FORMAT representationFormat;

    private BsonValue documentId;

    private String mongoResourceUri = null;

    private final String etag;

    private boolean forceEtagCheck = false;

    private BsonDocument shardKey = null;

    private boolean noProps = false;

    private ClientSessionImpl clientSession = null;

    /**
     * the HAL mode
     */
    private HAL_MODE halMode = HAL_MODE.FULL;

    private final long requestStartTime = System.currentTimeMillis();

    /**
     * path template match
     */
    private final PathTemplateMatch pathTemplateMatch;

    /**
     * the json mode
     */
    private final JsonMode jsonMode;

    /**
     * noCache switch, e.g.: ?nocache=true
     */
    final boolean noCache;

    private final Optional<RSOps> rsOps;

    protected MongoRequest(HttpServerExchange exchange, String whereUri, String whatUri) {
        super(exchange);

        this.whereUri = removeTrailingSlashes(whereUri == null
                ? null
                : whereUri.startsWith("/") ? whereUri
                        : "/" + whereUri);

        this.whatUri = removeTrailingSlashes(whatUri == null ? null
                : whatUri.startsWith("/") || "*".equals(whatUri) ? whatUri
                        : "/" + whatUri);

        if (exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY) != null) {
            this.pathTemplateMatch = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);

            // add the host variables
            var params = this.pathTemplateMatch.getParameters();
            var hostname = this.wrapped.getHostName();
            if (hostname != null) {
                params.put("host", hostname);
                final var parts = hostname.split("\\.");
                IntStream.range(0, parts.length)
                        .forEach(idx -> params.put("host[".concat(String.valueOf(idx)).concat("]"), parts[idx]));
            }
        } else {
            this.pathTemplateMatch = null;
        }

        this.mongoResourceUri = mongoUriFromRequestPath(exchange.getRequestPath());

        // "/db/collection/document" --> { "", "mappedDbName", "collection", "document"
        // }
        this.pathTokens = this.mongoResourceUri.split(SLASH);

        this.type = selectRequestType(pathTokens);

        // etag
        var etagHvs = getHeaders() == null ? null : getHeaders().get(Headers.IF_MATCH);

        this.etag = etagHvs == null || etagHvs.getFirst() == null ? null : etagHvs.getFirst();

        this.forceEtagCheck = exchange.getQueryParameters().get(ETAG_CHECK_QPARAM_KEY) != null;

        this.cache = exchange.getQueryParameters().get(CACHE_QPARAM_KEY) != null;

        this.noProps = exchange.getQueryParameters().get(NO_PROPS_KEY) != null;

        var _jsonMode = exchange.getQueryParameters().containsKey(JSON_MODE_QPARAM_KEY)
                ? exchange.getQueryParameters().get(JSON_MODE_QPARAM_KEY).getFirst().toUpperCase()
                : null;

        if (_jsonMode != null) {
            JsonMode mode;

            try {
                mode = JsonMode.valueOf(_jsonMode.toUpperCase());
            } catch (IllegalArgumentException iae) {
                mode = null;
            }

            this.jsonMode = mode;
        } else {
            this.jsonMode = null;
        }

        this.noCache = exchange.getQueryParameters().get(NO_CACHE_QPARAM_KEY) != null;

        // writeMode
        var _writeMode = exchange.getQueryParameters().containsKey(WRITE_MODE_QPARAM_KEY)
                ? exchange.getQueryParameters().get(WRITE_MODE_QPARAM_KEY).getFirst().toUpperCase()
                : exchange.getQueryParameters().containsKey(WRITE_MODE_SHORT_QPARAM_KEY)
                        ? exchange.getQueryParameters().get(WRITE_MODE_SHORT_QPARAM_KEY).getFirst().toUpperCase()
                        : defaultWriteMode();

        WRITE_MODE mode;

        try {
            mode = WRITE_MODE.valueOf(_writeMode.toUpperCase());
        } catch (IllegalArgumentException iae) {
            mode = WRITE_MODE.valueOf(defaultWriteMode());
        }

        this.writeMode = mode;

        var anyRsSet = false;
        var _rsOps = new RSOps();

        // readConcern
        if (exchange.getQueryParameters().containsKey(READ_CONCERN_QPARAM_KEY)) {
            try {
                _rsOps = _rsOps.withReadConcern(exchange.getQueryParameters().get(READ_CONCERN_QPARAM_KEY).getFirst());
                anyRsSet = true;
            } catch (IllegalArgumentException iae) {
                // nothing to do
            }
        }

        // readPreference
        if (exchange.getQueryParameters().containsKey(READ_PREFERENCE_QPARAM_KEY)) {
            try {
                _rsOps = _rsOps
                        .withReadPreference(exchange.getQueryParameters().get(READ_PREFERENCE_QPARAM_KEY).getFirst());
                anyRsSet = true;
            } catch (IllegalArgumentException iae) {
                // nothing to do
            }
        }

        // writeConcern
        if (exchange.getQueryParameters().containsKey(WRITE_CONCERN_QPARAM_KEY)) {
            _rsOps = _rsOps.withWriteConcern(exchange.getQueryParameters().get(WRITE_CONCERN_QPARAM_KEY).getFirst());
            anyRsSet = true;
        }

        this.rsOps = anyRsSet ? Optional.of(_rsOps) : Optional.empty();
        LOGGER.debug("ReplicaSet connection options: {}", _rsOps);
    }

    private String defaultWriteMode() {
        if (isPost()) {
            return WRITE_MODE.INSERT.name();
        } else if (isPatch() || isPut()) {
            return WRITE_MODE.UPDATE.name();
        } else {
            return WRITE_MODE.UPSERT.name();
        }
    }

    /**
     *
     * @param exchange
     *
     *                 the exchange request path (mapped resolvedTemplate) is
     *                 rewritten replacing the
     *                 whereUri with the whatUri
     *                 the special whatUri value * means any resource
     *
     *                 example 1
     *
     *                 whatUri=/db/mycollection whereUri=/
     *
     *                 then the requestPath / is rewritten as /db/mycollection
     *
     *                 example 2
     *
     *                 whatUri=*, whereUri=/data
     *
     *                 then the requestPath /data is rewritten as /
     *
     * @param whereUri the where URI
     * @param whatUri  the what URI idenitifying a MongoDB resource, as
     *                 /db/collection
     * @return the MongoRequest
     */
    public static MongoRequest init(HttpServerExchange exchange, String whereUri, String whatUri) {
        return new MongoRequest(exchange, whereUri, whatUri);
    }

    public static MongoRequest of(HttpServerExchange exchange) {
        return of(exchange, MongoRequest.class);
    }

    /**
     *
     * @param dbName
     * @return true if the dbName is a reserved resource
     */
    public static boolean isReservedDbName(String dbName) {
        return dbName == null
                ? false
                : "".equals(dbName)
                        || dbName.equalsIgnoreCase(ADMIN)
                        || dbName.equalsIgnoreCase(CONFIG)
                        || dbName.equalsIgnoreCase(LOCAL)
                        || dbName.startsWith(SYSTEM);
    }

    /**
     *
     * @param collectionName
     * @return true if the collectionName is a reserved resource
     */
    public static boolean isReservedCollectionName(String collectionName) {
        return collectionName == null
                ? false
                : "".equals(collectionName)
                        || collectionName.startsWith(SYSTEM)
                        || collectionName.endsWith(FS_CHUNKS_SUFFIX)
                        || collectionName.equals(META_COLLNAME);
    }

    /**
     *
     * @param type
     * @param documentId
     * @return true if the documentIdRaw is a reserved resource
     */
    public static boolean isReservedDocumentId(TYPE type, BsonValue documentId) {
        if (documentId == null || !documentId.isString()) {
            return false;
        }

        var sdi = documentId.asString().getValue();

        if ((type == TYPE.COLLECTION_META && sdi.startsWith(COLL_META_DOCID_PREFIX))
                || (type == TYPE.DB_META && sdi.startsWith(DB_META_DOCID))
                || (type == TYPE.BULK_DOCUMENTS && RESOURCES_WILDCARD_KEY.equals(sdi))
                || (type == TYPE.COLLECTION_SIZE && _SIZE.equalsIgnoreCase(sdi))
                || (type == TYPE.INDEX && _INDEXES.equalsIgnoreCase(sdi))
                || (type == TYPE.COLLECTION_META && _META.equalsIgnoreCase(sdi))
                || (type == TYPE.INVALID && _AGGREGATIONS.equalsIgnoreCase(sdi))
                || (type == TYPE.INVALID && _STREAMS.equalsIgnoreCase(sdi))) {
            return false;
        } else {
            return DB_META_DOCID.equalsIgnoreCase(sdi) || sdi.startsWith(COLL_META_DOCID_PREFIX);
        }
    }

    /**
     *
     * @return type
     */
    public TYPE getType() {
        return type;
    }

    static TYPE selectRequestType(String[] pathTokens) {
        TYPE type;

        if (pathTokens.length > 0 && pathTokens[pathTokens.length - 1].equalsIgnoreCase(_SIZE)) {
            if (pathTokens.length == 2) {
                type = TYPE.ROOT_SIZE;
            } else if (pathTokens.length == 3) {
                type = TYPE.DB_SIZE;
            } else if (pathTokens.length == 4 && pathTokens[2].endsWith(FS_FILES_SUFFIX)) {
                type = TYPE.FILES_BUCKET_SIZE;
            } else if (pathTokens.length == 4 && pathTokens[2].equalsIgnoreCase(_SCHEMAS)) {
                type = TYPE.SCHEMA_STORE_SIZE;
            } else if (pathTokens.length == 4) {
                type = TYPE.COLLECTION_SIZE;
            } else {
                type = TYPE.INVALID;
            }
        } else if (pathTokens.length > 2 && pathTokens[pathTokens.length - 1].equalsIgnoreCase(_META)) {
            if (pathTokens.length == 3) {
                type = TYPE.DB_META;
            } else if (pathTokens.length == 4 && pathTokens[2].endsWith(FS_FILES_SUFFIX)) {
                type = TYPE.FILES_BUCKET_META;
            } else if (pathTokens.length == 4 && pathTokens[2].equalsIgnoreCase(_SCHEMAS)) {
                type = TYPE.SCHEMA_STORE_META;
            } else if (pathTokens.length == 4) {
                type = TYPE.COLLECTION_META;
            } else {
                type = TYPE.INVALID;
            }
        } else if (pathTokens.length < 2) {
            type = TYPE.ROOT;
        } else if (pathTokens.length == 2 && pathTokens[pathTokens.length - 1].equalsIgnoreCase(_SESSIONS)) {
            type = TYPE.SESSIONS;
        } else if (pathTokens.length == 3 && pathTokens[pathTokens.length - 2].equalsIgnoreCase(_SESSIONS)) {
            type = TYPE.SESSION;
        } else if (pathTokens.length == 4 && pathTokens[pathTokens.length - 3].equalsIgnoreCase(_SESSIONS)
                && pathTokens[pathTokens.length - 1].equalsIgnoreCase(_TRANSACTIONS)) {
            type = TYPE.TRANSACTIONS;
        } else if (pathTokens.length == 5 && pathTokens[pathTokens.length - 4].equalsIgnoreCase(_SESSIONS)
                && pathTokens[pathTokens.length - 2].equalsIgnoreCase(_TRANSACTIONS)) {
            type = TYPE.TRANSACTION;
        } else if (pathTokens.length < 3) {
            type = TYPE.DB;
        } else if (pathTokens.length >= 3 && pathTokens[2].endsWith(FS_FILES_SUFFIX)) {
            if (pathTokens.length == 3) {
                type = TYPE.FILES_BUCKET;
            } else if (pathTokens.length == 4 && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
                type = TYPE.COLLECTION_INDEXES;
            } else if (pathTokens.length == 4 && !pathTokens[3].equalsIgnoreCase(_INDEXES)
                    && !pathTokens[3].equals(RESOURCES_WILDCARD_KEY)) {
                type = TYPE.FILE;
            } else if (pathTokens.length > 4 && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
                type = TYPE.INDEX;
            } else if (pathTokens.length > 4 && pathTokens[3].equalsIgnoreCase(_AGGREGATIONS)) {
                type = TYPE.AGGREGATION;
            } else if (pathTokens.length > 4 && !pathTokens[3].equalsIgnoreCase(_INDEXES)
                    && !pathTokens[4].equalsIgnoreCase(BINARY_CONTENT)) {
                type = TYPE.FILE;
            } else if (pathTokens.length == 5 && pathTokens[4].equalsIgnoreCase(BINARY_CONTENT)) {
                // URL: <host>/db/bucket.filePath/xxx/binary
                type = TYPE.FILE_BINARY;
            } else {
                type = TYPE.DOCUMENT;
            }
        } else if (pathTokens.length >= 3 && pathTokens[2].equalsIgnoreCase(_SCHEMAS)) {
            if (pathTokens.length == 3) {
                type = TYPE.SCHEMA_STORE;
            } else if (pathTokens[3].equals(RESOURCES_WILDCARD_KEY)) {
                type = TYPE.BULK_DOCUMENTS;
            } else {
                type = TYPE.SCHEMA;
            }
        } else if (pathTokens.length < 4) {
            type = TYPE.COLLECTION;
        } else if (pathTokens.length == 4 && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
            type = TYPE.COLLECTION_INDEXES;
        } else if (pathTokens.length == 4 && pathTokens[3].equals(RESOURCES_WILDCARD_KEY)) {
            type = TYPE.BULK_DOCUMENTS;
        } else if (pathTokens.length > 4 && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
            type = TYPE.INDEX;
        } else if (pathTokens.length == 4 && pathTokens[3].equalsIgnoreCase(_AGGREGATIONS)) {
            type = TYPE.INVALID;
        } else if (pathTokens.length > 4 && pathTokens[3].equalsIgnoreCase(_AGGREGATIONS)) {
            type = TYPE.AGGREGATION;
        } else if (pathTokens.length == 4 && pathTokens[3].equalsIgnoreCase(_STREAMS)) {
            type = TYPE.INVALID;
        } else if (pathTokens.length > 4 && pathTokens[3].equalsIgnoreCase(_STREAMS)) {
            type = TYPE.CHANGE_STREAM;
        } else {
            type = TYPE.DOCUMENT;
        }

        return type;
    }

    /**
     * given a request path (eg. /some/mapped/coll) returns the mongo resource uri
     * (eg. /db/coll); request paths are mapped to mongodb resources by mongo-mounts
     * configuration properties. note that the mapped uri can make use of path
     * templates (/some/{path}/template/{*})
     *
     * @param path the request path
     * @return the mongo resource uri
     */
    private String mongoUriFromRequestPath(String path) {
        // don't unmap URIs statring with /_sessions
        if (path.startsWith("/".concat(_SESSIONS))) {
            return path;
        }

        if (this.pathTemplateMatch == null) {
            return mongoUriFromPathMatch(path);
        } else {
            return mongoUruFromPathTemplateMatch(path);
        }
    }

    private String mongoUriFromPathMatch(String requestPath) {
        var mongoUri = removeTrailingSlashes(requestPath);

        if (this.whatUri.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                mongoUri = mongoUri.replaceFirst("^" + Pattern.quote(this.whereUri), "");
            }
        } else if (!this.whereUri.equals(SLASH)) {
            // problem, whereUri can contain regex special chars such as *
            mongoUri = removeTrailingSlashes(mongoUri.replaceFirst("^" + Pattern.quote(this.whereUri), this.whatUri));
        } else {
            mongoUri = removeTrailingSlashes(removeTrailingSlashes(this.whatUri) + mongoUri);
        }

        return mongoUri.isEmpty() ? SLASH : mongoUri;
    }

    private String mongoUruFromPathTemplateMatch(String requestPath) {
        // requestPath=/api/a/b
        // what=*
        // where=/api/{*} -> /api/a/b
        // -> /a/b
        var mongoUri = removeTrailingSlashes(requestPath);

        final var _whatUri = resolveTemplate(this.whatUri);
        final var _whereUri = resolveTemplate(this.whereUri);

        if (_whatUri.equals("*")) {
            mongoUri = mongoUri.replaceFirst("^" + Pattern.quote(_whereUri), "");
        } else if (!_whereUri.equals(SLASH)) {
            mongoUri = removeTrailingSlashes(mongoUri.replaceFirst("^" + Pattern.quote(_whereUri), _whatUri));
        } else {
            mongoUri = removeTrailingSlashes(removeTrailingSlashes(_whatUri) + mongoUri);
        }

        return mongoUri.isEmpty() ? SLASH : removeTrailingSlashes(mongoUri);
    }

    /**
     * given a canonical uri (/db/coll) returns the mapped uri
     * (/some/mapping/uri) relative to this context. URLs are mapped to mongodb
     * resources via the mongo-mounts configuration properties
     *
     * @param mongoResourceUri
     * @return
     */
    public String requestPathFromMongoUri(String mongoResourceUri) {
        if (this.pathTemplateMatch == null) {
            return requestPathFromPathMatch(mongoResourceUri);
        } else {
            return requestPathFromPathTemplateMatch(mongoResourceUri);
        }
    }

    private String requestPathFromPathMatch(String mongoUri) {
        var requestPath = removeTrailingSlashes(mongoUri);

        if (whatUri.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                return this.whereUri + mongoUri;
            }
        } else {
            requestPath = removeTrailingSlashes(
                    requestPath.replaceFirst("^" + Pattern.quote(this.whatUri), this.whereUri));
        }

        if (requestPath.isEmpty()) {
            requestPath = SLASH;
        } else {
            requestPath = requestPath.replaceAll("//", "/");
        }

        return requestPath;
    }

    private String requestPathFromPathTemplateMatch(String mongoUri) {
        var requestPath = removeTrailingSlashes(mongoUri);
        var resolvedWhere = resolveTemplate(this.whereUri);
        var resolvedWhat = resolveTemplate(this.whatUri);

        // now replace mappedUri with resolved path template
        if (resolvedWhat.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                return resolvedWhere + mongoUri;
            }
        } else {
            requestPath = removeTrailingSlashes(
                    requestPath.replaceFirst("^" + Pattern.quote(resolvedWhat), resolvedWhere));
        }

        return requestPath.isEmpty() ? SLASH : requestPath;
    }

    /**
     * @return the resolved template with actual paramenters
     */
    private String resolveTemplate(String template) {
        String resolvedTemplate = template;
        // replace params within whatUri
        // eg _whatUri: /{prefix}_db, _whereUri: /{prefix}/*
        for (var key : this.pathTemplateMatch.getParameters().keySet()) {
            resolvedTemplate = resolvedTemplate.replace("{".concat(key).concat("}"),
                    this.pathTemplateMatch.getParameters().get(key));
        }
        return removeTrailingSlashes(resolvedTemplate);
    }

    /**
     * check if the parent of the requested resource is accessible in this
     * request context
     *
     * for instance if /db/mycollection is mapped to /coll then:
     *
     * the db is accessible from the collection the root is not accessible from
     * the collection (since / is actually mapped to the db)
     *
     * @return true if parent of the requested resource is accessible
     */
    public boolean isParentAccessible() {
        return getType() == TYPE.DB
                ? getExchange().getRequestPath().split(SLASH).length > 1
                : getExchange().getRequestPath().split(SLASH).length > 2;
    }

    /**
     *
     * @return DB Name
     */
    public String getDBName() {
        return getPathTokenAt(1);
    }

    /**
     *
     * @return collection name
     */
    public String getCollectionName() {
        return getPathTokenAt(2);
    }

    /**
     *
     * @return document id
     */
    public String getDocumentIdRaw() {
        return getPathTokenAt(3);
    }

    /**
     *
     * @return index id
     */
    public String getIndexId() {
        return getPathTokenAt(4);
    }

    /**
     *
     * @return the txn id or null if request type is not SESSIONS, TRANSACTIONS
     *         or TRANSACTION
     */
    public String getSid() {
        return isTxn() || isTxns() || isSessions() ? getPathTokenAt(2) : null;
    }

    /**
     *
     * @return the txn id or null if request type is not TRANSACTION
     */
    public Long getTxnId() {
        return isTxn() ? Long.valueOf(getPathTokenAt(4)) : null;
    }

    /**
     *
     * @return collection name
     */
    public String getAggregationOperation() {
        return getPathTokenAt(4);
    }

    /**
     * @return change stream operation name
     */
    public String getChangeStreamOperation() {
        return getPathTokenAt(4);
    }

    /**
     *
     * @return URI
     * @throws URISyntaxException
     */
    public URI getUri() throws URISyntaxException {
        return new URI(Arrays.asList(pathTokens)
                .stream()
                .reduce(SLASH, (t1, t2) -> t1 + SLASH + t2));
    }

    /**
     *
     * @return isReservedResource
     */
    public boolean isReservedResource() {
        if (getType() == TYPE.ROOT) {
            return false;
        }

        return isReservedDbName(getDBName()) || isReservedCollectionName(getCollectionName())
                || isReservedDocumentId(getType(), getDocumentId());
    }

    /**
     * @return the whereUri
     */
    public String getWhereUri() {
        return whereUri;
    }

    /**
     * @return the whatUri
     */
    public String getWhatUri() {
        return whatUri;
    }

    /**
     * @return the page
     */
    public int getPage() {
        return page;
    }

    /**
     * @param page the page to set
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * @return the pagesize
     */
    public int getPagesize() {
        return pagesize;
    }

    /**
     * @param pagesize the pagesize to set
     */
    public void setPagesize(int pagesize) {
        this.pagesize = pagesize;
    }

    /**
     * @return the representationFormat
     */
    public REPRESENTATION_FORMAT getRepresentationFormat() {
        return representationFormat;
    }

    /**
     * sets representationFormat
     *
     * @param representationFormat
     */
    public void setRepresentationFormat(REPRESENTATION_FORMAT representationFormat) {
        this.representationFormat = representationFormat;
    }

    /**
     * @return the count
     */
    public boolean isCount() {
        return count
                || getType() == TYPE.ROOT_SIZE
                || getType() == TYPE.COLLECTION_SIZE
                || getType() == TYPE.FILES_BUCKET_SIZE
                || getType() == TYPE.SCHEMA_STORE_SIZE;
    }

    /**
     * @param count the count to set
     */
    public void setCount(boolean count) {
        this.count = count;
    }

    /**
     * @return the filter
     */
    public Deque<String> getFilter() {
        return filter;
    }

    /**
     * @param filter the filter to set
     */
    public void setFilter(Deque<String> filter) {
        this.filter = filter;
    }

    /**
     * @return the hint
     */
    public Deque<String> getHint() {
        return hint;
    }

    /**
     * @param hint the hint to set
     */
    public void setHint(Deque<String> hint) {
        this.hint = hint;
    }

    /**
     *
     * @return the $and composed filter qparam values
     */
    public BsonDocument getFiltersDocument() throws JsonParseException {
        final var filterQuery = new BsonDocument();

        if (filter != null) {
            if (filter.size() > 1) {
                var _filters = new BsonArray();

                filter.stream().forEach(f -> _filters.add(BsonDocument.parse(f)));

                filterQuery.put("$and", _filters);
            } else if (filter.size() == 1) {
                // this can throw JsonParseException for invalid filter parameters
                filterQuery.putAll(BsonDocument.parse(filter.getFirst()));
            } else {
                return filterQuery;
            }
        }

        return filterQuery;
    }

    /**
     *
     * @return @throws JsonParseException
     */
    public BsonDocument getSortByDocument() throws JsonParseException {
        if (sortBy == null) {
            return document().put("_id", -1).get();
        } else {
            var ret = document();

            sortBy.stream().map(String::trim).forEach((s) -> {
                // manage the case where sort_by is a json object
                try {
                    var _s = BsonDocument.parse(s);
                    ret.putAll(_s.asDocument());
                } catch (JsonParseException e) {
                    // if we cannot parse it as a document,
                    // assume it as a string property name
                    // unless it starts with {
                    if (s.startsWith("{")) {
                        throw new JsonParseException("Invalid sort parameter", e);
                    } else if (s.startsWith("-")) {
                        ret.put(s.substring(1), -1);
                    } else if (s.startsWith("+")) {
                        ret.put(s.substring(1), 1);
                    } else {
                        ret.put(s, 1);
                    }
                } catch (BsonInvalidOperationException biop) {
                    throw new JsonParseException("Invalid sort parameter", biop);
                }
            });

            return ret.get();
        }
    }

    /**
     * hints can be either an index document as {"key":1} or an index name
     * the compact format is allowed, eg. +key means {"key":1}
     * if the value is a string (not starting with + or -) than it is taken into account as an index name
     * @return an array of hints, either documents or strings (index names)
     */
    public BsonArray getHintValue() {
        if (hint == null || hint.isEmpty()) {
            return null;
        } else {
            var ret = array();

            hint.stream().forEach(s -> {
                var _s = s.strip(); // the + sign is decoded into a space, in case remove it

                // manage the case where hint is a json object
                try {
                    ret.add(BsonDocument.parse(_s));
                } catch (JsonParseException e) {
                    // ret is just a string, i.e. an index name
                    if (_s.startsWith("-")) {
                        ret.add(document().put(_s.substring(1), -1));
                    } else if (_s.startsWith("+")) {
                        ret.add(document().put(_s.substring(1), 1));
                    } else {
                        ret.add(_s);
                    }
                }
            });

            return ret.get();
        }
    }

    /**
     *
     * @return @throws JsonParseException
     */
    public BsonDocument getProjectionDocument() throws JsonParseException {
        if (keys == null || keys.isEmpty()) {
            return null;
        } else {
            final var projection = new BsonDocument();
            // this can throw JsonParseException for invalid keys parameters
            keys.stream().forEach(f -> projection.putAll(BsonDocument.parse(f)));
            return projection;
        }
    }

    /**
     * @return the aggregationVars
     */
    public BsonDocument getAggregationVars() {
        return aggregationVars;
    }

    /**
     * @param aggregationVars the aggregationVars to set
     */
    public void setAggregationVars(BsonDocument aggregationVars) {
        this.aggregationVars = aggregationVars;
    }

    /**
     * @return the sortBy
     */
    public Deque<String> getSortBy() {
        return sortBy;
    }

    /**
     * @param sortBy the sortBy to set
     */
    public void setSortBy(Deque<String> sortBy) {
        this.sortBy = sortBy;
    }

    /**
     * @return the collectionProps
     */
    public BsonDocument getCollectionProps() {
        return collectionProps;
    }

    /**
     * @param collectionProps the collectionProps to set
     */
    public void setCollectionProps(BsonDocument collectionProps) {
        this.collectionProps = collectionProps;
    }

    /**
     * @return the dbProps
     */
    public BsonDocument getDbProps() {
        return dbProps;
    }

    /**
     * @param dbProps the dbProps to set
     */
    public void setDbProps(BsonDocument dbProps) {
        this.dbProps = dbProps;
    }

    /**
     *
     * Return the uri of the mongodb resource ass /db/coll or /db/coll/docid.
     *
     * @return the mongoResourceUri
     */
    public String getMongoResourceUri() {
        return mongoResourceUri;
    }

    /**
     * if mongo-mounts specifies a path template (i.e. /{foo}/*) this returns
     * the request template parameters {@literal (/x/y => foo=x, *=y) }
     *
     * @return
     */
    public Map<String, String> getPathTemplateParamenters() {
        if (this.pathTemplateMatch == null) {
            return null;
        } else {
            return this.pathTemplateMatch.getParameters();
        }
    }

    /**
     *
     * @param index
     * @return pathTokens[index] if pathTokens.length > index, else null
     */
    private String getPathTokenAt(int index) {
        return pathTokens.length > index ? pathTokens[index] : null;
    }

    /**
     *
     * @return the cache
     */
    public boolean isCache() {
        return cache;
    }

    /**
     * @param cache true to use caching
     */
    public void setCache(boolean cache) {
        this.cache = cache;
    }

    /**
     * @return the docIdType
     */
    public DOC_ID_TYPE getDocIdType() {
        return docIdType;
    }

    /**
     * @param docIdType the docIdType to set
     */
    public void setDocIdType(DOC_ID_TYPE docIdType) {
        this.docIdType = docIdType;
    }

    /**
     * @param documentId the documentId to set
     */
    public void setDocumentId(BsonValue documentId) {
        this.documentId = documentId;
    }

    /**
     * @return the documentId
     */
    public BsonValue getDocumentId() {
        if (isDbMeta()) {
            return new BsonString(DB_META_DOCID);
        } else if (isCollectionMeta()) {
            return new BsonString(COLL_META_DOCID_PREFIX.concat(getPathTokenAt(2)));
        } else {
            return documentId;
        }
    }

    /**
     * @return the clientSession
     */
    public ClientSessionImpl getClientSession() {
        return this.clientSession;
    }

    /**
     * @param clientSession the clientSession to set
     */
    public void setClientSession(ClientSessionImpl clientSession) {
        this.clientSession = clientSession;
    }

    /**
     * @return the jsonMode as specified by jsonMode query paramter
     */
    public JsonMode getJsonMode() {
        return jsonMode;
    }

    @Override
    public BsonValue parseContent() throws BadRequestException, IOException {
        // the MongoRequest content can have been already attached to the exchange
        // with MongoServiceAttachments.attachBsonContent()
        // for instance, by an Interceptor at interceptPoint=BEFORE_EXCHANGE_INIT
        var attacheBsonContent = MongoServiceAttachments.attachedBsonContent(wrapped);
        return attacheBsonContent == null
                ? MongoRequestContentInjector.inject(wrapped)
                : attacheBsonContent;
    }

    /**
     *
     * @return the fileInputStream in case of a file resouces the fileInputStream,
     *         null othewise
     * @throws org.restheart.exchange.BadRequestException
     * @throws java.io.IOException
     */
    public InputStream getFileInputStream() throws BadRequestException, IOException {
        if (!isContentInjected()) {
            LOGGER.debug("getFileInputStream() called but content has not been injected yet. Let's inject it.");
            MongoRequestContentInjector.inject(wrapped); // inject calls request.setFileInputStream(fileInputStream);
        }

        return fileInputStream;
    }

    /**
     * @param fileInputStream the fileInputStream to set
     */
    public void setFileInputStream(InputStream fileInputStream) {
        this.fileInputStream = fileInputStream;
        setContentInjected(true);
    }

    /**
     * @return keys
     */
    public Deque<String> getKeys() {
        return keys;
    }

    /**
     * @param keys keys to set
     */
    public void setKeys(Deque<String> keys) {
        this.keys = keys;
    }

    /**
     * @return the halMode
     */
    public HAL_MODE getHalMode() {
        return halMode;
    }

    /**
     *
     * @return
     */
    public boolean isFullHalMode() {
        return halMode == HAL_MODE.FULL || halMode == HAL_MODE.F;
    }

    /**
     * @param halMode the halMode to set
     */
    public void setHalMode(HAL_MODE halMode) {
        this.halMode = halMode;
    }

    /**
     * Seehttps://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     *
     * @return
     */
    public boolean isDbNameInvalid() {
        return isDbNameInvalid(getDBName());
    }

    /**
     *
     * @return
     */
    public long getRequestStartTime() {
        return requestStartTime;
    }

    /**
     * @param dbName
     *               Seehttps://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isDbNameInvalid(String dbName) {
        return (dbName == null
                || dbName.contains(NUL)
                || dbName.contains(" ")
                || dbName.contains("/")
                || dbName.contains("\\")
                || dbName.contains(".")
                || dbName.contains("\"")
                || dbName.contains("$")
                || dbName.length() > 64
                || dbName.length() == 0);
    }

    /**
     * Seehttps://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     *
     * @return
     */
    public boolean isDbNameInvalidOnWindows() {
        return isDbNameInvalidOnWindows(getDBName());
    }

    /**
     * @param dbName
     *               Seehttps://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isDbNameInvalidOnWindows(String dbName) {
        return (isDbNameInvalid()
                || dbName.contains("*")
                || dbName.contains("<")
                || dbName.contains(">")
                || dbName.contains(":")
                || dbName.contains(".")
                || dbName.contains("|")
                || dbName.contains("?"));
    }

    /**
     * Seehttps://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     *
     * @return
     */
    public boolean isCollectionNameInvalid() {
        return isCollectionNameInvalid(getCollectionName());
    }

    /**
     * @param collectionName
     *                       Seehttps://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isCollectionNameInvalid(String collectionName) {
        // collection starting with system. will return FORBIDDEN

        return (collectionName == null
                || collectionName.contains(NUL)
                || collectionName.contains("$")
                || collectionName.length() == 64);
    }

    /**
     *
     * @return
     */
    public String getETag() {
        return etag;
    }

    /**
     * @return the shardKey
     */
    public BsonDocument getShardKey() {
        return shardKey;
    }

    /**
     * @param shardKey the shardKey to set
     */
    public void setShardKey(BsonDocument shardKey) {
        this.shardKey = shardKey;
    }

    /**
     * @return the noProps
     */
    public boolean isNoProps() {
        return noProps;
    }

    /**
     * @param noProps the noProps to set
     */
    public void setNoProps(boolean noProps) {
        this.noProps = noProps;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.AGGREGATION
     */
    public boolean isAggregation() {
        return getType() == TYPE.AGGREGATION;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.BULK_DOCUMENTS
     */
    public boolean isBulkDocuments() {
        return getType() == TYPE.BULK_DOCUMENTS;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION
     */
    public boolean isCollection() {
        return getType() == TYPE.COLLECTION;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION_INDEXES
     */
    public boolean isCollectionIndexes() {
        return getType() == TYPE.COLLECTION_INDEXES;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DB
     */
    public boolean isDb() {
        return getType() == TYPE.DB;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DOCUMENT
     */
    public boolean isDocument() {
        return getType() == TYPE.DOCUMENT;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILE
     */
    public boolean isFile() {
        return getType() == TYPE.FILE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILES_BUCKET
     */
    public boolean isFilesBucket() {
        return getType() == TYPE.FILES_BUCKET;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILE_BINARY
     */
    public boolean isFileBinary() {
        return getType() == TYPE.FILE_BINARY;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.INDEX
     */
    public boolean isIndex() {
        return getType() == TYPE.INDEX;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.ROOT
     */
    public boolean isRoot() {
        return getType() == TYPE.ROOT;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SESSIONS
     */
    public boolean isSessions() {
        return getType() == TYPE.SESSIONS;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SESSION
     */
    public boolean isSession() {
        return getType() == TYPE.SESSION;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.TRANSACTIONS
     */
    public boolean isTxns() {
        return getType() == TYPE.TRANSACTIONS;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.TRANSACTION
     */
    public boolean isTxn() {
        return getType() == TYPE.TRANSACTION;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA
     */
    public boolean isSchema() {
        return getType() == TYPE.SCHEMA;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA_STORE
     */
    public boolean isSchemaStore() {
        return getType() == TYPE.SCHEMA_STORE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.ROOT_SIZE
     */
    public boolean isRootSize() {
        return getType() == TYPE.ROOT_SIZE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DB_SIZE
     */
    public boolean isDbSize() {
        return getType() == TYPE.DB_SIZE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DB_META
     */
    public boolean isDbMeta() {
        return getType() == TYPE.DB_META;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION_SIZE
     */
    public boolean isCollectionSize() {
        return getType() == TYPE.COLLECTION_SIZE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION_META
     */
    public boolean isCollectionMeta() {
        return getType() == TYPE.COLLECTION_META;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILES_BUCKET_SIZE
     */
    public boolean isFilesBucketSize() {
        return getType() == TYPE.FILES_BUCKET_SIZE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILES_BUCKET_META
     */
    public boolean isFilesBucketMeta() {
        return getType() == TYPE.FILES_BUCKET_META;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA_STORE_SIZE
     */
    public boolean isSchemaStoreSize() {
        return getType() == TYPE.SCHEMA_STORE_SIZE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA_STORE_SIZE
     */
    public boolean isSchemaStoreMeta() {
        return getType() == TYPE.SCHEMA_STORE_META;
    }

    /**
     * helper method to check if a request writes a document or a file or a
     * schema
     *
     * @return true if the request writes a document or a file or a schema
     */
    public boolean isWriteDocument() {
        return (((isPut() || isPatch()) && (isFile() || isDocument() || isSchema()))
                || isPost()
                        && (isCollection() || isFilesBucket() || isSchemaStore()));
    }

    /**
     * @return the isETagCheckRequired
     */
    public boolean isETagCheckRequired() {
        return etagCheckRequired;
    }

    /**
     * @param etagCheckRequired
     */
    public void setETagCheckRequired(boolean etagCheckRequired) {
        this.etagCheckRequired = etagCheckRequired;
    }

    /**
     * @return the write mode
     */
    public WRITE_MODE getWriteMode() {
        return writeMode;
    }

    /**
     * @param writeMode the write mode to set
     */
    public void setWriteMode(WRITE_MODE writeMode) {
        this.writeMode = writeMode;
    }

    /**
     * @return the forceEtagCheck
     */
    public boolean isForceEtagCheck() {
        return forceEtagCheck;
    }

    /**
     * @return the noCache
     */
    public boolean isNoCache() {
        return noCache;
    }

    /**
     * ReplicaSet connection otpions
     *
     * @return the _rsOps
     */
    public Optional<RSOps> rsOps() {
        return rsOps;
    }
}
