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
package org.restheart.handlers;

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.PathTemplateMatch;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonMode;
import org.bson.json.JsonParseException;
import org.restheart.Bootstrapper;
import org.restheart.db.CursorPool.EAGER_CURSOR_ALLOCATION_POLICY;
import org.restheart.db.OperationResult;
import org.restheart.db.sessions.ClientSessionImpl;
import org.restheart.representation.Resource.REPRESENTATION_FORMAT;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestContext {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(RequestContext.class);

    // query parameters
    public static final String PAGE_QPARAM_KEY = "page";
    public static final String PAGESIZE_QPARAM_KEY = "pagesize";
    public static final String COUNT_QPARAM_KEY = "count";
    public static final String SORT_BY_QPARAM_KEY = "sort_by";
    public static final String SORT_QPARAM_KEY = "sort";
    public static final String FILTER_QPARAM_KEY = "filter";
    public static final String HINT_QPARAM_KEY = "hint";
    public static final String AGGREGATION_VARIABLES_QPARAM_KEY = "avars";
    public static final String KEYS_QPARAM_KEY = "keys";
    public static final String EAGER_CURSOR_ALLOCATION_POLICY_QPARAM_KEY = "eager";
    public static final String HAL_QPARAM_KEY = "hal";
    public static final String DOC_ID_TYPE_QPARAM_KEY = "id_type";
    public static final String ETAG_CHECK_QPARAM_KEY = "checkEtag";
    public static final String SHARDKEY_QPARAM_KEY = "shardkey";
    public static final String NO_PROPS_KEY = "np";
    public static final String REPRESENTATION_FORMAT_KEY = "rep";
    public static final String CLIENT_SESSION_KEY = "sid";
    public static final String TXNID_KEY = "txn";
    public static final String JSON_MODE_QPARAM_KEY = "jsonMode";
    public static final String NOCACHE_QPARAM_KEY = "nocache";

    // matadata
    public static final String ETAG_DOC_POLICY_METADATA_KEY = "etagDocPolicy";
    public static final String ETAG_POLICY_METADATA_KEY = "etagPolicy";

    // special resource names
    public static final String SYSTEM = "system.";
    public static final String LOCAL = "local";
    public static final String ADMIN = "admin";
    public static final String CONFIG = "config";
    public static final String _METRICS = "_metrics";
    public static final String _SIZE = "_size";
    public static final String _META = "_meta";
    public static final String _SESSIONS = "_sessions";
    public static final String _TRANSACTIONS = "_txns";

    public static final String FS_CHUNKS_SUFFIX = ".chunks";
    public static final String FS_FILES_SUFFIX = ".files";
    public static final String META_COLLNAME = "_properties";
    public static final String DB_META_DOCID = "_properties";
    public static final String COLL_META_DOCID_PREFIX = "_properties.";

    public static final String RESOURCES_WILDCARD_KEY = "*";

    public static final String _INDEXES = "_indexes";
    public static final String _SCHEMAS = "_schemas";
    public static final String _AGGREGATIONS = "_aggrs";
    public static final String _STREAMS = "_streams";

    public static final String BINARY_CONTENT = "binary";

    public static final String MAX_KEY_ID = "_MaxKey";
    public static final String MIN_KEY_ID = "_MinKey";
    public static final String NULL_KEY_ID = "_null";
    public static final String TRUE_KEY_ID = "_true";
    public static final String FALSE_KEY_ID = "_false";

    // other constants
    public static final String SLASH = "/";
    public static final String PATCH = "PATCH";
    public static final String UNDERSCORE = "_";
    private static final String NUL = Character.toString('\0');

    static METHOD selectRequestMethod(HttpString _method) {
        METHOD method;
        if (Methods.GET.equals(_method)) {
            method = METHOD.GET;
        } else if (Methods.POST.equals(_method)) {
            method = METHOD.POST;
        } else if (Methods.PUT.equals(_method)) {
            method = METHOD.PUT;
        } else if (Methods.DELETE.equals(_method)) {
            method = METHOD.DELETE;
        } else if (PATCH.equals(_method.toString())) {
            method = METHOD.PATCH;
        } else if (Methods.OPTIONS.equals(_method)) {
            method = METHOD.OPTIONS;
        } else {
            method = METHOD.OTHER;
        }
        return method;
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
            } else if (pathTokens.length == 4 && pathTokens[2].endsWith(_SCHEMAS)) {
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
            } else if (pathTokens.length == 4 && pathTokens[2].endsWith(_SCHEMAS)) {
                type = TYPE.SCHEMA_STORE_META;
            } else if (pathTokens.length == 4) {
                type = TYPE.COLLECTION_META;
            } else {
                type = TYPE.INVALID;
            }
        } else if (pathTokens.length < 2) {
            type = TYPE.ROOT;
        } else if (pathTokens.length == 2
                && pathTokens[pathTokens.length - 1]
                        .equalsIgnoreCase(_SESSIONS)) {
            type = TYPE.SESSIONS;
        } else if (pathTokens.length == 3
                && pathTokens[pathTokens.length - 2]
                        .equalsIgnoreCase(_SESSIONS)) {
            type = TYPE.SESSION;
        } else if (pathTokens.length == 4
                && pathTokens[pathTokens.length - 3]
                        .equalsIgnoreCase(_SESSIONS)
                && pathTokens[pathTokens.length - 1]
                        .equalsIgnoreCase(_TRANSACTIONS)) {
            type = TYPE.TRANSACTIONS;
        } else if (pathTokens.length == 5
                && pathTokens[pathTokens.length - 4]
                        .equalsIgnoreCase(_SESSIONS)
                && pathTokens[pathTokens.length - 2]
                        .equalsIgnoreCase(_TRANSACTIONS)) {
            type = TYPE.TRANSACTION;
        } else if (pathTokens.length < 3
                && pathTokens[1].equalsIgnoreCase(_METRICS)) {
            type = TYPE.METRICS;
        } else if (pathTokens.length < 3) {
            type = TYPE.DB;
        } else if (pathTokens.length >= 3
                && pathTokens[2].endsWith(FS_FILES_SUFFIX)) {
            if (pathTokens.length == 3) {
                type = TYPE.FILES_BUCKET;
            } else if (pathTokens.length == 4
                    && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
                type = TYPE.COLLECTION_INDEXES;
            } else if (pathTokens.length == 4
                    && !pathTokens[3].equalsIgnoreCase(_INDEXES)
                    && !pathTokens[3].equals(RESOURCES_WILDCARD_KEY)) {
                type = TYPE.FILE;
            } else if (pathTokens.length > 4
                    && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
                type = TYPE.INDEX;
            } else if (pathTokens.length > 4
                    && !pathTokens[3].equalsIgnoreCase(_INDEXES)
                    && !pathTokens[4].equalsIgnoreCase(BINARY_CONTENT)) {
                type = TYPE.FILE;
            } else if (pathTokens.length == 5
                    && pathTokens[4].equalsIgnoreCase(BINARY_CONTENT)) {
                // URL: <host>/db/bucket.filePath/xxx/binary
                type = TYPE.FILE_BINARY;
            } else {
                type = TYPE.DOCUMENT;
            }
        } else if (pathTokens.length >= 3
                && pathTokens[2].endsWith(_SCHEMAS)) {
            if (pathTokens.length == 3) {
                type = TYPE.SCHEMA_STORE;
            } else {
                type = TYPE.SCHEMA;
            }
        } else if (pathTokens.length >= 3
                && pathTokens[2].equalsIgnoreCase(_METRICS)) {
            type = TYPE.METRICS;
        } else if (pathTokens.length < 4) {
            type = TYPE.COLLECTION;
        } else if (pathTokens.length == 4
                && pathTokens[3].equalsIgnoreCase(_METRICS)) {
            type = TYPE.METRICS;
        } else if (pathTokens.length == 4
                && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
            type = TYPE.COLLECTION_INDEXES;
        } else if (pathTokens.length == 4
                && pathTokens[3].equals(RESOURCES_WILDCARD_KEY)) {
            type = TYPE.BULK_DOCUMENTS;
        } else if (pathTokens.length > 4
                && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
            type = TYPE.INDEX;
        } else if (pathTokens.length == 4
                && pathTokens[3].equalsIgnoreCase(_AGGREGATIONS)) {
            type = TYPE.INVALID;
        } else if (pathTokens.length > 4
                && pathTokens[3].equalsIgnoreCase(_AGGREGATIONS)) {
            type = TYPE.AGGREGATION;
        } else if (pathTokens.length == 4
                && pathTokens[3].equalsIgnoreCase(_STREAMS)) {
            type = TYPE.INVALID;
        } else if (pathTokens.length > 4
                && pathTokens[3].equalsIgnoreCase(_STREAMS)) {
            type = TYPE.CHANGE_STREAM;
        } else {
            type = TYPE.DOCUMENT;
        }

        return type;
    }

    /**
     *
     * @param dbName
     * @return true if the dbName is a reserved resource
     */
    public static boolean isReservedResourceDb(String dbName) {
        return !dbName.equalsIgnoreCase(_METRICS)
                && !dbName.equalsIgnoreCase(_SIZE)
                && !dbName.equalsIgnoreCase(_SESSIONS)
                && (dbName.equals(ADMIN)
                || dbName.equals(CONFIG)
                || dbName.equals(LOCAL)
                || dbName.startsWith(SYSTEM)
                || dbName.startsWith(UNDERSCORE)
                || dbName.equals(RESOURCES_WILDCARD_KEY));
    }

    /**
     *
     * @param collectionName
     * @return true if the collectionName is a reserved resource
     */
    public static boolean isReservedResourceCollection(String collectionName) {
        return collectionName != null
                && !collectionName.equalsIgnoreCase(_SCHEMAS)
                && !collectionName.equalsIgnoreCase(_METRICS)
                && !collectionName.equalsIgnoreCase(_META)
                && !collectionName.equalsIgnoreCase(_SIZE)
                && (collectionName.startsWith(SYSTEM)
                || collectionName.startsWith(UNDERSCORE)
                || collectionName.endsWith(FS_CHUNKS_SUFFIX)
                || collectionName.equals(RESOURCES_WILDCARD_KEY));
    }

    /**
     *
     * @param type
     * @param documentIdRaw
     * @return true if the documentIdRaw is a reserved resource
     */
    public static boolean isReservedResourceDocument(
            TYPE type,
            String documentIdRaw) {
        if (documentIdRaw == null) {
            return false;
        }

        return (documentIdRaw.startsWith(UNDERSCORE)
                || (type != TYPE.AGGREGATION
                && _AGGREGATIONS.equalsIgnoreCase(documentIdRaw)))
                && (type == TYPE.TRANSACTION
                || !_TRANSACTIONS.equalsIgnoreCase(documentIdRaw))
                && (documentIdRaw.startsWith(UNDERSCORE)
                || (type != TYPE.CHANGE_STREAM
                && _STREAMS.equalsIgnoreCase(documentIdRaw)))
                && !documentIdRaw.equalsIgnoreCase(_METRICS)
                && !documentIdRaw.equalsIgnoreCase(_SIZE)
                && !documentIdRaw.equalsIgnoreCase(_INDEXES)
                && !documentIdRaw.equalsIgnoreCase(_META)
                && !documentIdRaw.equalsIgnoreCase(DB_META_DOCID)
                && !documentIdRaw.startsWith(COLL_META_DOCID_PREFIX)
                && !documentIdRaw.equalsIgnoreCase(MIN_KEY_ID)
                && !documentIdRaw.equalsIgnoreCase(MAX_KEY_ID)
                && !documentIdRaw.equalsIgnoreCase(NULL_KEY_ID)
                && !documentIdRaw.equalsIgnoreCase(TRUE_KEY_ID)
                && !documentIdRaw.equalsIgnoreCase(FALSE_KEY_ID)
                && !(type == TYPE.AGGREGATION)
                && !(type == TYPE.CHANGE_STREAM)
                && !(type == TYPE.TRANSACTION)
                || (documentIdRaw.equals(RESOURCES_WILDCARD_KEY)
                && !(type == TYPE.BULK_DOCUMENTS));
    }

    private final String whereUri;
    private final String whatUri;

    private final TYPE type;
    private final METHOD method;
    private final String[] pathTokens;

    private BsonDocument dbProps;
    private BsonDocument collectionProps;

    private BsonValue content;

    private String rawContent;

    private Path filePath;

    private BsonValue responseContent;

    private int responseStatusCode;

    private String responseContentType;

    private final List<String> warnings = new ArrayList<>();

    private int page = 1;
    private int pagesize = 100;
    private boolean count = false;
    private EAGER_CURSOR_ALLOCATION_POLICY cursorAllocationPolicy;
    private Deque<String> filter = null;
    private BsonDocument aggregationVars = null; // aggregation vars
    private Deque<String> keys = null;
    private Deque<String> sortBy = null;
    private Deque<String> hint = null;
    private DOC_ID_TYPE docIdType = DOC_ID_TYPE.STRING_OID;

    private REPRESENTATION_FORMAT representationFormat;

    private BsonValue documentId;

    private String mappedUri = null;
    private String unmappedUri = null;

    private final String etag;

    private boolean forceEtagCheck = false;

    private OperationResult dbOperationResult;

    private BsonDocument shardKey = null;

    private boolean noProps = false;

    private boolean inError = false;

    private Account authenticatedAccount = null;

    private ClientSessionImpl clientSession = null;

    /**
     * the HAL mode
     */
    private HAL_MODE halMode = HAL_MODE.FULL;

    private final long requestStartTime = System.currentTimeMillis();

    // path template match
    private final PathTemplateMatch pathTemplateMatch;

    private final JsonMode jsonMode;
    
    final boolean noCache;

    /**
     *
     * @param exchange the url rewriting feature is implemented by the whatUri
     * and whereUri parameters.
     *
     * the exchange request path (mapped uri) is rewritten replacing the
     * whereUri string with the whatUri string the special whatUri value * means
     * any resource: the whereUri is replaced with /
     *
     * example 1
     *
     * whatUri = /db/mycollection whereUri = /
     *
     * then the requestPath / is rewritten to /db/mycollection
     *
     * example 2
     *
     * whatUri = * whereUri = /data
     *
     * then the requestPath /data is rewritten to /
     *
     * @param whereUri the uri to map to
     * @param whatUri the uri to map
     */
    public RequestContext(
            HttpServerExchange exchange,
            String whereUri,
            String whatUri) {
        this.whereUri = URLUtils.removeTrailingSlashes(whereUri == null ? null
                : whereUri.startsWith("/") ? whereUri
                : "/" + whereUri);

        this.whatUri = URLUtils.removeTrailingSlashes(
                whatUri == null ? null
                        : whatUri.startsWith("/")
                        || "*".equals(whatUri) ? whatUri
                        : "/" + whatUri);

        this.mappedUri = exchange.getRequestPath();

        if (exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY) != null) {
            this.pathTemplateMatch = exchange
                    .getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
        } else {
            this.pathTemplateMatch = null;
        }

        this.unmappedUri = unmapUri(exchange.getRequestPath());

        // "/db/collection/document" --> { "", "mappedDbName", "collection", "document" }
        this.pathTokens = this.unmappedUri.split(SLASH);
        this.type = selectRequestType(pathTokens);

        this.method = selectRequestMethod(exchange.getRequestMethod());

        // etag
        HeaderValues etagHvs = exchange.getRequestHeaders() == null
                ? null : exchange.getRequestHeaders().get(Headers.IF_MATCH);

        this.etag = etagHvs == null || etagHvs.getFirst() == null
                ? null
                : etagHvs.getFirst();

        this.forceEtagCheck = exchange
                .getQueryParameters()
                .get(ETAG_CHECK_QPARAM_KEY) != null;

        this.noProps = exchange.getQueryParameters().get(NO_PROPS_KEY) != null;

        var _jsonMode = exchange.getQueryParameters().containsKey(JSON_MODE_QPARAM_KEY)
                ? exchange.getQueryParameters().get(JSON_MODE_QPARAM_KEY).getFirst().toUpperCase()
                : null;

        if (_jsonMode != null) {
            JsonMode jsonMode = null;

            try {
                jsonMode = JsonMode.valueOf(_jsonMode.toUpperCase());
            } catch (IllegalArgumentException iae) {
                jsonMode = null;
            }

            this.jsonMode = jsonMode;
        } else {
            this.jsonMode = null;
        }
        
        this.noCache = exchange.getQueryParameters().get(NOCACHE_QPARAM_KEY) != null;
    }

    /**
     * given a mapped uri (/some/mapping/coll) returns the canonical uri
     * (/db/coll) URLs are mapped to mongodb resources by using the mongo-mounts
     * configuration properties. note that the mapped uri can make use of path
     * templates (/some/{path}/template/*)
     *
     * @param mappedUri
     * @return
     */
    private String unmapUri(String mappedUri) {
        // don't unmpa URIs statring with /_sessions
        if (mappedUri.startsWith("/".concat(_SESSIONS))) {
            return mappedUri;
        }

        if (this.pathTemplateMatch == null) {
            return unmapPathUri(mappedUri);
        } else {
            return unmapPathTemplateUri(mappedUri);
        }
    }

    private String unmapPathUri(String mappedUri) {
        String ret = URLUtils.removeTrailingSlashes(mappedUri);

        if (whatUri.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                ret = ret.replaceFirst("^" + this.whereUri, "");
            }
        } else if (!this.whereUri.equals(SLASH)) {
            ret = URLUtils.removeTrailingSlashes(
                    ret.replaceFirst("^" + this.whereUri, this.whatUri));
        } else {
            ret = URLUtils.removeTrailingSlashes(
                    URLUtils.removeTrailingSlashes(this.whatUri) + ret);
        }

        if (ret.isEmpty()) {
            ret = SLASH;
        }

        return ret;
    }

    private String unmapPathTemplateUri(String mappedUri) {
        String ret = URLUtils.removeTrailingSlashes(mappedUri);
        String rewriteUri = replaceParamsWithActualValues();

        String replacedWhatUri = replaceParamsWithinWhatUri();
        // replace params with in whatUri
        // eg what: /{account}, where: /{account/*

        // now replace mappedUri with resolved path template
        if (replacedWhatUri.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                ret = ret.replaceFirst("^" + rewriteUri, "");
            }
        } else if (!this.whereUri.equals(SLASH)) {
            ret = URLUtils.removeTrailingSlashes(
                    ret.replaceFirst("^" + rewriteUri, replacedWhatUri));
        } else {
            ret = URLUtils.removeTrailingSlashes(
                    URLUtils.removeTrailingSlashes(replacedWhatUri) + ret);
        }

        if (ret.isEmpty()) {
            ret = SLASH;
        }

        return ret;
    }

    /**
     * given a canonical uri (/db/coll) returns the mapped uri
     * (/some/mapping/uri) relative to this context. URLs are mapped to mongodb
     * resources via the mongo-mounts configuration properties
     *
     * @param unmappedUri
     * @return
     */
    public String mapUri(String unmappedUri) {
        if (this.pathTemplateMatch == null) {
            return mapPathUri(unmappedUri);
        } else {
            return mapPathTemplateUri(unmappedUri);
        }
    }

    private String mapPathUri(String unmappedUri) {
        String ret = URLUtils.removeTrailingSlashes(unmappedUri);

        if (whatUri.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                return this.whereUri + unmappedUri;
            }
        } else {
            ret = URLUtils.removeTrailingSlashes(
                    ret.replaceFirst("^" + this.whatUri, this.whereUri));
        }
        
        if (ret.isEmpty()) {
            ret = SLASH;
        } else {
            ret = ret.replaceAll("//", "/");
        }

        return ret;
    }

    private String mapPathTemplateUri(String unmappedUri) {
        String ret = URLUtils.removeTrailingSlashes(unmappedUri);
        String rewriteUri = replaceParamsWithActualValues();
        String replacedWhatUri = replaceParamsWithinWhatUri();

        // now replace mappedUri with resolved path template
        if (replacedWhatUri.equals("*")) {
            if (!this.whereUri.equals(SLASH)) {
                return rewriteUri + unmappedUri;
            }
        } else {
            ret = URLUtils.removeTrailingSlashes(
                    ret.replaceFirst("^" + replacedWhatUri, rewriteUri));
        }

        if (ret.isEmpty()) {
            ret = SLASH;
        }

        return ret;
    }

    private String replaceParamsWithinWhatUri() {
        String uri = this.whatUri;
        // replace params within whatUri
        // eg what: /{prefix}_db, where: /{prefix}/*
        for (String key : this.pathTemplateMatch
                .getParameters().keySet()) {
            uri = uri.replace(
                    "{".concat(key).concat("}"),
                    this.pathTemplateMatch
                            .getParameters().get(key));
        }
        return uri;
    }

    private String replaceParamsWithActualValues() {
        String rewriteUri;
        // path template with variables resolved to actual values
        rewriteUri = this.pathTemplateMatch.getMatchedTemplate();
        // remove trailing wildcard from template
        if (rewriteUri.endsWith("/*")) {
            rewriteUri = rewriteUri.substring(0, rewriteUri.length() - 2);
        }
        // collect params
        this.pathTemplateMatch
                .getParameters()
                .keySet()
                .stream()
                .filter(key -> !key.equals("*"))
                .collect(Collectors.toMap(
                        key -> key,
                        key -> this.pathTemplateMatch
                                .getParameters().get(key)));
        // replace params with actual values
        for (String key : this.pathTemplateMatch
                .getParameters().keySet()) {
            rewriteUri = rewriteUri.replace(
                    "{".concat(key).concat("}"),
                    this.pathTemplateMatch
                            .getParameters().get(key));
        }
        return rewriteUri;
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
        return type == TYPE.DB
                ? mappedUri.split(SLASH).length > 1
                : mappedUri.split(SLASH).length > 2;
    }

    /**
     *
     * @return type
     */
    public TYPE getType() {
        return type;
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
     * or TRANSACTION
     */
    public String getSid() {
        return isTxn() || isTxns() || isSessions() ? getPathTokenAt(2) : null;
    }

    /**
     *
     * @return the txn id or null if request type is not TRANSACTION
     */
    public long getTxnId() {
        return isTxn() ? Long.parseLong(getPathTokenAt(4)) : null;
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

    public String getChangeStreamIdentifier() {
        return getPathTokenAt(5);
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
     * @return method
     */
    public METHOD getMethod() {
        return method;
    }

    /**
     *
     * @return isReservedResource
     */
    public boolean isReservedResource() {
        if (type == TYPE.ROOT) {
            return false;
        }

        return isReservedResourceDb(getDBName())
                || isReservedResourceCollection(getCollectionName())
                || isReservedResourceDocument(type, getDocumentIdRaw());
    }

    /**
     * @return the whereUri
     */
    public String getUriPrefix() {
        return whereUri;
    }

    /**
     * @return the whatUri
     */
    public String getMappingUri() {
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
    public void setRepresentationFormat(
            REPRESENTATION_FORMAT representationFormat) {
        this.representationFormat = representationFormat;
    }

    /**
     * @return the count
     */
    public boolean isCount() {
        return count
                || this.type == TYPE.ROOT_SIZE
                || this.type == TYPE.COLLECTION_SIZE
                || this.type == TYPE.FILES_BUCKET_SIZE
                || this.type == TYPE.SCHEMA_STORE_SIZE;
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
        final BsonDocument filterQuery = new BsonDocument();

        if (filter != null) {
            if (filter.size() > 1) {
                BsonArray _filters = new BsonArray();

                filter.stream().forEach((String f) -> {
                    _filters.add(BsonDocument.parse(f));
                });

                filterQuery.put("$and", _filters);
            } else if (filter.size() == 1) {
                filterQuery.putAll(BsonDocument.parse(filter.getFirst()));  // this can throw JsonParseException for invalid filter parameters
            } else {
                return filterQuery;
            }
        }

        return filterQuery;
    }

    public BsonDocument getSortByDocument() throws JsonParseException {
        BsonDocument sort = new BsonDocument();

        if (sortBy == null) {
            sort.put("_id", new BsonInt32(-1));
        } else {
            sortBy.stream().forEach((s) -> {

                String _s = s.trim(); // the + sign is decoded into a space, in case remove it

                // manage the case where sort_by is a json object
                try {
                    BsonDocument _sort = BsonDocument.parse(_s);

                    sort.putAll(_sort);
                } catch (JsonParseException e) {
                    // sort_by is just a string, i.e. a property name
                    if (_s.startsWith("-")) {
                        sort.put(_s.substring(1), new BsonInt32(-1));
                    } else if (_s.startsWith("+")) {
                        sort.put(_s.substring(1), new BsonInt32(11));
                    } else {
                        sort.put(_s, new BsonInt32(1));
                    }
                }
            });
        }

        return sort;
    }

    public BsonDocument getHintDocument() throws JsonParseException {
        BsonDocument ret = new BsonDocument();

        if (hint == null || hint.isEmpty()) {
            return null;
        } else {
            hint.stream().forEach((s) -> {

                String _s = s.trim(); // the + sign is decoded into a space, in case remove it

                // manage the case where hint is a json object
                try {
                    BsonDocument _hint = BsonDocument.parse(_s);

                    ret.putAll(_hint);
                } catch (JsonParseException e) {
                    // ret is just a string, i.e. an index name
                    if (_s.startsWith("-")) {
                        ret.put(_s.substring(1), new BsonInt32(-1));
                    } else if (_s.startsWith("+")) {
                        ret.put(_s.substring(1), new BsonInt32(11));
                    } else {
                        ret.put(_s, new BsonInt32(1));
                    }
                }
            });
        }

        return ret;
    }

    public BsonDocument getProjectionDocument() throws JsonParseException {
        final BsonDocument projection = new BsonDocument();

        if (keys == null || keys.isEmpty()) {
            return null;
        } else {
            keys.stream().forEach((String f) -> {
                projection.putAll(BsonDocument.parse(f));  // this can throw JsonParseException for invalid keys parameters
            });
        }

        return projection;
    }

    /**
     * @return the aggregationVars
     */
    public BsonDocument getAggreationVars() {
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
     * @return the content
     */
    public BsonValue getContent() {
        return content;
    }

    /**
     * @param content the content to set
     */
    public void setContent(BsonValue content) {
        if (content != null
                && !(content.isDocument()
                || content.isArray())) {
            throw new IllegalArgumentException("content must be "
                    + "either an object or an array");
        }
        this.content = content;
    }

    /**
     * @return the rawContent
     */
    public String getRawContent() {
        return rawContent;
    }

    /**
     * @param rawContent the rawContent to set
     */
    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    /**
     * @return the warnings
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * @param warning
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     *
     * The unmapped uri is the cononical uri of a mongodb resource (e.g.
     * /db/coll).
     *
     * @return the unmappedUri
     */
    public String getUnmappedRequestUri() {
        return unmappedUri;
    }

    /**
     * The mapped uri is the exchange request uri. This is "mapped" by the
     * mongo-mounts mapping paramenters.
     *
     * @return the mappedUri
     */
    public String getMappedRequestUri() {
        return mappedUri;
    }

    /**
     * if mongo-mounts specifies a path template (i.e. /{foo}/*) this returns
     * the request template parameters (/x/y => foo=x, *=y)
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
     * @return the cursorAllocationPolicy
     */
    public EAGER_CURSOR_ALLOCATION_POLICY getCursorAllocationPolicy() {
        return cursorAllocationPolicy;
    }

    /**
     * @param cursorAllocationPolicy the cursorAllocationPolicy to set
     */
    public void setCursorAllocationPolicy(
            EAGER_CURSOR_ALLOCATION_POLICY cursorAllocationPolicy) {
        this.cursorAllocationPolicy = cursorAllocationPolicy;
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
     * @return the responseContent
     */
    public BsonValue getResponseContent() {
        return responseContent;
    }

    /**
     * @param responseContent the responseContent to set
     */
    public void setResponseContent(BsonValue responseContent) {
        if (responseContent != null
                && !(responseContent.isDocument()
                || responseContent.isArray())) {
            throw new IllegalArgumentException("response content must be "
                    + "either an object or an array");
        }

        this.responseContent = responseContent;
    }

    /**
     * @return the responseStatusCode
     */
    public int getResponseStatusCode() {
        return responseStatusCode;
    }

    /**
     * @param responseStatusCode the responseStatusCode to set
     */
    public void setResponseStatusCode(int responseStatusCode) {
        this.responseStatusCode = responseStatusCode;
    }

    /**
     * @return the responseContentType
     */
    public String getResponseContentType() {
        return responseContentType;
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setResponseContentType(String responseContentType) {
        this.responseContentType = responseContentType;
    }

    /**
     * @return the filePath
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * @param filePath the filePath to set
     */
    public void setFilePath(Path filePath) {
        this.filePath = filePath;
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
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isDbNameInvalid() {
        return isDbNameInvalid(getDBName());
    }

    public long getRequestStartTime() {
        return requestStartTime;
    }

    /**
     * @param dbName
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
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
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isDbNameInvalidOnWindows() {
        return isDbNameInvalidOnWindows(getDBName());
    }

    /**
     * @param dbName
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
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
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isCollectionNameInvalid() {
        return isCollectionNameInvalid(getCollectionName());
    }

    /**
     * @param collectionName
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isCollectionNameInvalid(String collectionName) {
        // collection starting with system. will return FORBIDDEN

        return (collectionName == null
                || collectionName.contains(NUL)
                || collectionName.contains("$")
                || collectionName.length() == 64);
    }

    public String getETag() {
        return etag;
    }

    public boolean isETagCheckRequired() {
        // if client specifies the If-Match header, than check it
        if (getETag() != null) {
            return true;
        }

        // if client requires the check via qparam return true
        if (forceEtagCheck) {
            return true;
        }

        // for documents consider db and coll etagDocPolicy metadata
        if (type == TYPE.DOCUMENT || type == TYPE.FILE) {
            // check the coll metadata
            BsonValue _policy = collectionProps != null
                    ? collectionProps.get(ETAG_DOC_POLICY_METADATA_KEY)
                    : null;

            LOGGER.trace(
                    "collection etag policy (from coll properties) {}",
                    _policy);

            if (_policy == null) {
                // check the db metadata
                _policy = dbProps != null ? dbProps.get(ETAG_DOC_POLICY_METADATA_KEY)
                        : null;
                LOGGER.trace(
                        "collection etag policy (from db properties) {}",
                        _policy);
            }

            ETAG_CHECK_POLICY policy = null;

            if (_policy != null && _policy.isString()) {
                try {
                    policy = ETAG_CHECK_POLICY
                            .valueOf(_policy.asString().getValue()
                                    .toUpperCase());
                } catch (IllegalArgumentException iae) {
                    policy = null;
                }
            }

            if (null != policy) {
                if (method == METHOD.DELETE) {
                    return policy != ETAG_CHECK_POLICY.OPTIONAL;
                } else {
                    return policy == ETAG_CHECK_POLICY.REQUIRED;
                }
            }
        }

        // for db consider db etagPolicy metadata
        if (type == TYPE.DB && dbProps != null) {
            // check the coll  metadata
            BsonValue _policy = dbProps.get(ETAG_POLICY_METADATA_KEY);

            LOGGER.trace("db etag policy (from db properties) {}", _policy);

            ETAG_CHECK_POLICY policy = null;

            if (_policy != null && _policy.isString()) {
                try {
                    policy = ETAG_CHECK_POLICY.valueOf(
                            _policy.asString().getValue()
                                    .toUpperCase());
                } catch (IllegalArgumentException iae) {
                    policy = null;
                }
            }

            if (null != policy) {
                if (method == METHOD.DELETE) {
                    return policy != ETAG_CHECK_POLICY.OPTIONAL;
                } else {
                    return policy == ETAG_CHECK_POLICY.REQUIRED;
                }
            }
        }

        // for collection consider coll and db etagPolicy metadata
        if (type == TYPE.COLLECTION && collectionProps != null) {
            // check the coll  metadata
            BsonValue _policy = collectionProps.get(ETAG_POLICY_METADATA_KEY);

            LOGGER.trace(
                    "coll etag policy (from coll properties) {}",
                    _policy);

            if (_policy == null) {
                // check the db metadata
                _policy = dbProps != null ? dbProps.get(ETAG_POLICY_METADATA_KEY)
                        : null;

                LOGGER.trace(
                        "coll etag policy (from db properties) {}",
                        _policy);
            }

            ETAG_CHECK_POLICY policy = null;

            if (_policy != null && _policy.isString()) {
                try {
                    policy = ETAG_CHECK_POLICY.valueOf(
                            _policy.asString().getValue()
                                    .toUpperCase());
                } catch (IllegalArgumentException iae) {
                    policy = null;
                }
            }

            if (null != policy) {
                if (method == METHOD.DELETE) {
                    return policy != ETAG_CHECK_POLICY.OPTIONAL;
                } else {
                    return policy == ETAG_CHECK_POLICY.REQUIRED;
                }
            }
        }

        // apply the default policy from configuration
        ETAG_CHECK_POLICY dbP = Bootstrapper.getConfiguration()
                .getDbEtagCheckPolicy();

        ETAG_CHECK_POLICY collP = Bootstrapper.getConfiguration()
                .getCollEtagCheckPolicy();

        ETAG_CHECK_POLICY docP = Bootstrapper.getConfiguration()
                .getDocEtagCheckPolicy();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("default etag db check (from conf) {}", dbP);
            LOGGER.trace("default etag coll check (from conf) {}", collP);
            LOGGER.trace("default etag doc check (from conf) {}", docP);
        }

        ETAG_CHECK_POLICY policy = null;

        if (null != type) {
            switch (type) {
                case DB:
                    policy = dbP;
                    break;
                case COLLECTION:
                case FILES_BUCKET:
                case SCHEMA_STORE:
                    policy = collP;
                    break;
                default:
                    policy = docP;
            }
        }

        if (null != policy) {
            if (method == METHOD.DELETE) {
                return policy != ETAG_CHECK_POLICY.OPTIONAL;
            } else {
                return policy == ETAG_CHECK_POLICY.REQUIRED;
            }
        }

        return false;
    }

    /**
     * @return the dbOperationResult
     */
    public OperationResult getDbOperationResult() {
        return dbOperationResult;
    }

    /**
     * @param dbOperationResult the dbOperationResult to set
     */
    public void setDbOperationResult(OperationResult dbOperationResult) {
        this.dbOperationResult = dbOperationResult;
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
     * @return the inError
     */
    public boolean isInError() {
        return inError;
    }

    /**
     * @param inError the inError to set
     */
    public void setInError(boolean inError) {
        this.inError = inError;
    }

    /**
     * @return the authenticatedAccount
     */
    public Account getAuthenticatedAccount() {
        return authenticatedAccount;
    }

    /**
     * @param authenticatedAccount the authenticatedAccount to set
     */
    public void setAuthenticatedAccount(Account authenticatedAccount) {
        this.authenticatedAccount = authenticatedAccount;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.AGGREGATION
     */
    public boolean isAggregation() {
        return this.type == TYPE.AGGREGATION;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.BULK_DOCUMENTS
     */
    public boolean isBulkDocuments() {
        return this.type == TYPE.BULK_DOCUMENTS;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION
     */
    public boolean isCollection() {
        return this.type == TYPE.COLLECTION;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION_INDEXES
     */
    public boolean isCollectionIndexes() {
        return this.type == TYPE.COLLECTION_INDEXES;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DB
     */
    public boolean isDb() {
        return this.type == TYPE.DB;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DOCUMENT
     */
    public boolean isDocument() {
        return this.type == TYPE.DOCUMENT;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILE
     */
    public boolean isFile() {

        return this.type == TYPE.FILE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILES_BUCKET
     */
    public boolean isFilesBucket() {
        return this.type == TYPE.FILES_BUCKET;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILE_BINARY
     */
    public boolean isFileBinary() {
        return this.type == TYPE.FILE_BINARY;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.INDEX
     */
    public boolean isIndex() {
        return this.type == TYPE.INDEX;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.ROOT
     */
    public boolean isRoot() {
        return this.type == TYPE.ROOT;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.TRANSACTIONS
     */
    public boolean isSessions() {
        return this.type == TYPE.SESSIONS;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.TRANSACTIONS
     */
    public boolean isTxns() {
        return this.type == TYPE.TRANSACTIONS;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.TRANSACTION
     */
    public boolean isTxn() {
        return this.type == TYPE.TRANSACTION;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA
     */
    public boolean isSchema() {
        return this.type == TYPE.SCHEMA;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA_STORE
     */
    public boolean isSchemaStore() {
        return this.type == TYPE.SCHEMA_STORE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.ROOT_SIZE
     */
    public boolean isRootSize() {
        return this.type == TYPE.ROOT_SIZE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DB_SIZE
     */
    public boolean isDbSize() {
        return this.type == TYPE.DB_SIZE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DB_META
     */
    public boolean isDbMeta() {
        return this.type == TYPE.DB_META;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION_SIZE
     */
    public boolean isCollectionSize() {
        return this.type == TYPE.COLLECTION_SIZE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION_META
     */
    public boolean isCollectionMeta() {
        return this.type == TYPE.COLLECTION_META;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILES_BUCKET_SIZE
     */
    public boolean isFilesBucketSize() {
        return this.type == TYPE.FILES_BUCKET_SIZE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILES_BUCKET_META
     */
    public boolean isFilesBucketMeta() {
        return this.type == TYPE.FILES_BUCKET_META;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA_STORE_SIZE
     */
    public boolean isSchemaStoreSize() {
        return this.type == TYPE.SCHEMA_STORE_SIZE;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA_STORE_SIZE
     */
    public boolean isSchemaStoreMeta() {
        return this.type == TYPE.SCHEMA_STORE_META;
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.METRICS
     */
    public boolean isMetrics() {
        return this.type == TYPE.METRICS;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.DELETE
     */
    public boolean isDelete() {
        return this.method == METHOD.DELETE;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.GET
     */
    public boolean isGet() {
        return this.method == METHOD.GET;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.OPTIONS
     */
    public boolean isOptions() {
        return this.method == METHOD.OPTIONS;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PATCH
     */
    public boolean isPatch() {
        return this.method == METHOD.PATCH;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.POST
     */
    public boolean isPost() {
        return this.method == METHOD.POST;
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PUT
     */
    public boolean isPut() {
        return this.method == METHOD.PUT;
    }

    /**
     * @return the clientSession
     */
    public ClientSessionImpl getClientSession() {
        return clientSession;
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

    public enum TYPE {
        INVALID,
        ROOT,
        ROOT_SIZE,
        DB,
        DB_SIZE,
        DB_META,
        CHANGE_STREAM,
        COLLECTION,
        COLLECTION_SIZE,
        COLLECTION_META,
        DOCUMENT,
        COLLECTION_INDEXES,
        INDEX,
        FILES_BUCKET,
        FILES_BUCKET_SIZE,
        FILES_BUCKET_META,
        FILE,
        FILE_BINARY,
        AGGREGATION,
        SCHEMA,
        SCHEMA_STORE,
        SCHEMA_STORE_SIZE,
        SCHEMA_STORE_META,
        BULK_DOCUMENTS,
        METRICS,
        SESSION,
        SESSIONS,
        TRANSACTIONS,
        TRANSACTION
    }

    public enum METHOD {
        GET,
        POST,
        PUT,
        DELETE,
        PATCH,
        OPTIONS,
        OTHER
    }

    public enum DOC_ID_TYPE {
        OID, // ObjectId
        STRING_OID, // String eventually converted to ObjectId in case ObjectId.isValid() is true
        STRING, // String
        NUMBER, // any Number (including mongodb NumberLong)
        DATE, // Date
        MINKEY, // org.bson.types.MinKey;
        MAXKEY, // org.bson.types.MaxKey
        NULL, // null
        BOOLEAN     // boolean
    }

    public enum HAL_MODE {
        FULL, // full mode
        F, // alias for full
        COMPACT, // new compact mode
        C           // alias for compact
    }

    public enum ETAG_CHECK_POLICY {
        REQUIRED, // always requires the etag, return PRECONDITION FAILED if missing
        REQUIRED_FOR_DELETE, // only requires the etag for DELETE, return PRECONDITION FAILED if missing
        OPTIONAL                // checks the etag only if provided by client via If-Match header
    }

    /**
     * @return the noCache
     */
    public boolean isNoCache() {
        return noCache;
    }
}
