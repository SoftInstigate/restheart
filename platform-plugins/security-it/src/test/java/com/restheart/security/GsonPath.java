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
package com.restheart.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GsonPath {
    
    public GsonPath() {
        Configuration.setDefaults(new Configuration.Defaults() {
            private final JsonProvider jsonProvider = new GsonJsonProvider();
            private final MappingProvider mappingProvider = new GsonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });
    }

    @Test
    //@Ignore
    public void playWithJsonPath() {
        String sjson = "{"
                + "\"null\": null,"
                + "\"n\":1,"
                + "\"array\": [0,1,2,3,4,5],"
                + "\"object\": {"
                + " \"x\":1, "
                + "\"y\":2, "
                + "\"nested-array\": [0,1,2,3,4,5]}"
                + "}";
        
        JsonArray result = JsonPath.read(sjson, "$.object.nested-array[*]");
    
        System.out.println("result: " + result);

        DocumentContext dc = JsonPath.parse(sjson);
        
        JsonPrimitive a2 = dc.read("$.array[2]");
        
        dc.set("$.array[2]", new JsonPrimitive(a2.getAsInt() + 1000));
        
        System.out.println(dc.jsonString());
        
        JsonElement ne = dc.read("$.null");
        
        System.out.println("ne: " + ne);
    }
}
