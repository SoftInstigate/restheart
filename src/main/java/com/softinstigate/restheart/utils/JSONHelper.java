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

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author uji
 */
public class JSONHelper
{
    static public Map<String, Map> getReference(String parentUrl, String referencedName)
    {
        Map<String, Map> ret  = new HashMap<>();
        Map<String, String> href = new HashMap<>();
        
        href.put("href", removeTrailingSlashes(parentUrl) + "/" + referencedName);
        ret.put(referencedName, href);
        
        return ret;
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
}
