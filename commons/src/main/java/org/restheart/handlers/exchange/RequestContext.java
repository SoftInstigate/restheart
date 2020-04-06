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
package org.restheart.handlers.exchange;

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonMode;
import org.bson.json.JsonParseException;
import org.restheart.handlers.exchange.AbstractExchange.METHOD;
import org.restheart.handlers.exchange.ExchangeKeys.DOC_ID_TYPE;
import org.restheart.handlers.exchange.ExchangeKeys.EAGER_CURSOR_ALLOCATION_POLICY;
import org.restheart.handlers.exchange.ExchangeKeys.HAL_MODE;
import org.restheart.handlers.exchange.ExchangeKeys.REPRESENTATION_FORMAT;
import org.restheart.handlers.exchange.ExchangeKeys.TYPE;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@Deprecated
public class RequestContext {
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
        this.bsonRequest = BsonRequest.init(exchange, whereUri, whatUri);
        this.bsonResponse = BsonResponse.wrap(exchange);
    }
    
    private RequestContext(
            BsonRequest bsonRequest,
            BsonResponse bsonResponse) {
        this.bsonRequest = bsonRequest;
        this.bsonResponse = bsonResponse;
    }
    
    public static RequestContext wrap(HttpServerExchange exchange) {
        return new RequestContext(BsonRequest.wrap(exchange),
                BsonResponse.wrap(exchange));
    }
    
    /**
     *
     * @param dbName
     * @return true if the dbName is a reserved resource
     */
    public static boolean isReservedResourceDb(String dbName) {
        return BsonRequest.isReservedResourceDb(dbName);
    }

    /**
     *
     * @param collectionName
     * @return true if the collectionName is a reserved resource
     */
    public static boolean isReservedResourceCollection(String collectionName) {
        return BsonRequest.isReservedResourceCollection(collectionName);
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
        return BsonRequest.isReservedResourceDocument(type, documentIdRaw);
    }

    private final BsonRequest bsonRequest;
    private final BsonResponse bsonResponse;

    /**
     * given a canonical uri (/db/coll) returns the mapped uri
     * (/some/mapping/uri) relative to this context. URLs are mapped to mongodb
     * resources via the mongo-mounts configuration properties
     *
     * @param unmappedUri
     * @return
     */
    public String mapUri(String unmappedUri) {
        return this.bsonRequest.mapUri(unmappedUri);
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
        return this.bsonRequest.isParentAccessible();
    }

    /**
     *
     * @return type
     */
    public TYPE getType() {
        return this.bsonRequest.getType();
    }

    /**
     *
     * @return DB Name
     */
    public String getDBName() {
        return this.bsonRequest.getDBName();
    }

    /**
     *
     * @return collection name
     */
    public String getCollectionName() {
        return this.bsonRequest.getCollectionName();
    }

    /**
     *
     * @return document id
     */
    public String getDocumentIdRaw() {
        return this.bsonRequest.getDocumentIdRaw();
    }

    /**
     *
     * @return index id
     */
    public String getIndexId() {
        return this.bsonRequest.getIndexId();
    }

    /**
     *
     * @return the txn id or null if request type is not SESSIONS, TRANSACTIONS
     * or TRANSACTION
     */
    public String getSid() {
        return this.bsonRequest.getSid();
    }

    /**
     *
     * @return the txn id or null if request type is not TRANSACTION
     */
    public long getTxnId() {
        return this.bsonRequest.getTxnId();
    }

    /**
     *
     * @return collection name
     */
    public String getAggregationOperation() {
        return this.bsonRequest.getAggregationOperation();
    }

    /**
     * @return change stream operation name
     */
    public String getChangeStreamOperation() {
        return this.bsonRequest.getChangeStreamOperation();
    }

    /**
     *
     * @return
     */
    public String getChangeStreamIdentifier() {
        return this.bsonRequest.getChangeStreamIdentifier();
    }

    /**
     *
     * @return URI
     * @throws URISyntaxException
     */
    public URI getUri() throws URISyntaxException {
        return this.bsonRequest.getUri();
    }

    /**
     *
     * @return method
     */
    public METHOD getMethod() {
        return this.bsonRequest.getMethod();
    }

    /**
     *
     * @return isReservedResource
     */
    public boolean isReservedResource() {
        return this.bsonRequest.isReservedResource();
    }

    /**
     * @return the whereUri
     */
    public String getUriPrefix() {
        return this.bsonRequest.getUriPrefix();
    }

    /**
     * @return the whatUri
     */
    public String getMappingUri() {
        return this.bsonRequest.getMappingUri();
    }

    /**
     * @return the page
     */
    public int getPage() {
        return this.bsonRequest.getPage();
    }

    /**
     * @param page the page to set
     */
    public void setPage(int page) {
        this.bsonRequest.setPage(page);
    }

    /**
     * @return the pagesize
     */
    public int getPagesize() {
        return this.bsonRequest.getPagesize();
    }

    /**
     * @param pagesize the pagesize to set
     */
    public void setPagesize(int pagesize) {
        this.bsonRequest.setPagesize(pagesize);
    }

    /**
     * @return the representationFormat
     */
    public REPRESENTATION_FORMAT getRepresentationFormat() {
        return this.bsonRequest.getRepresentationFormat();
    }

    /**
     * sets representationFormat
     *
     * @param representationFormat
     */
    public void setRepresentationFormat(
            REPRESENTATION_FORMAT representationFormat) {
        this.bsonRequest.setRepresentationFormat(representationFormat);
    }

    /**
     * @return the count
     */
    public boolean isCount() {
        return this.bsonRequest.isCount();
    }

    /**
     * @param count the count to set
     */
    public void setCount(boolean count) {
        this.bsonRequest.setCount(count);
    }

    /**
     * @return the filter
     */
    public Deque<String> getFilter() {
        return this.bsonRequest.getFilter();
    }

    /**
     * @param filter the filter to set
     */
    public void setFilter(Deque<String> filter) {
        this.bsonRequest.setFilter(filter);
    }

    /**
     * @return the hint
     */
    public Deque<String> getHint() {
        return this.bsonRequest.getHint();
    }

    /**
     * @param hint the hint to set
     */
    public void setHint(Deque<String> hint) {
        this.bsonRequest.setHint(hint);
    }

    /**
     *
     * @return the $and composed filter qparam values
     */
    public BsonDocument getFiltersDocument() throws JsonParseException {
        return this.bsonRequest.getFiltersDocument();
    }

    /**
     *
     * @return @throws JsonParseException
     */
    public BsonDocument getSortByDocument() throws JsonParseException {
        return this.bsonRequest.getSortByDocument();
    }

    /**
     *
     * @return @throws JsonParseException
     */
    public BsonDocument getHintDocument() throws JsonParseException {
        return this.bsonRequest.getHintDocument();
    }

    /**
     *
     * @return @throws JsonParseException
     */
    public BsonDocument getProjectionDocument() throws JsonParseException {
        return this.bsonRequest.getProjectionDocument();
    }

    /**
     * @return the aggregationVars
     */
    public BsonDocument getAggreationVars() {
        return this.bsonRequest.getAggreationVars();
    }

    /**
     * @param aggregationVars the aggregationVars to set
     */
    public void setAggregationVars(BsonDocument aggregationVars) {
        this.bsonRequest.setAggregationVars(aggregationVars);
    }

    /**
     * @return the sortBy
     */
    public Deque<String> getSortBy() {
        return this.bsonRequest.getSortBy();
    }

    /**
     * @param sortBy the sortBy to set
     */
    public void setSortBy(Deque<String> sortBy) {
        this.bsonRequest.setSortBy(sortBy);
    }

    /**
     * @return the collectionProps
     */
    public BsonDocument getCollectionProps() {
        return this.bsonRequest.getCollectionProps();
    }

    /**
     * @param collectionProps the collectionProps to set
     */
    public void setCollectionProps(BsonDocument collectionProps) {
        this.bsonRequest.setCollectionProps(collectionProps);
    }

    /**
     * @return the dbProps
     */
    public BsonDocument getDbProps() {
        return this.bsonRequest.getDbProps();
    }

    /**
     * @param dbProps the dbProps to set
     */
    public void setDbProps(BsonDocument dbProps) {
        this.bsonRequest.setDbProps(dbProps);
    }

    /**
     * @return the content
     */
    public BsonValue getContent() {
        return this.bsonRequest.getContent();
    }

    /**
     * @param content the content to set
     */
    public void setContent(BsonValue content) {
        this.bsonRequest.setContent(content);
    }

    /**
     * @return the rawContent
     */
    public String getRawContent() {
        throw new UnsupportedOperationException("Not supported from v5. "
                + "Use ByteArrayRequest instead.");
    }

    /**
     * @param rawContent the rawContent to set
     */
    public void setRawContent(String rawContent) {
        throw new UnsupportedOperationException("Not supported from v5. "
                + "Use ByteArrayRequest instead.");
    }

    /**
     * @return the warnings
     */
    public List<String> getWarnings() {
        return this.bsonResponse.getWarnings();
    }

    /**
     * @param warning
     */
    public void addWarning(String warning) {
        this.bsonResponse.addWarning(warning);
    }

    /**
     *
     * The unmapped uri is the cononical uri of a mongodb resource (e.g.
     * /db/coll).
     *
     * @return the unmappedUri
     */
    public String getUnmappedRequestUri() {
        return this.bsonRequest.getUnmappedRequestUri();
    }

    /**
     * The mapped uri is the exchange request uri. This is "mapped" by the
     * mongo-mounts mapping paramenters.
     *
     * @return the mappedUri
     */
    public String getMappedRequestUri() {
        return this.bsonRequest.getMappedRequestUri();
    }

    /**
     * if mongo-mounts specifies a path template (i.e. /{foo}/*) this returns
     * the request template parameters (/x/y => foo=x, *=y)
     *
     * @return
     */
    public Map<String, String> getPathTemplateParamenters() {
        return this.bsonRequest.getPathTemplateParamenters();
    }

    /**
     *
     * @return the cursorAllocationPolicy
     */
    public EAGER_CURSOR_ALLOCATION_POLICY getCursorAllocationPolicy() {
        return this.bsonRequest.getCursorAllocationPolicy();
    }

    /**
     * @param cursorAllocationPolicy the cursorAllocationPolicy to set
     */
    public void setCursorAllocationPolicy(
            EAGER_CURSOR_ALLOCATION_POLICY cursorAllocationPolicy) {
        this.bsonRequest.setCursorAllocationPolicy(cursorAllocationPolicy);
    }

    /**
     * @return the docIdType
     */
    public DOC_ID_TYPE getDocIdType() {
        return this.bsonRequest.getDocIdType();
    }

    /**
     * @param docIdType the docIdType to set
     */
    public void setDocIdType(DOC_ID_TYPE docIdType) {
        this.bsonRequest.setDocIdType(docIdType);
    }

    /**
     * @param documentId the documentId to set
     */
    public void setDocumentId(BsonValue documentId) {
        this.bsonRequest.setDocumentId(documentId);
    }

    /**
     * @return the documentId
     */
    public BsonValue getDocumentId() {
        return this.bsonRequest.getDocumentId();
    }

    /**
     * @return the responseContent
     */
    public BsonValue getResponseContent() {
        return this.bsonResponse.getContent();
    }

    /**
     * @param responseContent the responseContent to set
     */
    public void setResponseContent(BsonValue responseContent) {
        this.bsonResponse.setContent(responseContent);
    }

    /**
     * @return the responseStatusCode
     */
    public int getResponseStatusCode() {
        return this.bsonResponse.getStatusCode();
    }

    /**
     * @param responseStatusCode the responseStatusCode to set
     */
    public void setResponseStatusCode(int responseStatusCode) {
        this.bsonResponse.setStatusCode(responseStatusCode);
    }

    /**
     * @return the responseContentType
     */
    public String getResponseContentType() {
        return this.bsonResponse.getContentType();
    }

    /**
     * @param responseContentType the responseContentType to set
     */
    public void setResponseContentType(String responseContentType) {
        this.bsonResponse.setContentType(responseContentType);
    }

    /**
     * @return the filePath
     */
    public Path getFilePath() {
        return this.bsonRequest.getFilePath();
    }

    /**
     * @param filePath the filePath to set
     */
    public void setFilePath(Path filePath) {
        this.bsonRequest.setFilePath(filePath);
    }

    /**
     * @return keys
     */
    public Deque<String> getKeys() {
        return this.bsonRequest.getKeys();
    }

    /**
     * @param keys keys to set
     */
    public void setKeys(Deque<String> keys) {
        this.bsonRequest.setKeys(keys);
    }

    /**
     * @return the halMode
     */
    public HAL_MODE getHalMode() {
        return this.bsonRequest.getHalMode();
    }

    /**
     *
     * @return
     */
    public boolean isFullHalMode() {
        return this.bsonRequest.isFullHalMode();
    }

    /**
     * @param halMode the halMode to set
     */
    public void setHalMode(HAL_MODE halMode) {
        this.bsonRequest.setHalMode(halMode);
    }

    /**
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isDbNameInvalid() {
        return this.bsonRequest.isDbNameInvalid();
    }

    /**
     *
     * @return
     */
    public long getRequestStartTime() {
        return this.bsonRequest.getRequestStartTime();
    }

    /**
     * @param dbName
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isDbNameInvalid(String dbName) {
        return this.bsonRequest.isDbNameInvalid(dbName);
    }

    /**
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isDbNameInvalidOnWindows() {
        return this.bsonRequest.isDbNameInvalidOnWindows();
    }

    /**
     * @param dbName
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isDbNameInvalidOnWindows(String dbName) {
        return this.bsonRequest.isDbNameInvalidOnWindows(dbName);
    }

    /**
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isCollectionNameInvalid() {
        return this.bsonRequest.isCollectionNameInvalid();
    }

    /**
     * @param collectionName
     * @see https://docs.mongodb.org/v3.2/reference/limits/#naming-restrictions
     * @return
     */
    public boolean isCollectionNameInvalid(String collectionName) {
        return this.bsonRequest.isCollectionNameInvalid(collectionName);
    }

    /**
     *
     * @return
     */
    public String getETag() {
        return this.bsonRequest.getETag();
    }

    /**
     *
     * @return
     */
    public boolean isETagCheckRequired() {
        return this.bsonRequest.isETagCheckRequired();
    }

    /**
     * @return the dbOperationResult
     */
    public OperationResult getDbOperationResult() {
        return this.bsonResponse.getDbOperationResult();
    }

    /**
     * @param dbOperationResult the dbOperationResult to set
     */
    public void setDbOperationResult(OperationResult dbOperationResult) {
        this.bsonResponse.setDbOperationResult(dbOperationResult);
    }

    /**
     * @return the shardKey
     */
    public BsonDocument getShardKey() {
        return this.bsonRequest.getShardKey();
    }

    /**
     * @param shardKey the shardKey to set
     */
    public void setShardKey(BsonDocument shardKey) {
        this.bsonRequest.setShardKey(shardKey);
    }

    /**
     * @return the noProps
     */
    public boolean isNoProps() {
        return this.bsonRequest.isNoProps();
    }

    /**
     * @param noProps the noProps to set
     */
    public void setNoProps(boolean noProps) {
        this.bsonRequest.setNoProps(noProps);
    }

    /**
     * @return true if request is errored
     */
    public boolean isInError() {
        return this.bsonRequest.isInError();
    }

    /**
     * sets the inError
     * @param inError
     */
    public void setInError(boolean inError) {
        this.bsonRequest.setInError(inError);
    }

    /**
     * @return the authenticatedAccount
     */
    public Account getAuthenticatedAccount() {
        return this.bsonRequest.getAuthenticatedAccount();
    }

    /**
     * @param authenticatedAccount the authenticatedAccount to set
     */
    public void setAuthenticatedAccount(Account authenticatedAccount) {
        this.bsonRequest.setAuthenticatedAccount(authenticatedAccount);
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.AGGREGATION
     */
    public boolean isAggregation() {
        return this.bsonRequest.isAggregation();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.BULK_DOCUMENTS
     */
    public boolean isBulkDocuments() {
        return this.bsonRequest.isBulkDocuments();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION
     */
    public boolean isCollection() {
        return this.bsonRequest.isCollection();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION_INDEXES
     */
    public boolean isCollectionIndexes() {
        return this.bsonRequest.isCollectionIndexes();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DB
     */
    public boolean isDb() {
        return this.bsonRequest.isDb();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DOCUMENT
     */
    public boolean isDocument() {
        return this.bsonRequest.isDocument();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILE
     */
    public boolean isFile() {
        return this.bsonRequest.isFile();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILES_BUCKET
     */
    public boolean isFilesBucket() {
        return this.bsonRequest.isFilesBucket();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILE_BINARY
     */
    public boolean isFileBinary() {
        return this.bsonRequest.isFileBinary();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.INDEX
     */
    public boolean isIndex() {
        return this.bsonRequest.isIndex();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.ROOT
     */
    public boolean isRoot() {
        return this.bsonRequest.isRoot();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.TRANSACTIONS
     */
    public boolean isSessions() {
        return this.bsonRequest.isSessions();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.TRANSACTIONS
     */
    public boolean isTxns() {
        return this.bsonRequest.isTxns();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.TRANSACTION
     */
    public boolean isTxn() {
        return this.bsonRequest.isTxn();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA
     */
    public boolean isSchema() {
        return this.bsonRequest.isSchema();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA_STORE
     */
    public boolean isSchemaStore() {
        return this.bsonRequest.isSchemaStore();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.ROOT_SIZE
     */
    public boolean isRootSize() {
        return this.bsonRequest.isRootSize();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DB_SIZE
     */
    public boolean isDbSize() {
        return this.bsonRequest.isDbSize();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.DB_META
     */
    public boolean isDbMeta() {
        return this.bsonRequest.isDbMeta();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION_SIZE
     */
    public boolean isCollectionSize() {
        return this.bsonRequest.isCollectionSize();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.COLLECTION_META
     */
    public boolean isCollectionMeta() {
        return this.bsonRequest.isCollectionMeta();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILES_BUCKET_SIZE
     */
    public boolean isFilesBucketSize() {
        return this.bsonRequest.isFilesBucketSize();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.FILES_BUCKET_META
     */
    public boolean isFilesBucketMeta() {
        return this.bsonRequest.isFilesBucketMeta();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA_STORE_SIZE
     */
    public boolean isSchemaStoreSize() {
        return this.bsonRequest.isSchemaStoreSize();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.SCHEMA_STORE_SIZE
     */
    public boolean isSchemaStoreMeta() {
        return this.bsonRequest.isSchemaStoreMeta();
    }

    /**
     * helper method to check request resource type
     *
     * @return true if type is TYPE.METRICS
     */
    public boolean isMetrics() {
        return this.bsonRequest.isMetrics();
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.DELETE
     */
    public boolean isDelete() {
        return this.bsonRequest.isDelete();
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.GET
     */
    public boolean isGet() {
        return this.bsonRequest.isGet();
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.OPTIONS
     */
    public boolean isOptions() {
        return this.bsonRequest.isOptions();
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PATCH
     */
    public boolean isPatch() {
        return this.bsonRequest.isPatch();
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.POST
     */
    public boolean isPost() {
        return this.bsonRequest.isPost();
    }

    /**
     * helper method to check request method
     *
     * @return true if method is METHOD.PUT
     */
    public boolean isPut() {
        return this.bsonRequest.isPut();
    }

    /**
     * @return the clientSession
     */
    public ClientSessionImpl getClientSession() {
        return this.bsonRequest.getClientSession();
    }

    /**
     * @param clientSession the clientSession to set
     */
    public void setClientSession(ClientSessionImpl clientSession) {
        this.bsonRequest.setClientSession(clientSession);
    }

    /**
     * @return the jsonMode as specified by jsonMode query paramter
     */
    public JsonMode getJsonMode() {
        return this.bsonRequest.getJsonMode();
    }
}
