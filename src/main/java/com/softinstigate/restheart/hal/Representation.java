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
package com.softinstigate.restheart.hal;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.BSONObject;

/**
 *
 * @author uji
 */
public class Representation
{
    public static final String HAL_JSON_MEDIA_TYPE = "application/hal+json";
    
    private final BasicDBObject properties;
    private final BasicDBObject embedded;
    private final BasicDBObject links;
    
    public Representation(String href)
    {
        properties = new BasicDBObject();
        embedded = new BasicDBObject();
        links = new BasicDBObject();
        
        Link self = new Link("self", href);
        
        links.put("_links", self.getDBObject());
    }
        
    BasicDBObject getDBObject()
    {
        BasicDBObject ret = new BasicDBObject(properties);
        
        if (!embedded.isEmpty())
            ret.append("_embedded", embedded);
        
        if (!links.isEmpty())
            ret.append("_links", links); 
        
        return ret;
    }
    
    public void addLink(Link link)
    {
        links.putAll((BSONObject)((Link)link).getDBObject());
    }
    
    public void addLink(Link link, boolean inArray)
    {
        BasicDBList linkArray = (BasicDBList) links.get(link.getRef());
        
        if (linkArray == null)
        {
            linkArray = new BasicDBList();
            links.append(link.getRef(), linkArray);
        }
        
        linkArray.add(link.getDBObject().get(link.getRef()));
        
        links.put(link.getRef(), linkArray);
    }
    
    public void addProperty(String key, Object value)
    {
        properties.append(key, value);
    }
    
    public void addProperties(DBObject props)
    {
        if (props == null)
            return;
        
        properties.putAll(props);
    }
    
    public void addRepresentation(String rel, Representation rep)
    {
        BasicDBList repArray = (BasicDBList) embedded.get(rel);
        
        if (repArray == null)
        {
            repArray = new BasicDBList();
            
            embedded.append(rel, repArray);
        }
        
        repArray.add(rep.getDBObject());
    }
    
    @Override
    public String toString()
    {
        return getDBObject().toString();
    }
}
