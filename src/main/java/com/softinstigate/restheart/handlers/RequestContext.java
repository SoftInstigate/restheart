/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */

package com.softinstigate.restheart.handlers;

import com.mongodb.DBObject;
import com.softinstigate.restheart.utils.URLUtilis;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;

/**
 *
 * @author uji
 */
public class RequestContext
{
    public enum TYPE { ERROR, ROOT, DB, COLLECTION, DOCUMENT, COLLECTION_INDEXES, INDEX };
    public enum METHOD { GET, POST, PUT, DELETE, PATCH, OTHER };
    
    private final String whereUri;
    private final String whatUri;
    
    private final TYPE type;
    private final METHOD method;
    private final String[] pathTokens;
    
    private DBObject dbProps;
    private DBObject collectionProps;
    
    private DBObject content;
    
    private final ArrayList<String> warnings = new ArrayList<>();
    
    private int page = 1;
    private int pagesize = 100;
    private boolean count = false;
    private Deque<String> filter = null;
    private Deque<String> sortBy = null;
    
    private String requestUri = null;
    /**
     * 
     * @param exchange
     * @param whereUri the uri to map to
     * @param whatUri  the uri to map
     */
    public RequestContext(HttpServerExchange exchange, String whereUri, String whatUri)
    {
        this.whereUri = URLUtilis.removeTrailingSlashes(whereUri);
        this.whatUri = whatUri;

        requestUri = unmapUri(exchange.getRequestPath());
        
        pathTokens = requestUri.split("/"); // "/db/collection/document" --> { "", "mappedDbName", "collection", "document" }
        
        if (pathTokens.length < 2)
        {
            type = TYPE.ROOT;
        } else if (pathTokens.length < 3)
        {
            type = TYPE.DB;
        } else if (pathTokens.length < 4)
        {
            type = TYPE.COLLECTION;
        } else if (pathTokens.length == 4 && pathTokens[3].equals("_indexes"))
        {
            type = TYPE.COLLECTION_INDEXES;
        }
        else if (pathTokens.length > 4 && pathTokens[3].equals("_indexes"))
        {
            type = TYPE.INDEX;
        }
        else
        {
            type = TYPE.DOCUMENT;
        }
        
        HttpString _method = exchange.getRequestMethod();
        
        if (Methods.GET.equals(_method))
            this.method = METHOD.GET;
        else if (Methods.POST.equals(_method))
            this.method = METHOD.POST;
        else if (Methods.PUT.equals(_method))
            this.method = METHOD.PUT;
        else if (Methods.DELETE.equals(_method))
            this.method = METHOD.DELETE;
        else if ("PATCH".equals(_method.toString()))
            this.method = METHOD.PATCH;
        else
            this.method = METHOD.OTHER;
    }

    /**
     * given a mapped uri (/some/mapping/coll) returns the canonical uri (/db/coll)
     * 
     * @param mappedUri
     * @return 
     */
    public final String unmapUri(String mappedUri)
    {
        String ret = URLUtilis.removeTrailingSlashes(mappedUri);
        
        if (whatUri.equals("*"))
        {
            if (!this.whereUri.equals("/"))
                ret = ret.replaceFirst("^" + this.whereUri, "");
        }
        else
        {
            ret = URLUtilis.removeTrailingSlashes(ret.replaceFirst("^" + this.whereUri, this.whatUri));
        }
        
        if (ret.isEmpty())
            ret = "/";
        
        return ret;
    }
    
    /**
     * given a canonical uri (/db/coll) returns the mapped uri (/db/coll) relative to this context
     * 
     * @param unmappedUri
     * @return 
     */
    public final String mapUri(String unmappedUri)
    {
        String ret = URLUtilis.removeTrailingSlashes(unmappedUri);
        
        if (whatUri.equals("*"))
        {
            if (!this.whereUri.equals("/"))
                return this.whereUri + "/" + unmappedUri;
        }
        else
        {
            ret = URLUtilis.removeTrailingSlashes(ret.replaceFirst("^" + this.whatUri, this.whereUri));
        }
        
        if (ret.isEmpty())
            ret = "/";
        
        return ret;
    }
    
