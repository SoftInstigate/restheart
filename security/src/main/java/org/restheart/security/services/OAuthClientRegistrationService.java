/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.services;

import java.time.Instant;
import java.util.UUID;

import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import static org.restheart.utils.GsonUtils.array;
import static org.restheart.utils.GsonUtils.object;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OAuth 2.0 Dynamic Client Registration endpoint (RFC 7591).
 *
 * <p>Accepts {@code POST /register} from OAuth clients (e.g. mcp-inspector)
 * and returns a generated {@code client_id}. RESTHeart's authorization service
 * embeds {@code client_id} directly into the authorization code JWT without
 * database validation, so no client storage is required.
 *
 * <p>Advertise this endpoint by setting
 * {@code /oauthAuthorizationServerMetadataService/registration-endpoint-uri: /register}
 * in the configuration. The service is disabled by default.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7591">RFC 7591</a>
 */
@RegisterPlugin(
    name             = "oauthClientRegistrationService",
    description      = "OAuth 2.0 Dynamic Client Registration endpoint (RFC 7591)",
    defaultURI       = "/register",
    secure           = false,
    enabledByDefault = false)
public class OAuthClientRegistrationService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthClientRegistrationService.class);

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @OnInit
    public void init() {
        aclRegistry.registerAllow(req ->
            req.getPath().equals("/register") && req.getMethod() == METHOD.POST);
    }

    @Override
    public void handle(JsonRequest request, JsonResponse response) throws Exception {
        switch (request.getMethod()) {
            case POST    -> handlePost(request, response);
            case OPTIONS -> handleOptions(request);
            default      -> response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void handlePost(JsonRequest request, JsonResponse response) {
        try {
            var body = request.getContent();

            // redirect_uris is required by RFC 7591
            if (body == null || !body.isJsonObject() || !body.getAsJsonObject().has("redirect_uris")) {
                response.setContent(object()
                    .put("error", "invalid_client_metadata")
                    .put("error_description", "redirect_uris is required"));
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                return;
            }

            var bodyObj      = body.getAsJsonObject();
            var redirectUris = bodyObj.get("redirect_uris").getAsJsonArray();
            var clientId     = UUID.randomUUID().toString();
            var issuedAt     = Instant.now().getEpochSecond();

            var authMethod = bodyObj.has("token_endpoint_auth_method")
                ? bodyObj.get("token_endpoint_auth_method").getAsString()
                : "none";

            var resp = object()
                .put("client_id", clientId)
                .put("client_id_issued_at", issuedAt)
                .put("redirect_uris", redirectUris)
                .put("token_endpoint_auth_method", authMethod)
                .put("grant_types",    array().add("authorization_code"))
                .put("response_types", array().add("code"));

            if (bodyObj.has("client_name")) {
                resp.put("client_name", bodyObj.get("client_name").getAsString());
            }

            LOGGER.debug("Registered OAuth client_id={} redirect_uris={}", clientId, redirectUris);

            response.setContent(resp);
            response.setStatusCode(HttpStatus.SC_CREATED);

        } catch (Exception e) {
            LOGGER.error("Client registration error", e);
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
