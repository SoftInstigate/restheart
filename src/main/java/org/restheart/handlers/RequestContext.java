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

import org.restheart.db.CursorPool.EAGER_CURSOR_ALLOCATION_POLICY;
import org.restheart.utils.URLUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.restheart.Bootstrapper;
import org.restheart.db.OperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestContext {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(RequestContext.class);

    public enum TYPE {
        INVALID,
        ROOT,
        DB,
        COLLECTION,
        DOCUMENT,
        COLLECTION_INDEXES,
        INDEX,
        FILES_BUCKET,
        FILE,
        FILE_BINARY,
        AGGREGATION,
        SCHEMA,
        SCHEMA_STORE,
        BULK_DOCUMENTS
    };

    public enum METHOD {
        GET,
        POST,
        PUT,
        DELETE,
        PATCH,
        OPTIONS,
        OTHER
    };

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

    // query parameters
    public static final String PAGE_QPARAM_KEY = "page";
    public static final String PAGESIZE_QPARAM_KEY = "pagesize";
    public static final String COUNT_QPARAM_KEY = "count";
    public static final String SORT_BY_QPARAM_KEY = "sort_by";
    public static final String FILTER_QPARAM_KEY = "filter";
    public static final String AGGREGATION_VARIABLES_QPARAM_KEY = "avars";
    public static final String KEYS_QPARAM_KEY = "keys";
    public static final String EAGER_CURSOR_ALLOCATION_POLICY_QPARAM_KEY = "eager";
    public static final String HAL_QPARAM_KEY = "hal";
    public static final String DOC_ID_TYPE_QPARAM_KEY = "id_type";
    public static final String ETAG_CHECK_QPARAM_KEY = "checkEtag";
    public static final String SHARDKEY_QPARAM_KEY = "shardkey";
    public static final String NO_PROPS_KEY = "np";

    // matadata
    public static final String ETAG_DOC_POLICY_METADATA_KEY = "etagDocPolicy";
    public static final String ETAG_POLICY_METADATA_KEY = "etagPolicy";

    // special resource names
    public static final String SYSTEM = "system.";
    public static final String LOCAL = "local";
    public static final String ADMIN = "admin";

    public static final String FS_CHUNKS_SUFFIX = ".chunks";
    public static final String FS_FILES_SUFFIX = ".files";

    public static final String RESOURCES_WILDCARD_KEY = "*";

    public static final String _INDEXES = "_indexes";
    public static final String _SCHEMAS = "_schemas";
    public static final String _AGGREGATIONS = "_aggrs";

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

    private final String whereUri;
    private final String whatUri;

    private final TYPE type;
    private final METHOD method;
    private final String[] pathTokens;

    private BsonDocument dbProps;
    private BsonDocument collectionProps;

    private BsonValue content;

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
    private DOC_ID_TYPE docIdType = DOC_ID_TYPE.STRING_OID;
    private BsonValue documentId;

    private String mappedUri = null;
    private String unmappedUri = null;

    private static final String NUL = Character.toString('\0');

    private final String etag;

    private boolean forceEtagCheck = false;

    private OperationResult dbOperationResult;

    private BsonDocument shardKey = null;

    private boolean noProps = false;

    /**
     * the HAL mode
     */
    private HAL_MODE halMode = HAL_MODE.FULL;

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
     * whatUri = /mydb/mycollection whereUri = /
     *
     * then the requestPath / is rewritten to /mydb/mycollection
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
    }

    protected static METHOD selectRequestMethod(HttpString _method) {
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

    protected static TYPE selectRequestType(String[] pathTokens) {
        TYPE type;
        if (pathTokens.length < 2) {
            type = TYPE.ROOT;
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
        } else if (pathTokens.length < 4) {
            type = TYPE.COLLECTION;
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
        } else {
            type = TYPE.DOCUMENT;
        }

        return type;
    }

    /**
     * given a mapped uri (/some/mapping/coll) returns the canonical uri
     * (/db/coll) URLs are mapped to mongodb resources by using the mongo-mounts
     * configuration properties
     *
     * @param mappedUri
     * @return
     */
    public final String unmapUri(String mappedUri) {
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

    /**
     * given a canonical uri (/db/coll) returns the mapped uri
     * (/some/mapping/uri) relative to this context. URLs are mapped to mongodb
     * resources via the mongo-mounts configuration properties
     *
     * @param unmappedUri
     * @return
     */
    public final String mapUri(String unmappedUri) {
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
        }

        return ret;
    }

    /**
     * check if the parent of the requested resource is accessible in this
     * request context
     *
     * for instance if /mydb/mycollection is mapped to /coll then:
     *
     * the db is accessible from the collection the root is not accessible from
     * the collection (since / is actually mapped to the db)
     *
     * @return true if parent of the requested resource is accessible
     */
    public final boolean isParentAccessible() {
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
     * @return collection name
     */
    public String getAggregationOperation() {
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
     * @return method
     */
    public METHOD getMethod() {
        return method;
    }

    /**
     *
     * @param dbName
     * @return true if the dbName is a reserved resource
     */
    public static boolean isReservedResourceDb(String dbName) {
        return dbName.equals(ADMIN)
                || dbName.equals(LOCAL)
                || dbName.startsWith(SYSTEM)
                || dbName.startsWith(UNDERSCORE)
                || dbName.equals(RESOURCES_WILDCARD_KEY);
    }

    /**
     *
     * @param collectionName
     * @return true if the collectionName is a reserved resource
     */
    public static boolean isReservedResourceCollection(String collectionName) {
        return collectionName != null
                && !collectionName.equalsIgnoreCase(_SCHEMAS)
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
                && !documentIdRaw.equalsIgnoreCase(_INDEXES)
                && !documentIdRaw.equalsIgnoreCase(MIN_KEY_ID)
                && !documentIdRaw.equalsIgnoreCase(MAX_KEY_ID)
                && !documentIdRaw.equalsIgnoreCase(NULL_KEY_ID)
                && !documentIdRaw.equalsIgnoreCase(TRUE_KEY_ID)
                && !documentIdRaw.equalsIgnoreCase(FALSE_KEY_ID)
                && !(type == TYPE.AGGREGATION)
                || (documentIdRaw.equals(RESOURCES_WILDCARD_KEY)
                && !(type == TYPE.BULK_DOCUMENTS));
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
     * @return the count
     */
    public boolean isCount() {
        return count;
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
                return null;
            }
        }

        return filterQuery;
    }

    public BsonDocument getSortByDocument() throws JsonParseException {
        BsonDocument sort = new BsonDocument();

        if (sortBy == null || sortBy.isEmpty()) {
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
     * @return the warnings
     */
    public List<String> getWarnings() {
        return warnings;
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
        return documentId;
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
}
