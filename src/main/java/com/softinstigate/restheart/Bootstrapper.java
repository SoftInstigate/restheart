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
import com.softinstigate.restheart.handlers.RequestDispacherHandler;
import com.softinstigate.restheart.handlers.SchemaEnforcerHandler;
import com.softinstigate.restheart.handlers.account.DeleteAccountHandler;
import com.softinstigate.restheart.handlers.account.GetAccountHandler;
import com.softinstigate.restheart.handlers.account.PatchAccountHandler;
import com.softinstigate.restheart.handlers.account.PostAccountHandler;
import com.softinstigate.restheart.handlers.account.PutAccountHandler;
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
import io.undertow.Undertow;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.HttpContinueAcceptingHandler;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author uji
 */
public class Bootstrapper
{
    private static Undertow server;
    
    private static final Logger logger = LoggerFactory.getLogger(Bootstrapper.class);
    
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
            System.err.println("cannot find the configuration file");
            System.exit(-2);
        }

        int port = (Integer) conf.getOrDefault("port", "443");
        boolean useEmbeddedKeystore = (Boolean) conf.getOrDefault("use-embedded-keystore", "true");
        String keystoreFile = (String) conf.get("keystore-file");
        String keystorePassword = (String) conf.get("keystore-password");
        String certPassword = (String) conf.get("certpassword");
        String mongoHost = (String) conf.getOrDefault("mongo-host", "127.0.0.1");
        int mongoPort = (Integer) conf.getOrDefault("mongo-port", 27017);
        String mongoUser = (String) conf.getOrDefault("mongo-user", "");
        String mongoPassword = (String) conf.getOrDefault("mongo-password", "");

        MongoDBClientSingleton.init(mongoHost, mongoPort, mongoUser, mongoPassword);

        logger.info("configuration {}", conf.toString());

        start(port, useEmbeddedKeystore, keystoreFile, keystorePassword, certPassword);

        MongoClient client = MongoDBClientSingleton.getInstance().getClient();
        
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
                    catch(Throwable t)
                    {
                        logger.error("error stopping undertow server", t);
                    }
                }
                
                if (client != null)
                {
                    try
                    {
                        client.fsync(false);
                        client.close();
                    }
                    catch(Throwable t)
                    {
                        logger.error("error closing flushing and clonsing the mongo cliet", t);
                    }
                }
                
                logger.info("restheart stopped");
            }
        });

        logger.info("restheart started on port " + port);
    }

    private static void start(int port,
            boolean useEmbeddedKeystore,
            String keystoreFile,
            String keystorePassword,
            String certPassword)
    {
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
                throw new IllegalArgumentException("custom keyfactory not yet implemented");
            }
        }
        catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | CertificateException | UnrecoverableKeyException ex)
        {
            logger.error("couldn't start restheart, error with specified keystore", ex);
            System.exit(-1);
        }
        catch (FileNotFoundException ex)
        {
            logger.error("couldn't start restheart, keystore file not found" , ex);
            System.exit(-1);
        }
        catch (IOException ex)
        {
            logger.error("couldn't start restheart, error reading the keystore file", ex);
            System.exit(-1);
        }

        server = Undertow.builder()
                .addHttpsListener(port, "0.0.0.0", sslContext)
                .setWorkerThreads(50)
                .setHandler(addSecurity(
                                new ErrorHandler(
                                        new BlockingHandler(
                                                new HttpContinueAcceptingHandler(
                                                        new SchemaEnforcerHandler(
                                                                new RequestDispacherHandler(
                                                                        new GetAccountHandler(),
                                                                        new PostAccountHandler(),
                                                                        new PutAccountHandler(),
                                                                        new DeleteAccountHandler(),
                                                                        new PatchAccountHandler(),
                                                                        
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
                                ), identityManager)
                )
                .build();
        server.start();
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