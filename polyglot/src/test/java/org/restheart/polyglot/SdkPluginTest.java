/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.polyglot.interceptors.StringJSInterceptor;
import org.restheart.polyglot.services.JSStringService;
import org.restheart.polyglot.spi.Capabilities;
import org.restheart.polyglot.spi.ContentKind;
import org.restheart.polyglot.spi.PluginDefinition.Kind;

import com.google.gson.JsonParser;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

/**
 * A plugin written against {@code restheart:sdk}, loaded and run the way the deployer does it,
 * on a request built by hand: the declaration, the request and response adapters, the
 * utilities, the typed errors, and a plugin importing a capability this test has not.
 */
class SdkPluginTest {
    private static final Capabilities NONE = Capabilities.none();

    private static Path plugin(Path dir, String file, String source) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{ \"rh:services\": [\"" + file + "\"], \"rh:interceptors\": [\"" + file + "\"] }");
        var path = dir.resolve(file);
        Files.writeString(path, source);
        return path;
    }

    private static HttpServerExchange exchange(String method, String path) {
        var exchange = new HttpServerExchange(null);
        exchange.setRequestMethod(new HttpString(method));
        exchange.setRequestPath(path);
        exchange.setRelativePath(path);
        exchange.setRequestURI(path);
        return exchange;
    }

    // ---- declaration

    @Test
    void loadsAnSdkService(@TempDir Path dir) throws Exception {
        var path = plugin(dir, "svc.mjs", """
                import { defineService } from "restheart:sdk";
                export default defineService({
                    name: "svc", description: "a service", uri: "/svc", secured: true, matchPolicy: "EXACT", contentKind: "json",
                    handle(req, res) { res.json({ ok: true }); }
                });
                """);

        var def = PluginLoader.load(path, Kind.SERVICE, NONE, null, Optional.empty()).definition();

        assertTrue(def.sdk());
        assertEquals("svc", def.name());
        assertEquals("a service", def.description());
        assertEquals("/svc", def.uri());
        assertTrue(def.secured());
        assertEquals(MATCH_POLICY.EXACT, def.matchPolicy());
        assertEquals(ContentKind.JSON, def.contentKind());
        assertNull(def.pluginClass());
        assertEquals(dir, def.pluginRoot());
    }

    @Test
    void loadsALegacyService(@TempDir Path dir) throws Exception {
        var path = plugin(dir, "legacy.mjs", """
                export const options = { name: "legacy", description: "before the sdk", uri: "/legacy" };
                export function handle(req, res) { res.setContent("hi"); }
                """);

        var def = PluginLoader.load(path, Kind.SERVICE, NONE, null, Optional.empty()).definition();

        assertFalse(def.sdk());
        assertEquals("legacy", def.name());
        assertEquals(MATCH_POLICY.PREFIX, def.matchPolicy());
        assertEquals(ContentKind.STRING, def.contentKind());
        assertFalse(def.secured());
    }

    @Test
    void legacyPluginClassIsAnAliasOfContentKind(@TempDir Path dir) throws Exception {
        var path = plugin(dir, "i.mjs", """
                export const options = { name: "i", description: "mongo interceptor", interceptPoint: "RESPONSE", pluginClass: "MongoInterceptor" };
                export function handle(req, res) {}
                export function resolve(req) { return true; }
                """);

        var def = PluginLoader.load(path, Kind.INTERCEPTOR, NONE, null, Optional.empty()).definition();

        assertEquals(ContentKind.MONGO, def.contentKind());
        assertEquals("MongoInterceptor", def.pluginClass());
        assertEquals(InterceptPoint.RESPONSE, def.interceptPoint());
    }

    @Test
    void aMissingCapabilityFailsAtLoadTimeWithItsName(@TempDir Path dir) throws Exception {
        var path = plugin(dir, "mail.mjs", """
                import { defineService, mail } from "restheart:sdk";
                export default defineService({ name: "m", description: "mails", uri: "/m", handle(req, res) { mail.send({}); } });
                """);

        var e = assertThrows(PluginLoader.MissingCapabilityException.class,
                () -> PluginLoader.load(path, Kind.SERVICE, NONE, null, Optional.empty()));

        assertEquals("mail", e.capability());
        assertTrue(e.getMessage().contains("emails provider"), e.getMessage());
    }

    @Test
    void aServiceListedAsInterceptorIsRefused(@TempDir Path dir) throws Exception {
        var path = plugin(dir, "x.mjs", """
                import { defineService } from "restheart:sdk";
                export default defineService({ name: "x", description: "x", uri: "/x", handle(req, res) {} });
                """);

        var e = assertThrows(IllegalArgumentException.class, () -> PluginLoader.load(path, Kind.INTERCEPTOR, NONE, null, Optional.empty()));
        assertTrue(e.getMessage().contains("defineService"), e.getMessage());
    }

    @Test
    void aWrongDeclarationSaysWhatIsWrong(@TempDir Path dir) throws Exception {
        var path = plugin(dir, "x.mjs", """
                import { defineService } from "restheart:sdk";
                export default defineService({ name: "x", description: "x", uri: "nope", handle(req, res) {} });
                """);

        var e = assertThrows(IllegalArgumentException.class, () -> PluginLoader.load(path, Kind.SERVICE, NONE, null, Optional.empty()));
        assertTrue(e.getMessage().contains("'uri' must start with /"), e.getMessage());

        var noHandle = plugin(dir, "y.mjs", """
                import { defineService } from "restheart:sdk";
                export default defineService({ name: "y", description: "y", uri: "/y" });
                """);

        var e2 = assertThrows(IllegalArgumentException.class, () -> PluginLoader.load(noHandle, Kind.SERVICE, NONE, null, Optional.empty()));
        assertTrue(e2.getMessage().contains("handle"), e2.getMessage());
    }

    // ---- running

    private static JSStringService service(Path dir, String body) throws Exception {
        var path = plugin(dir, "svc.mjs", """
                import { defineService, logger, crypto, encode, bson, config, resource,
                         BadRequestError, NotFoundError, HttpError } from "restheart:sdk";
                export default defineService({
                    name: "svc", description: "a service", uri: "/svc", contentKind: "string",
                    handle(req, res) {
                """ + body + """
                    }
                });
                """);
        var loaded = PluginLoader.load(path, Kind.SERVICE, NONE, null, Optional.empty());
        return new JSStringService(loaded.definition(), loaded.runtime(), null, Optional.empty());
    }

    @Test
    void theRequestAsNativeData(@TempDir Path dir) throws Exception {
        var srv = service(dir, """
                res.json({
                    path: req.path, method: req.method, isGet: req.isGet(), isPost: req.isPost(),
                    limit: req.query.limit, missing: req.query.missing === undefined, tags: req.queryAll.tag,
                    ct: req.headers["content-type"], CT: req.headers["CONTENT-TYPE"], absent: req.headers["x-nope"] === undefined,
                    keys: Object.keys(req.query), account: req.account, authenticated: req.isAuthenticated(),
                    params: req.pathParams("/svc/{id}"), content: req.content, contentType: req.contentType
                });
                """);

        var exchange = exchange("GET", "/svc/42");
        exchange.addQueryParam("limit", "10");
        exchange.addQueryParam("tag", "a");
        exchange.addQueryParam("tag", "b");
        exchange.getRequestHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        var request = StringRequest.init(exchange);
        request.setContent("hello");
        var response = StringResponse.init(exchange);

        srv.handle(request, response);

        var json = JsonParser.parseString(response.getContent()).getAsJsonObject();
        assertEquals("/svc/42", json.get("path").getAsString());
        assertEquals("GET", json.get("method").getAsString());
        assertTrue(json.get("isGet").getAsBoolean());
        assertFalse(json.get("isPost").getAsBoolean());
        assertEquals("10", json.get("limit").getAsString());
        assertTrue(json.get("missing").getAsBoolean());
        assertEquals(2, json.getAsJsonArray("tags").size());
        assertEquals("text/plain", json.get("ct").getAsString());
        assertEquals("text/plain", json.get("CT").getAsString());
        assertTrue(json.get("absent").getAsBoolean());
        assertTrue(json.get("account").isJsonNull());
        assertFalse(json.get("authenticated").getAsBoolean());
        assertEquals("42", json.getAsJsonObject("params").get("id").getAsString());
        assertEquals("hello", json.get("content").getAsString());
        assertEquals("text/plain", json.get("contentType").getAsString());
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
    }

    @Test
    void theResponseStatusHeadersAndText(@TempDir Path dir) throws Exception {
        var srv = service(dir, """
                res.statusCode = 201;
                res.headers["x-plugin"] = "svc";
                res.text(`got ${req.content.toUpperCase()}`);
                """);

        var exchange = exchange("POST", "/svc");
        var request = StringRequest.init(exchange);
        request.setContent("ciao");
        var response = StringResponse.init(exchange);

        srv.handle(request, response);

        assertEquals(201, response.getStatusCode(), "text() keeps a status already set");
        assertEquals("svc", response.getHeader("X-Plugin"));
        assertEquals("got CIAO", response.getContent());
        assertTrue(response.getContentType().startsWith("text/plain"));
    }

    @Test
    void theUtilities(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Files.writeString(dir.resolve("data/greeting.txt"), "ciao");

        var srv = service(dir, """
                logger.info("null {} undefined {} object {}", null, undefined, { a: 1 });
                let outside;
                try { resource("../etc/passwd"); outside = "read"; } catch (e) { outside = "refused"; }
                res.json({
                    sha256: crypto.sha256("abc"),
                    hmac: crypto.hmacSha256("key", "The quick brown fox jumps over the lazy dog"),
                    equal: crypto.timingSafeEqual("abc", "abc"), different: crypto.timingSafeEqual("abc", "abd"),
                    uuid: crypto.uuid().length, hex: crypto.randomHex(4).length,
                    b64: encode.toBase64("ciao"), back: encode.fromUtf8(encode.fromBase64("Y2lhbw==")),
                    url: encode.urlEncode("a b&c"), hexs: encode.toHex("hi"),
                    oid: bson.oid({ $oid: "507f1f77bcf86cd799439011" }), isOid: bson.isOid("507f1f77bcf86cd799439011"), fresh: typeof bson.newOid().$oid,
                    greeting: resource("data/greeting.txt"), exists: resource.exists("data/greeting.txt"), outside,
                    config: config.missing === undefined
                });
                """);

        var exchange = exchange("GET", "/svc");
        var request = StringRequest.init(exchange);
        request.setContent(null);
        var response = StringResponse.init(exchange);

        srv.handle(request, response);

        assertEquals(200, response.getStatusCode(), response.getContent());
        var json = JsonParser.parseString(response.getContent()).getAsJsonObject();
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", json.get("sha256").getAsString());
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8", json.get("hmac").getAsString());
        assertTrue(json.get("equal").getAsBoolean());
        assertFalse(json.get("different").getAsBoolean());
        assertEquals(36, json.get("uuid").getAsInt());
        assertEquals(8, json.get("hex").getAsInt());
        assertEquals("Y2lhbw==", json.get("b64").getAsString());
        assertEquals("ciao", json.get("back").getAsString());
        assertEquals("a+b%26c", json.get("url").getAsString());
        assertEquals("6869", json.get("hexs").getAsString());
        assertEquals("507f1f77bcf86cd799439011", json.get("oid").getAsString());
        assertTrue(json.get("isOid").getAsBoolean());
        assertEquals("string", json.get("fresh").getAsString());
        assertEquals("ciao", json.get("greeting").getAsString());
        assertTrue(json.get("exists").getAsBoolean());
        assertEquals("refused", json.get("outside").getAsString());
        assertTrue(json.get("config").getAsBoolean());
    }

    @Test
    void aTypedErrorIsItsStatus(@TempDir Path dir) throws Exception {
        var srv = service(dir, """
                if (req.query.kind === "bad") throw new BadRequestError("wrong limit");
                if (req.query.kind === "nf") throw new NotFoundError();
                if (req.query.kind === "teapot") throw new HttpError(418, "short and stout");
                throw new TypeError("a secret");
                """);

        for (var kind : new String[][]{{"bad", "400", "wrong limit"}, {"nf", "404", "Not Found"}, {"teapot", "418", "short and stout"}}) {
            var exchange = exchange("GET", "/svc");
            exchange.addQueryParam("kind", kind[0]);
            var response = StringResponse.init(exchange);

            srv.handle(StringRequest.init(exchange), response);

            assertEquals(Integer.parseInt(kind[1]), response.getStatusCode());
            assertEquals(kind[2], JsonParser.parseString(response.getContent()).getAsJsonObject().get("msg").getAsString());
        }

        var exchange = exchange("GET", "/svc");
        var response = StringResponse.init(exchange);
        srv.handle(StringRequest.init(exchange), response);

        assertEquals(500, response.getStatusCode());
        assertEquals("Internal Server Error", JsonParser.parseString(response.getContent()).getAsJsonObject().get("msg").getAsString());
        assertFalse(response.getContent().contains("secret"), "nothing of an uncaught exception reaches the client");
    }

    @Test
    void anInterceptorResolvesAndHandles(@TempDir Path dir) throws Exception {
        var path = plugin(dir, "i.mjs", """
                import { defineInterceptor } from "restheart:sdk";
                export default defineInterceptor({
                    name: "i", description: "appends", interceptPoint: "RESPONSE", contentKind: "string",
                    handle(req, res) { res.content = `${res.content}!`; res.headers["x-i"] = "yes"; },
                    resolve(req, res) { return req.query.go === "1"; }
                });
                """);
        var loaded = PluginLoader.load(path, Kind.INTERCEPTOR, NONE, null, Optional.empty());
        var interceptor = new StringJSInterceptor(loaded.definition(), loaded.runtime(), null, Optional.empty());

        assertEquals(InterceptPoint.RESPONSE, interceptor.getInterceptPoint());
        assertEquals("string", interceptor.getPluginClass());

        var skip = exchange("GET", "/x");
        assertFalse(interceptor.resolve(StringRequest.init(skip), StringResponse.init(skip)));

        var go = exchange("GET", "/x");
        go.addQueryParam("go", "1");
        var request = StringRequest.init(go);
        var response = StringResponse.init(go);
        response.setContent("hello");

        assertTrue(interceptor.resolve(request, response));
        interceptor.handle(request, response);

        assertEquals("hello!", response.getContent());
        assertEquals("yes", response.getHeader("x-i"));
    }

    @Test
    void aLegacyPluginGetsTheJavaObjects(@TempDir Path dir) throws Exception {
        var path = plugin(dir, "legacy.mjs", """
                export const options = { name: "legacy", description: "before the sdk", uri: "/legacy" };
                export function handle(request, response) {
                    LOGGER.debug("legacy {}", request.getPath());
                    response.setContent(JSON.stringify({ path: request.getPath(), args: typeof pluginArgs }));
                    response.setContentTypeAsJson();
                }
                """);
        var loaded = PluginLoader.load(path, Kind.SERVICE, NONE, null, Optional.empty());
        var srv = new JSStringService(loaded.definition(), loaded.runtime(), null, Optional.empty());

        var exchange = exchange("GET", "/legacy");
        var response = StringResponse.init(exchange);
        srv.handle(StringRequest.init(exchange), response);

        var json = JsonParser.parseString(response.getContent()).getAsJsonObject();
        assertEquals("/legacy", json.get("path").getAsString());
        assertEquals("object", json.get("args").getAsString());
    }

    @Test
    void methodsSeeTheJavaMethod(@TempDir Path dir) throws Exception {
        var srv = service(dir, "res.json({ m: req.method, post: req.isPost() });");

        var exchange = exchange("POST", "/svc");
        exchange.setRequestMethod(Methods.POST);
        var response = StringResponse.init(exchange);
        srv.handle(StringRequest.init(exchange), response);

        var json = JsonParser.parseString(response.getContent()).getAsJsonObject();
        assertEquals("POST", json.get("m").getAsString());
        assertTrue(json.get("post").getAsBoolean());
    }
}
