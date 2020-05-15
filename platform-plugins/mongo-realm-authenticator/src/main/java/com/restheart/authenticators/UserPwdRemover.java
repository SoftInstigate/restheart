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
package com.restheart.authenticators;

import com.google.gson.JsonElement;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.restheart.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.JsonUtils;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "pwdHider",
        description = "hides the password from the response",
        interceptPoint = InterceptPoint.RESPONSE,
        requiresContent = true)
public class UserPwdRemover implements MongoInterceptor {
    static final Logger LOGGER = LoggerFactory.getLogger(UserPwdRemover.class);
     
    private String usersUri;
    private String propNamePassword;
    private boolean enabled = false;

    @InjectPluginsRegistry
    public void init(PluginsRegistry registry) {
        var _rhAuth = registry.getAuthenticator("rhAuthenticator");

        if (_rhAuth == null || !_rhAuth.isEnabled()) {
            enabled = false;
        } else {
            var rhAuth = (MongoRealmAuthenticator) _rhAuth.getInstance();
            
            this.usersUri = rhAuth.getUsersUri();
            this.propNamePassword = rhAuth.getPropPassword();
            enabled = true;
        }   
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        DocumentContext dc = JsonPath.parse(response.readContent());

        JsonElement content = dc.json();
        
        if (content == null || content.isJsonNull()) {
            return;
        }
        
        if (content.isJsonArray()) {
            // GET collection as array of documents (rep=PJ + 'np' qparam)
            dc.delete("$.[*].".concat(this.propNamePassword));
        } else if (content.isJsonObject()
                && content.getAsJsonObject().keySet().contains("_embedded")) {
            if (content.getAsJsonObject().get("_embedded").isJsonArray()) {
                //  GET collection as a compact HAL document
                dc.delete("$._embedded.*.".concat(this.propNamePassword));
            } else if (content.getAsJsonObject().get("_embedded").isJsonObject()
                    && content.getAsJsonObject()
                            .get("_embedded").getAsJsonObject().keySet().contains("rh:doc")
                    && content.getAsJsonObject()
                            .get("_embedded").getAsJsonObject().get("rh:doc").isJsonArray()) {
                //  GET collection as a full HAL document
                dc.delete("$._embedded.['rh:doc'].*.".concat(this.propNamePassword));
            }

        } else if (content.isJsonObject()) {
            // GET document
            dc.delete("$.".concat(this.propNamePassword));
        }
        
        response.setContent(JsonUtils.parse(content.toString()));
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        var requestPath = URLUtils.removeTrailingSlashes(request.getPath());
        
        return enabled &&
                request.isGet()
                && (requestPath.equals(usersUri)
                || requestPath.startsWith(usersUri + "/"));
    }
}
