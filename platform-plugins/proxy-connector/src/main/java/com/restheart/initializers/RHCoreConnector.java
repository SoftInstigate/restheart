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
package com.restheart.initializers;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.restheart.net.Client;
import com.restheart.net.Request;
import com.restheart.utils.LogUtils;
import com.restheart.utils.NetUtils;
import static io.undertow.Handlers.path;
import io.undertow.Undertow;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.HttpString;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Map;
import org.restheart.Configuration;
import org.restheart.ConfigurationException;
import static org.restheart.ConfigurationKeys.DEFAULT_HTTP_HOST;
import static org.restheart.ConfigurationKeys.DEFAULT_HTTP_PORT;
import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "rhCoreConnector",
        priority = 100,
        description = "Connects a Proxy to RESTHeart Mongo Service",
        enabledByDefault = true)
public class RHCoreConnector implements Initializer {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(RHCoreConnector.class);
    
    public static final String X_FORWARDED_ACCOUNT_ID = "rhAuthenticator";
    public static final String X_FORWARDED_ROLE = "RESTHeart";

    private URI restheartBaseUrl;
    private Configuration configuration;

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public void setConf(Map<String, Object> args) {
        this.configuration = new Configuration(args, true);

        this.restheartBaseUrl = this.configuration.getRestheartBaseUrl();
    }

    @Override
    public void init() {
        if (restheartBaseUrl != null) {
            try {
                waitForConnection(restheartBaseUrl);
            } catch (ConfigurationException ex) {
                LOGGER.error("Error connecting to RESTHeart Platform Core:",
                        ex);
                System.exit(-6060);
            }

            LOGGER.info("Connected to RESTHeart Platform");
        } else {
            LOGGER.warn("Cannot get RESTHeart base url. "
                    + "Make sure your proxy configuration specifies "
                    + "an entry with 'name: restheart'");
        }
    }

    public void waitForConnection(URI restheartBaseUrl) {
        boolean waitMessageShown = false;
        boolean licenseNotAcceptedHandled = false;
        Undertow undertow = null;

        while (true) {
            try {
                var status = ping(restheartBaseUrl);

                if ("OK".equals(status)) {
                    if (undertow != null) {
                        undertow.stop();
                    }

                    break;
                } else {
                    if (!waitMessageShown) {
                        LOGGER.info("Waiting for RESTHeart Platform Core.....");
                        waitMessageShown = true;
                    }

                    if (!licenseNotAcceptedHandled
                            && "LICENSE_NOT_YET_ACCEPTED".equals(status)) {
                        licenseNotAcceptedHandled = true;

                        requestAcceptingLicense();

                        undertow = startProxy(restheartBaseUrl,
                                this.configuration == null
                                ? DEFAULT_HTTP_HOST
                                : this.configuration.getHttpHost(),
                                this.configuration == null
                                ? DEFAULT_HTTP_PORT
                                : this.configuration.getHttpPort());
                    }

                    try {
                        Thread.sleep(5 * 1000);
                    } catch (InterruptedException ie) {
                        // nothing to do
                    }
                }
            } catch (Throwable t) {
                if (!waitMessageShown) {
                    LOGGER.info("Waiting for RESTHeart Platform Core.....");
                    waitMessageShown = true;
                }

                if (t.getCause() != null
                        && (t.getCause() instanceof MalformedURLException
                        || t.getCause() instanceof URISyntaxException)) {
                    LOGGER.error("Wrong RESTHeart Platform Core URL, "
                            + "{} Exiting...",
                            t.getCause().getMessage());
                    System.exit(-6050);
                } else {
                    LOGGER.warn("RESTHeart Platform Core not yet available "
                            + "at {}",
                            restheartBaseUrl);
                }

                LOGGER.trace("Error connecting to RESTHeart Platform Core: {}",
                        t.getMessage());

                try {
                    Thread.sleep(5 * 1000);
                } catch (InterruptedException ie) {
                    // nothing to do
                }
            }
        }
    }

