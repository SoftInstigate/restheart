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

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 *
 * @author uji
 */
public class HalResource
{
    JsonObject root = null;
    
    public HalResource() {
        root = new JsonObject();
    }
    
    public static HalResource builder()
    {
        return new HalResource();
    }
    /*
        root.add("string", "Andrea Di Cesare");
        root.add("number", 100l);
        root.add("date", Instant.now().toString()); // ISO 8601
        
        root.add("array", new JsonArray().add("item1").add(2));
        
        root.add("nested", new JsonObject().add("key1", "value1").add("key2", "value2"));
    
    */
    
    private void addProperty(String key, Object value)
    {
        addProperty(root, key, value);
    }
    
    private void addProperty(JsonObject fragment, String key, Object value)
    {
        if (value == null)
            fragment.add(key, "");
        else if (value instanceof String)
            fragment.add(key, (String) value);
        else if (value instanceof Date)
            fragment.add(key, ((Date) value).toInstant().toString()); // ISO 8601
        else if (value instanceof Instant)
            fragment.add(key, ((Instant) value).toString()); // ISO 8601
        else if (Integer.class.isInstance(value))
            fragment.add(key,(int) value);
        else if (Long.class.isInstance(value))
            fragment.add(key,(long) value);
        else if (Double.class.isInstance(value))
            fragment.add(key,(double) value);
        else if (Float.class.isInstance(value))
            fragment.add(key,(float) value);
        else
            fragment.add(key, value.toString());
    }
    
    private void addArrayProperty(String key, Map<String, Object> items)
    {
        JsonArray toadd = new JsonArray();
        
        root.add(key, new JsonArray());
    }
    
    public void addLink(URI link)
    {
        
    }
}
