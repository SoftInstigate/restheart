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
package com.restheart.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.net.ConnectException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Ignore;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ClientTest {
    public ClientTest() {
    }

    @Test
    @Ignore // requires restheart-platform-core running on ajp://localhost:8009
    public void testAjp() throws Exception {
        var client = Client.getInstance();

        var request = new Request(Request.METHOD.GET, "ajp://localhost:8009/coll")
                .parameter("rep", "HAL")
                .parameter("np", "true")
                .header("foo", "bar");

        System.out.println("request: " + request);

        try {
            var response = client.execute(request);

            System.out.println("code: " + response.getStatusCode());
            System.out.println("headers: " + response.getResponseHeaders());
            System.out.println("body: " + response.getBodyAsJson());
        } catch (ConnectException ce) {
            System.out.println("host not reacheable " + ce.getMessage());
        }
    }

    @Test
    @Ignore // requires restheart-platform-core running on http://localhost:8080
    public void testHttp() throws Exception {
        var client = Client.getInstance();

        var request = new Request(Request.METHOD.GET, "http://localhost:8080");

        System.out.println("request: " + request);

        try {
            var response = client.execute(request);

            System.out.println("code: " + response.getStatusCode());
            System.out.println("headers: " + response.getResponseHeaders());
            System.out.println("body: " + response.getBody());
        } catch (ConnectException ce) {
            System.out.println("Host not reacheable " + ce.getMessage());
        }
    }

    @Test
    @Ignore // requires restheart-platform-core running on http://localhost:8080
    public void testPostHttp() throws Exception {
        var client = Client.getInstance();

        JsonObject body = new JsonObject();
        body.add("a", new JsonPrimitive(1));

        var request = new Request(Request.METHOD.POST, "http://localhost:8080/coll")
                .body(body);

        System.out.println("request: " + request);

        try {
            var response = client.execute(request);

            System.out.println("code: " + response.getStatusCode());
            System.out.println("headers: " + response.getResponseHeaders());
            System.out.println("body: " + response.getBody());
        } catch (ConnectException ce) {
            System.out.println("Host not reacheable " + ce.getMessage());
        }
    }

    @Test
    public void testRequestParameter() throws Exception {
        var req = new Request(Request.METHOD.GET,
                "http://localhost:8080/users")
                .header("header1", "hv1")
                .header("header2", "hv2")
                .parameter("np", "true")
                .parameter("filter", "{\""
                        .concat("_id")
                        .concat("\":\"")
                        .concat("uji")
                        .concat("\"}"))
                .parameter("pagesize", "" + 1)
                .parameter("rep", "STANDARD");

        //System.out.println("path: " + req.getPath());
        Assert.assertEquals("check req path",
                "/users?np=true&filter={\"_id\":\"uji\"}&pagesize=1&rep=STANDARD",
                req.getPath());
    }

    @Test
    public void testRequestParameter2() throws Exception {
        var req = new Request(Request.METHOD.GET,
                "http://localhost:8080/users")
                .header("header1", "hv1")
                .header("header2", "hv2");

        //System.out.println("path: " + req.getPath());
        Assert.assertEquals("check req path",
                "/users",
                req.getPath());
    }

    @Test
    public void testRequestParameter3() throws Exception {
        var req = new Request(Request.METHOD.GET,
                "http://localhost:8080/users?a=1")
                .parameter("b", "2");

        //System.out.println("path: " + req.getPath());
        Assert.assertEquals("check req path",
                "/users?a=1&b=2",
                req.getPath());
    }
}