    private String ping(URI restheartBaseUrl) throws IOException, UnirestException {
        var pingUrl = restheartBaseUrl
                .resolve("/_internal/status");
        if (restheartBaseUrl.getScheme().equals("ajp")) {
            var resp = Client.getInstance()
                    .execute(new Request(Request.METHOD.GET, pingUrl)
                            .header(getXForwardedAccountIdHeaderName().toString(),
                                    X_FORWARDED_ACCOUNT_ID)
                            .header(getXForwardedRolesHeaderName().toString(),
                                    X_FORWARDED_ROLE));
            return resp.getBodyAsJson()
                    .getAsJsonObject()
                    .get("status")
                    .getAsString();
        } else {
            var resp = Unirest.get(pingUrl.toString())
                    .header(getXForwardedAccountIdHeaderName().toString(),
                            X_FORWARDED_ACCOUNT_ID)
                    .header(getXForwardedRolesHeaderName().toString(),
                            X_FORWARDED_ROLE)
                    .asJson();

            return resp.getBody().getObject().getString("status");
        }
    }

    /**
     * This resembles the method si-lka CommLicense.requestAcceptingLicense()
     * however, in this case we have the server always accepting connection at
     * http-host:http-port, since we have started a dedicated server
     */
    private void requestAcceptingLicense() {
        var host = this.configuration == null
                ? DEFAULT_HTTP_HOST
                : this.configuration.getHttpHost();

        var port = this.configuration == null
                ? DEFAULT_HTTP_PORT
                : this.configuration.getHttpPort();

        if ("127.0.0.1".equals(host)
                || "localhost".equals(host)) {
            LogUtils.boxedWarn(LOGGER,
                    "The License Agreement has not yet been accepted.",
                    "",
                    "Please open your browser at http://localhost:" + port + "/license and",
                    "accept the license to continue.",
                    "",
                    "The HTTP listener is bound to localhost: accept the license from ",
                    "a browser running on the same host or edit the configuration.",
                    "",
                    "More information at",
                    "https://restheart.org/docs/setup#accept-license");
        } else if ("0.0.0.0".equals(host)) {
            try {
                host = NetUtils.getLocalHostLANAddress().getHostAddress();
            } catch (UnknownHostException ex) {
                // nothing to do
            }

            LogUtils.boxedWarn(LOGGER,
                    "The License Agreement has not yet been accepted.",
                    "",
                    "Please open your browser at http://"
                    + host
                    + ":"
                    + port
                    + "/license and",
                    "accept the license to continue.",
                    "",
                    "We have detected your LAN address; to accept the license from",
                    "an external network use the host's public IP.",
                    "",
                    "More information at",
                    "https://restheart.org/docs/setup#accept-license");
        } else {
            LogUtils.boxedWarn(LOGGER,
                    "The License Agreement has not yet been accepted.",
                    "",
                    "Please open your browser at http://"
                    + host
                    + ":"
                    + port
                    + "/license and",
                    "accept the license to continue.",
                    "",
                    "More information at",
                    "https://restheart.org/docs/setup#accept-license");
        }
    }

    private Undertow startProxy(URI restheartBaseUrl, String host, int port)
            throws URISyntaxException {
        Undertow undertow = null;

        LoadBalancingProxyClient proxyClient
                = new LoadBalancingProxyClient();

        proxyClient.addHost(restheartBaseUrl.resolve("/license"));

        ProxyHandler proxyHandler = ProxyHandler.builder()
                .setProxyClient(proxyClient)
                .build();

        var path = path();

        path.addPrefixPath("/license", proxyHandler);

        undertow = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(path)
                .build();

        undertow.start();

        return undertow;
    }
    
    public static HttpString getXForwardedHeaderName(String suffix) {
        return HttpString.tryFromString("X-Forwarded-".concat(suffix));
    }

    public static HttpString getXForwardedAccountIdHeaderName() {
        return getXForwardedHeaderName("Account-Id");
    }

    public static HttpString getXForwardedRolesHeaderName() {
        return getXForwardedHeaderName("Account-Roles");
    }
}
