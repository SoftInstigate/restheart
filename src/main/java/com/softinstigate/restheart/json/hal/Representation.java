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
package com.softinstigate.restheart.json.hal;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 *
 * @author uji
 */
public class Representation
{
    private final JsonObject json;
    
    public Representation(String href)
    {
        json = new JsonObject().add("_links", new JsonObject().add("self", new JsonObject().add("href", href)));
    }
    
    private Representation(JsonObject json)
    {
        this.json = json;
    }
    
    public Representation withLink(String rel, String href)
    {
        JsonObject newJson = new JsonObject(json);
        
        if (newJson.get("_links") == null)
            newJson.add("_links", new JsonObject());
        
        JsonObject links = newJson.get("_links").asObject();
        
        JsonObject _href = new JsonObject();
        
        _href.add("href", href);
        
        links.add(rel, _href);
        
        return new Representation(newJson);
    }
    
    public Representation withProperty(String key, Object value)
    {
        JsonObject newJson = new JsonObject(json);
        
        if (value == null)
        {
            newJson.add(key, JsonValue.NULL);
        }
        else
        {
            newJson.add(key, JsonValue.valueOf(value.toString()));
        }
        
        return new Representation(newJson);
    }
    
    public Representation withRepresentation(String rel, Representation resource)
    {
        JsonObject newJson = new JsonObject(json);
        
        JsonValue _embedded = newJson.get("_embedded");
        
        if (_embedded == null)
        {
            _embedded = new JsonObject();
            newJson.add("_embedded", _embedded);
        }
        
        JsonValue _rels = _embedded.asObject().get(rel);
        
        if (_rels == null)
        {
            _rels = new JsonArray();
            _embedded.asObject().add(rel, _rels);
        }
        
        _rels.asArray().add(resource.json);
        
        return new Representation(newJson);
    }
    
    public JsonValue getResources()
    {
        return new JsonObject(json.get("_embedded").asObject());
    }
    
    public JsonValue getProperties()
    {
        JsonObject newJson = new JsonObject(json);
        newJson.remove("_embedded");
        newJson.remove("_links");
        
        return newJson;
    }
    
    public JsonValue getLinks()
    {
        return new JsonObject(json.get("_links").asObject());
    }
    
    public static Representation parse(String s) throws IOException
    {
        return parse(new StringReader(s));
    }
    
    public static Representation parse(Reader reader) throws IOException
    {
        JsonObject json = JsonObject.readFrom(reader);
        
        return new Representation(json);
    }
    
    @Override
    public String toString()
    {
        return json.toString();
    }
}
