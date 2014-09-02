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
package com.softinstigate.restheart;

import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.handlers.ErrorHandler;
import com.softinstigate.restheart.handlers.GzipEncodingHandler;
import com.softinstigate.restheart.handlers.RequestDispacherHandler;
import com.softinstigate.restheart.handlers.SchemaEnforcerHandler;
import com.softinstigate.restheart.handlers.root.DeleteRootHandler;
import com.softinstigate.restheart.handlers.root.GetRootHandler;
import com.softinstigate.restheart.handlers.root.PatchRootHandler;
import com.softinstigate.restheart.handlers.root.PostRootHandler;
import com.softinstigate.restheart.handlers.root.PutRootHandler;
import com.softinstigate.restheart.handlers.collection.DeleteCollectionHandler;
import com.softinstigate.restheart.handlers.collection.GetCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PatchCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PostCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PutCollectionHandler;
import com.softinstigate.restheart.handlers.database.DeleteDBHandler;
import com.softinstigate.restheart.handlers.database.GetDBHandler;
import com.softinstigate.restheart.handlers.database.PatchDBHandler;
import com.softinstigate.restheart.handlers.database.PostDBHandler;
import com.softinstigate.restheart.handlers.database.PutDBHandler;
import com.softinstigate.restheart.handlers.document.DeleteDocumentHandler;
import com.softinstigate.restheart.handlers.document.GetDocumentHandler;
import com.softinstigate.restheart.handlers.document.PatchDocumentHandler;
import com.softinstigate.restheart.handlers.document.PostDocumentHandler;
import com.softinstigate.restheart.handlers.document.PutDocumentHandler;
import com.softinstigate.restheart.security.MapIdentityManager;
import com.softinstigate.restheart.utils.ResourcesExtractor;
import io.undertow.Undertow;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.HttpContinueAcceptingHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.path;
import io.undertow.Undertow.Builder;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author uji
 */
public class Bootstrapper
{
    private static Undertow server;

    private static final Logger logger = LoggerFactory.getLogger(Bootstrapper.class);

    private static File browserRootFile = null;

