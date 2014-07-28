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
package com.softinstigate.restheart.utils;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import jdk.nashorn.internal.parser.JSONParser;
import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author uji
 */
public class JSONHelper
{
    public static String HAL_JSON_MEDIA_TYPE = "application/hal+json";
    public static String JSON_MEDIA_TYPE = "application/json";

    private static final Logger logger = LoggerFactory.getLogger(JSONHelper.class);

    static public URI getReference(String parentUrl, String referencedName)
    {
        try
        {
            return new URI(removeTrailingSlashes(parentUrl) + "/" + referencedName);
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating URI from {} + / + {}", parentUrl, referencedName, ex);
        }

        return null;
    }

    static private String removeTrailingSlashes(String s)
    {
        if (s.trim().charAt(s.length() - 1) == '/')
        {
            return removeTrailingSlashes(s.substring(0, s.length() - 1));
        }
        else
        {
            return s.trim();
        }
    }

    static public JsonObject getCollectionHal(String baseUrl, Map<String, Object> properties, Map<String, URI> links, List<Map<String, Object>> embedded)
    {
        JsonObject root = new JsonObject();

        if (properties != null && !properties.isEmpty())
        {
            properties.keySet().stream().forEach(k -> addObject(root, k, properties.get(k)));
        }

        if (links != null && !links.isEmpty())
        {
            JsonObject linksFragment = new JsonObject();

            links.keySet().stream().forEach(k -> linksFragment.add(k, new JsonObject().add("href", links.get(k).toString())));

            root.add("_links", linksFragment);
        }

        if (embedded != null & !embedded.isEmpty())
        {
            JsonObject embeddedFragment = new JsonObject();

            JsonArray embeddedItems = new JsonArray();

            embedded.stream().map((itemProperties) ->
            {
                JsonObject embeddedItem = new JsonObject();
                String id = null;

                for (String itemKey : itemProperties.keySet())
                {
                    addObject(embeddedItem, itemKey, itemProperties.get(itemKey));

                    if (itemKey.equals("id") || itemKey.equals("_id")) // id or _id filed are there sice collection was already filtered
                    {
                        id = itemProperties.get(itemKey).toString();
                    }
                }
                JsonObject embeddedItemlinksFragment = new JsonObject();
                embeddedItemlinksFragment.add("self", new JsonObject().add("href", removeTrailingSlashes(baseUrl) + "/" + id));
                embeddedItem.add("_links", embeddedItemlinksFragment);
                return embeddedItem;
            }).forEach((embeddedItem) ->
            {
                embeddedItems.add(embeddedItem);
            });

            root.add("_embedded", embeddedFragment.add("rh:collection", embeddedItems));
        }

        return root;
    }

    static public JsonObject getDocumentHal(String baseUrl, Map<String, Object> properties, Map<String, URI> links)
    {
        JsonObject root = new JsonObject();

        if (properties != null && !properties.isEmpty())
        {
            properties.keySet().stream().forEach(k -> addObject(root, k, properties.get(k)));
        }

        if (links != null && !links.isEmpty())
        {
            JsonObject linksFragment = new JsonObject();

            links.keySet().stream().forEach(k -> linksFragment.add(k, new JsonObject().add("href", links.get(k).toString())));

            root.add("_links", linksFragment);
        }

        return root;
    }

    private static void addObject(JsonObject json, String key, Object obj)
    {
        if (obj instanceof Integer)
        {
            json.add(key, (Integer) obj);
        }
        else if (obj instanceof Long)
        {
            json.add(key, (Long) obj);
        }
        else if (obj instanceof Float)
        {
            json.add(key, (Float) obj);
        }
        else if (obj instanceof Boolean)
        {
            json.add(key, (Boolean) obj);
        }
        else if (obj instanceof Double)
        {
            json.add(key, (Double) obj);
        }
        else if (obj instanceof String)
        {
            json.add(key, (String) obj);
        }
        else if (obj instanceof Map)
        {
            JsonObject nested = new JsonObject();

            Map<String, Object> nestedMap = (Map<String, Object>) obj;

            (nestedMap).keySet().stream().forEach(k -> addObject(nested, k, nestedMap.get(k)));

            json.add(key, nested);
        }
    }

    public static DBObject convertJsonToDbObj(JsonValue json)
    {
        return (DBObject) JSON.parse(json.toString());
    }
    
    public static JsonObject convertDbObjToJson(DBObject dbObj)
    {
        return JsonObject.readFrom(dbObj.toString());
    }
}