    public TYPE getType()
    {
        return type;
    }
    
    public String getDBName()
    {
        if (pathTokens.length > 1)
            return pathTokens[1];
        else
            return null;
    }
    
    public String getCollectionName()
    {
        if (pathTokens.length > 2)
            return pathTokens[2];
        else
            return null;
    }
    
    public String getDocumentId()
    {
        if (pathTokens.length > 3)
            return pathTokens[3];
        else
            return null;
    }
    
    public String getIndexId()
    {
        if (pathTokens.length > 4)
            return pathTokens[4];
        else
            return null;
    }
    
    public URI getUri() throws URISyntaxException
    {
        return new URI(Arrays.asList(pathTokens).stream().reduce("/", (t1,t2) -> t1 + "/" + t2));
    }
    
    public METHOD getMethod()
    {
        return method;
    }
    
    public static boolean isReservedResourceDb(String dbName)
    {
        return dbName.equals("admin") || dbName.equals("local") ||dbName.startsWith("system.") || dbName.startsWith("_");
    }
    
    public static boolean isReservedResourceCollection(String collectionName)
    {
        return collectionName!= null && (collectionName.startsWith("system.") || collectionName.startsWith("_") );
    }
    
    public static boolean isReservedResourceDocument(String documentId)
    {
        return documentId != null && (documentId.startsWith("_") && !documentId.equals("_indexes"));
    }
    
    public boolean isReservedResource()
    {
        if (type == TYPE.ROOT)
            return false;
        
        return isReservedResourceDb(getDBName()) || isReservedResourceCollection(getCollectionName()) || isReservedResourceDocument(getDocumentId());
    }

    /**
     * @return the whereUri
     */
    public String getUriPrefix()
    {
        return whereUri;
    }

    /**
     * @return the whatUri
     */
    public String getMappingUri()
    {
        return whatUri;
    }

    /**
     * @return the page
     */
    public int getPage()
    {
        return page;
    }

    /**
     * @param page the page to set
     */
    public void setPage(int page)
    {
        this.page = page;
    }

    /**
     * @return the pagesize
     */
    public int getPagesize()
    {
        return pagesize;
    }

    /**
     * @param pagesize the pagesize to set
     */
    public void setPagesize(int pagesize)
    {
        this.pagesize = pagesize;
    }

    /**
     * @return the count
     */
    public boolean isCount()
    {
        return count;
    }

    /**
     * @param count the count to set
     */
    public void setCount(boolean count)
    {
        this.count = count;
    }

    /**
     * @return the filter
     */
    public Deque<String> getFilter()
    {
        return filter;
    }

    /**
     * @param filter the filter to set
     */
    public void setFilter(Deque<String> filter)
    {
        this.filter = filter;
    }

    /**
     * @return the sortBy
     */
    public Deque<String> getSortBy()
    {
        return sortBy;
    }

    /**
     * @param sortBy the sortBy to set
     */
    public void setSortBy(Deque<String> sortBy)
    {
        this.sortBy = sortBy;
    }
    
    /**
     * @return the collectionProps
     */
    public DBObject getCollectionProps()
    {
        return collectionProps;
    }

    /**
     * @param collectionProps the collectionProps to set
     */
    public void setCollectionProps(DBObject collectionProps)
    {
        this.collectionProps = collectionProps;
    }

    /**
     * @return the dbProps
     */
    public DBObject getDbProps()
    {
        return dbProps;
    }

    /**
     * @param dbProps the dbProps to set
     */
    public void setDbProps(DBObject dbProps)
    {
        this.dbProps = dbProps;
    }

    /**
     * @return the content
     */
    public DBObject getContent()
    {
        return content;
    }

    /**
     * @param content the content to set
     */
    public void setContent(DBObject content)
    {
        this.content = content;
    }

    /**
     * @return the warnings
     */
    public ArrayList<String> getWarnings()
    {
        return warnings;
    }
    
    /**
     * @param warning
     */
    public void addWarning(String warning)
    {
        warnings.add(warning);
    }

    /**
     * @return the requestUri
     */
    public String getRequestUri()
    {
        return requestUri;
    }
}