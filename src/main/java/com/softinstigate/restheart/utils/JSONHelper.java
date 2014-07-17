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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URISyntaxException;
import net.hamnaberg.json.Collection;
import net.hamnaberg.json.Item;
import net.hamnaberg.json.Property;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author uji
 */
public class JSONHelper
{
    private static final Logger logger = LoggerFactory.getLogger(JSONHelper.class);
    
    static public URI getReference(String parentUrl, String referencedName)
    {
        try
        {
            return new URI(removeTrailingSlashes(parentUrl) + "/" + referencedName);
        }
        catch (URISyntaxException ex)
        {
            logger.error("error creating URI from {} + / + {}", parentUrl, referencedName , ex);
        }
        
        return null;
    }
    
    static private String removeTrailingSlashes(String s)
    {
        if (s.trim().charAt(s.length() - 1) == '/')
        {
            return removeTrailingSlashes(s.substring(0, s.length()-1));
        }
        else
        {
            return s.trim();
        }
    }
    
    static public String addMetadataAndGetJson(Collection c, Iterable<Property> props)
    {
        JsonNode node =  Collection.builder().addItem(Item.builder().addProperties(props).build()).build().asJson().findValue("items").findValue("data");
                
        ObjectNode on = c.asJson();
        
        on.set("metadata", node);
        
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode rootNode = mapper.createObjectNode();
        
        rootNode.set("collection", on);
        
        return rootNode.toString();
    }
}