    public static void main(final String[] args)
    {
        Yaml yaml = new Yaml();

        File confFile;

        if (args == null || args.length < 1)
        {
            confFile = new File("restheart.yml");
        }
        else
        {
            confFile = new File(args[0]);
        }

        Map<String, Object> conf = null;

        try
        {
            conf = (Map<String, Object>) yaml.load(new FileInputStream(confFile));
        }
        catch (FileNotFoundException ex)
        {
            System.err.println("cannot find the configuration file. exiting..");
            System.exit(-2);
        }

        boolean httpsListener = (Boolean) conf.getOrDefault("https-listener", true);
        int httpsPort = (Integer) conf.getOrDefault("https-port", "8443");
        String httpsHost = (String) conf.getOrDefault("https-host", "0.0.0.0");

        boolean httpListener = (Boolean) conf.getOrDefault("http-listener", false);
        int httpPort = (Integer) conf.getOrDefault("http-port", "8080");
        String httpHost = (String) conf.getOrDefault("http-host", "0.0.0.0");

        boolean ajpListener = (Boolean) conf.getOrDefault("ajp-listener", false);
        int ajpPort = (Integer) conf.getOrDefault("ajp-port", "8009");
        String ajpHost = (String) conf.getOrDefault("ajp-host", "0.0.0.0");

        boolean useEmbeddedKeystore = (Boolean) conf.getOrDefault("use-embedded-keystore", true);
        String keystoreFile = (String) conf.get("keystore-file");
        String keystorePassword = (String) conf.get("keystore-password");
        String certPassword = (String) conf.get("certpassword");

        String mongoHost = (String) conf.getOrDefault("mongo-host", "127.0.0.1");
        int mongoPort = (Integer) conf.getOrDefault("mongo-port", 27017);
        String mongoUser = (String) conf.getOrDefault("mongo-user", "");
        String mongoPassword = (String) conf.getOrDefault("mongo-password", "");

        int ioThreads = (Integer) conf.getOrDefault("io-threads", 8);
        int workerThreads = (Integer) conf.getOrDefault("worker-threads", 500);
        int bufferSize = (Integer) conf.getOrDefault("buffer-size", 16384);
        int buffersPerRegion = (Integer) conf.getOrDefault("buffers-per-region", 20);
        boolean directBuffers = (Boolean) conf.getOrDefault("direct-buffers", "true");
        
        boolean forceGzipEncoding = (Boolean) conf.getOrDefault("force-gzip-encoding", true);

        try
        {
            MongoDBClientSingleton.init(mongoHost, mongoPort, mongoUser, mongoPassword);
        }
        catch (Throwable t)
        {
            logger.error("error connecting to mongodb. exiting..", t);
            System.exit(-1);
        }

        try
        {
            start(
                    httpsListener, httpsHost, httpsPort,
                    httpListener, httpHost, httpPort,
                    ajpListener, ajpHost, ajpPort,
                    useEmbeddedKeystore, keystoreFile, keystorePassword, certPassword,
                    ioThreads, workerThreads, bufferSize, buffersPerRegion, directBuffers, forceGzipEncoding);
        }
        catch (Throwable t)
        {
            logger.error("error starting restheart. exiting..", t);
            System.exit(-2);
        }

        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                if (server != null)
                {
                    try
                    {
                        server.stop();
                    }
                    catch (Throwable t)
                    {
                        logger.error("error stopping undertow server", t);
                    }
                }

                try
                {
                    MongoClient client = MongoDBClientSingleton.getInstance().getClient();
                    client.fsync(false);
                    client.close();
                }
                catch (Throwable t)
                {
                    logger.error("error flushing and clonsing the mongo cliet", t);
                }

                if (browserRootFile != null)
                {
                    try
                    {
                        ResourcesExtractor.deleteTempDir("browser", browserRootFile);
                    }
                    catch (URISyntaxException | IOException ex)
                    {
                        logger.error("error cleaning up temporary directory {}", browserRootFile.toString(), ex);
                    }
                }

                logger.info("restheart stopped");
            }
        });

        logger.info("restheart started");
    }

    private static void start(
            boolean httpsListener,
            String httpsHost,
            int httpsPort,
            boolean httpListener,
            String httpHost,
            int httpPort,
            boolean ajpListener,
            String ajpHost,
            int ajpPort,
            boolean useEmbeddedKeystore,
            String keystoreFile,
            String keystorePassword,
            String certPassword,
            int ioThreads,
            int workerThreads,
            int bufferSize,
            int buffersPerRegion,
            boolean directBuffers,
            boolean forceGzipEncoding
            
    )
    {
        if (!httpsListener && !httpListener && !ajpListener)
        {
            logger.error("no listener specified. exiting..");
            System.exit(-1);
        }

        final Map<String, char[]> users = new HashMap<>(2);
        users.put("admin", "admin".toCharArray());
        users.put("user", "user".toCharArray());

        final IdentityManager identityManager = new MapIdentityManager(users);

        SSLContext sslContext = null;

        try
        {
            KeyManagerFactory kmf;
            KeyStore ks;

            if (useEmbeddedKeystore)
            {
                char[] storepass = "restheart".toCharArray();
                char[] keypass = "restheart".toCharArray();

                String storename = "rakeystore.jks";

                sslContext = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");
                ks.load(Bootstrapper.class.getClassLoader().getResourceAsStream(storename), storepass);

                kmf.init(ks, keypass);
                sslContext.init(kmf.getKeyManagers(), null, null);
            }
            else
            {
                sslContext = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");

                FileInputStream fis = new FileInputStream(new File(keystoreFile));

                ks.load(fis, keystorePassword.toCharArray());

                kmf.init(ks, certPassword.toCharArray());
                sslContext.init(kmf.getKeyManagers(), null, null);
            }
        }
        catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | CertificateException | UnrecoverableKeyException ex)
        {
            logger.error("couldn't start restheart, error with specified keystore. exiting..", ex);
            System.exit(-1);
        }
        catch (FileNotFoundException ex)
        {
            logger.error("couldn't start restheart, keystore file not found. exiting..", ex);
            System.exit(-1);
        }
        catch (IOException ex)
        {
            logger.error("couldn't start restheart, error reading the keystore file. exiting..", ex);
            System.exit(-1);
        }

        try
        {
            browserRootFile = ResourcesExtractor.extract("browser");
        }
        catch (URISyntaxException | IOException ex)
        {
            logger.error("error instanitating browser web app. exiting..", ex);
            System.exit(-1);
        }

        logger.info("static resources are in {}", browserRootFile.toString());

        Builder builder = Undertow.builder();

        if (httpsListener)
        {
            builder.addHttpsListener(httpsPort, httpsHost, sslContext);
            logger.info("https listener bound at {}:{}", httpsHost, httpsPort);
        }

        if (httpListener)
        {
            builder.addHttpListener(httpPort, httpHost);
            logger.info("http listener bound at {}:{}", httpHost, httpPort);
        }

        if (ajpListener)
        {
            builder.addAjpListener(ajpPort, ajpHost);
            logger.info("ajp listener bound at {}:{}", ajpHost, ajpPort);
        }

        builder
                .setIoThreads(ioThreads)
                .setWorkerThreads(workerThreads)
                .setDirectBuffers(directBuffers)
                .setBufferSize(bufferSize)
                .setBuffersPerRegion(buffersPerRegion)
                .setHandler(
                        path()
                        .addPrefixPath("/@browser", resource(new FileResourceManager(browserRootFile, 3)).addWelcomeFiles("browser.html").setDirectoryListingEnabled(false))
                        .addPrefixPath("/",
                                addSecurity(
                                        new GzipEncodingHandler(
                                                new ErrorHandler(
                                                        new BlockingHandler(
                                                                new HttpContinueAcceptingHandler(
                                                                        new SchemaEnforcerHandler(
                                                                                new RequestDispacherHandler(
                                                                                        new GetRootHandler(),
                                                                                        new PostRootHandler(),
                                                                                        new PutRootHandler(),
                                                                                        new DeleteRootHandler(),
                                                                                        new PatchRootHandler(),
                                                                                        new GetDBHandler(),
                                                                                        new PostDBHandler(),
                                                                                        new PutDBHandler(),
                                                                                        new DeleteDBHandler(),
                                                                                        new PatchDBHandler(),
                                                                                        new GetCollectionHandler(),
                                                                                        new PostCollectionHandler(),
                                                                                        new PutCollectionHandler(),
                                                                                        new DeleteCollectionHandler(),
                                                                                        new PatchCollectionHandler(),
                                                                                        new GetDocumentHandler(),
                                                                                        new PostDocumentHandler(),
                                                                                        new PutDocumentHandler(),
                                                                                        new DeleteDocumentHandler(),
                                                                                        new PatchDocumentHandler()
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                ), forceGzipEncoding
                                            ), identityManager)
                                ));

        builder.build().start();
    }

    private static HttpHandler addSecurity(final HttpHandler toWrap, final IdentityManager identityManager)
    {
        HttpHandler handler = toWrap;
        handler = new AuthenticationCallHandler(handler);
        handler = new AuthenticationConstraintHandler(handler);
        final List<AuthenticationMechanism> mechanisms = Collections.<AuthenticationMechanism>singletonList(new BasicAuthenticationMechanism("My Realm"));
        handler = new AuthenticationMechanismsHandler(handler, mechanisms);
        handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);
        return handler;
    }
}
