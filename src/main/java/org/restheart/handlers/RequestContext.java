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
package org.restheart.handlers;

import com.mongodb.DBObject;
import org.restheart.db.DBCursorPool.EAGER_CURSOR_ALLOCATION_POLICY;
import org.restheart.utils.URLUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RequestContext {

    public enum TYPE {
        ERROR,
        ROOT,
        DB,
        COLLECTION,
        DOCUMENT,
        COLLECTION_INDEXES,
        INDEX,
        FILES_BUCKET,
        FILE,
        FILE_BINARY
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
        MINKEY, //org.bson.types.MinKey;
        MAXKEY // org.bson.types.MaxKey
    }

    public static final String PAGE_QPARAM_KEY = "page";
    public static final String PAGESIZE_QPARAM_KEY = "pagesize";
    public static final String COUNT_QPARAM_KEY = "count";
    public static final String SORT_BY_QPARAM_KEY = "sort_by";
    public static final String FILTER_QPARAM_KEY = "filter";
    public static final String EAGER_CURSOR_ALLOCATION_POLICY_QPARAM_KEY = "eager";
    public static final String DOC_ID_TYPE_KEY = "id_type";
    public static final String SLASH = "/";
    public static final String PATCH = "PATCH";
    public static final String UNDERSCORE = "_";
    public static final String SYSTEM = "system.";
    public static final String LOCAL = "local";
    public static final String ADMIN = "admin";
    public static final String FS_CHUNKS_SUFFIX = ".chunks";
    public static final String FS_FILES_SUFFIX = ".files";
    public static final String _INDEXES = "_indexes";
    public static final String BINARY_CONTENT = "binary";

    public static final String MAX_KEY_ID = "_MaxKey";
    public static final String MIN_KEY_ID = "_MinKey";

    private final String whereUri;
    private final String whatUri;

    private final TYPE type;
    private final METHOD method;
    private final String[] pathTokens;

    private DBObject dbProps;
    private DBObject collectionProps;

    private DBObject content;
    
    private File file;

    private DBObject responseContent;

    private final List<String> warnings = new ArrayList<>();

    private int page = 1;
    private int pagesize = 100;
    private boolean count = false;
    private EAGER_CURSOR_ALLOCATION_POLICY cursorAllocationPolicy;
    private Deque<String> filter = null;
    private Deque<String> sortBy = null;
    private DOC_ID_TYPE docIdType = DOC_ID_TYPE.STRING_OID;
    private Object documentId;

    private String unmappedRequestUri = null;
    private String mappedRequestUri = null;

    /**
     *
     * @param exchange the url rewriting feature is implemented by the whatUri
     * and whereUri parameters
     *
     * the exchange request path is rewritten replacing the whereUri string with
     * the whatUri string the special whatUri value * means any resource: the
     * whereUri is replaced with /
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
    public RequestContext(HttpServerExchange exchange, String whereUri, String whatUri) {
        this.whereUri = URLUtils.removeTrailingSlashes(whereUri);
        this.whatUri = whatUri;

        this.unmappedRequestUri = exchange.getRequestPath();
        this.mappedRequestUri = unmapUri(exchange.getRequestPath());

        // "/db/collection/document" --> { "", "mappedDbName", "collection", "document" }
        this.pathTokens = this.mappedRequestUri.split(SLASH);
        this.type = selectRequestType(pathTokens);

        this.method = selectRequestMethod(exchange.getRequestMethod());
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
        } else if (pathTokens.length >= 3 && pathTokens[2].endsWith(FS_FILES_SUFFIX)) {
            if (pathTokens.length == 3) {
                type = TYPE.FILES_BUCKET;
            } else if (pathTokens.length == 4 && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
                type = TYPE.COLLECTION_INDEXES;
            } else if (pathTokens.length == 4 && !pathTokens[3].equalsIgnoreCase(_INDEXES)) {
                type = TYPE.FILE;
            } else if (pathTokens.length > 4 && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
                type = TYPE.INDEX;
            } else if (pathTokens.length > 4 && !pathTokens[3].equalsIgnoreCase(_INDEXES) && !pathTokens[4].equalsIgnoreCase(BINARY_CONTENT)) {
                type = TYPE.FILE;
            } else if (pathTokens.length == 5 && pathTokens[4].equalsIgnoreCase(BINARY_CONTENT)) {
                // URL: <host>/db/bucket.file/xxx/binary
                type = TYPE.FILE_BINARY;
            } else {
                type = TYPE.DOCUMENT;
            }
        } else if (pathTokens.length < 4) {
            type = TYPE.COLLECTION;
        } else if (pathTokens.length == 4 && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
            type = TYPE.COLLECTION_INDEXES;
        } else if (pathTokens.length > 4 && pathTokens[3].equalsIgnoreCase(_INDEXES)) {
            type = TYPE.INDEX;
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
        } else {
            ret = URLUtils.removeTrailingSlashes(ret.replaceFirst("^" + this.whereUri, this.whatUri));
        }

        if (ret.isEmpty()) {
            ret = SLASH;
        }

        return ret;
    }

    /**
     * given a canonical uri (/db/coll) returns the mapped uri (/db/coll)
     * relative to this context URLs are mapped to mongodb resources by using
     * the mongo-mounts configuration properties
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
            ret = URLUtils.removeTrailingSlashes(ret.replaceFirst("^" + this.whatUri, this.whereUri));
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
                ? unmappedRequestUri.split(SLASH).length > 1
                : unmappedRequestUri.split(SLASH).length > 2;
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
     * @return URI
     * @throws URISyntaxException
     */
    public URI getUri() throws URISyntaxException {
        return new URI(Arrays.asList(pathTokens).stream().reduce(SLASH, (t1, t2) -> t1 + SLASH + t2));
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
     * @return isReservedResourceDb
     */
    public static boolean isReservedResourceDb(String dbName) {
        return dbName.equals(ADMIN)
                || dbName.equals(LOCAL)
                || dbName.startsWith(SYSTEM)
                || dbName.startsWith(UNDERSCORE);
    }

    /**
     *
     * @param collectionName
     * @return isReservedResourceCollection
     */
    public static boolean isReservedResourceCollection(String collectionName) {
        return collectionName != null && (collectionName.startsWith(SYSTEM)
                || collectionName.startsWith(UNDERSCORE)
                || collectionName.endsWith(FS_CHUNKS_SUFFIX));
    }

    /**
     *
     * @param documentIdRaw
     * @return isReservedResourceDocument
     */
    public static boolean isReservedResourceDocument(String documentIdRaw) {
        if (documentIdRaw == null) {
            return false;
        }

        return documentIdRaw.startsWith(UNDERSCORE)
                && !documentIdRaw.equalsIgnoreCase(_INDEXES)
                && !documentIdRaw.equalsIgnoreCase(MIN_KEY_ID)
                && !documentIdRaw.equalsIgnoreCase(MAX_KEY_ID);
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
                || isReservedResourceDocument(getDocumentIdRaw());
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
    public DBObject getCollectionProps() {
        return collectionProps;
    }

    /**
     * @param collectionProps the collectionProps to set
     */
    public void setCollectionProps(DBObject collectionProps) {
        this.collectionProps = collectionProps;
    }

    /**
     * @return the dbProps
     */
    public DBObject getDbProps() {
        return dbProps;
    }

    /**
     * @param dbProps the dbProps to set
     */
    public void setDbProps(DBObject dbProps) {
        this.dbProps = dbProps;
    }

    /**
     * @return the content
     */
    public DBObject getContent() {
        return content;
    }

    /**
     * @param content the content to set
     */
    public void setContent(DBObject content) {
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
     * @return the mappedRequestUri
     */
    public String getMappedRequestUri() {
        return mappedRequestUri;
    }

    /**
     * @return the unmappedRequestUri
     */
    public String getUnmappedRequestUri() {
        return unmappedRequestUri;
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
    public void setCursorAllocationPolicy(EAGER_CURSOR_ALLOCATION_POLICY cursorAllocationPolicy) {
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
    public void setDocumentId(Object documentId) {
        this.documentId = documentId;
    }

    /**
     * @return the documentId
     */
    public Object getDocumentId() {
        return documentId;
    }

    /**
     * @return the responseContent
     */
    public DBObject getResponseContent() {
        return responseContent;
    }

    /**
     * @param responseContent the responseContent to set
     */
    public void setResponseContent(DBObject responseContent) {
        this.responseContent = responseContent;
    }

    /**
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * @param file the file to set
     */
    public void setFile(File file) {
        this.file = file;
    }
}
