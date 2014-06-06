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
package com.softinstigate.restart;

import com.softinstigate.restart.handlers.ErrorHandler;
import com.softinstigate.restart.handlers.RequestDispacherHandler;
import com.softinstigate.restart.handlers.SchemaEnforcerHandler;
import com.softinstigate.restart.handlers.collections.DeleteCollectionsHandler;
import com.softinstigate.restart.handlers.collections.GetCollectionsHandler;
import com.softinstigate.restart.handlers.collections.PostCollectionsHandler;
import com.softinstigate.restart.handlers.collections.PutCollectionsHandler;
import com.softinstigate.restart.handlers.databases.DeleteDBHandler;
import com.softinstigate.restart.handlers.databases.GetDBHandler;
import com.softinstigate.restart.handlers.databases.PostDBHandler;
import com.softinstigate.restart.handlers.databases.PutDBHandler;
import com.softinstigate.restart.handlers.documents.DeleteDocumentHandler;
import com.softinstigate.restart.handlers.documents.GetDocumentHandler;
import com.softinstigate.restart.handlers.documents.PostDocumentHandler;
import com.softinstigate.restart.handlers.documents.PutDocumentHandler;
import com.softinstigate.restart.security.MapIdentityManager;
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
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/**
 *
 * @author uji
 */
public class Bootstrapper
{
    private static Undertow server;

    public static void main(final String[] args)
    {
        start();

        System.out.println("started");
        System.out.println("press any key to stop/start");

        Scanner in = new Scanner(System.in);

        boolean started = true;

        while (in.hasNextLine())
        {
            if (started)
            {
                stop();
                started = false;

                System.out.println("stopped");
            }
            else
            {
                start();
                started = true;

                System.out.println("started");
            }

            in.nextLine();
        }
    }

    private static void start()
    {
        final Map<String, char[]> users = new HashMap<>(2);
        users.put("admin", "admin".toCharArray());
        users.put("user", "user".toCharArray());

        final IdentityManager identityManager = new MapIdentityManager(users);
        
        SSLContext sslContext;
        
        try
        {
            KeyManagerFactory kmf;
            KeyStore ks;
            char[] storepass = "restheart".toCharArray();
            char[] keypass   = "restheart".toCharArray();
            
            String storename = "rakeystore.jks";

            sslContext = SSLContext.getInstance("TLS");
            kmf = KeyManagerFactory.getInstance("SunX509");
            ks = KeyStore.getInstance("JKS");
            ks.load(Bootstrapper.class.getClassLoader().getResourceAsStream(storename), storepass);

            kmf.init(ks, keypass);
            sslContext.init(kmf.getKeyManagers(), null, null);
        }
        catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | CertificateException | UnrecoverableKeyException ex)
        {
            Logger.getLogger(Bootstrapper.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        catch (FileNotFoundException ex)
        {
            Logger.getLogger(Bootstrapper.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        catch (IOException ex)
        {
            Logger.getLogger(Bootstrapper.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        
        server = Undertow.builder()
                .addHttpsListener(443, "0.0.0.0", sslContext)
                .setWorkerThreads(50)
                .setHandler(addSecurity(
                        new ErrorHandler(
                                new BlockingHandler(
                                        new SchemaEnforcerHandler(
                                            new RequestDispacherHandler(
                                                new GetDBHandler(),             // get collections
                                                new PostDBHandler(),            // not allowed
                                                new PutDBHandler(),             // not allowed
                                                new DeleteDBHandler(),          // not allowed

                                                new GetCollectionsHandler(),    // get documents
                                                new PostCollectionsHandler(),   // create document(s)
                                                new PutCollectionsHandler(),    // not allowed
                                                new DeleteCollectionsHandler(), // delete documents

                                                new GetDocumentHandler(),       // get document
                                                new PostDocumentHandler(),      // not allowed !
                                                new PutDocumentHandler(),       // create/update document
                                                new DeleteDocumentHandler()     // delete document
                                            )
                                        )
                                )
                            ), identityManager)
                )
                .build();
        server.start();
    }
    
    /*
    
    
    
    new MgmtRequestDispacherHandler(
                                                new GetDBHandler(),             // get db metadata
                                                new PostDBHandler(),            // not allowed
                                                new PutDBHandler(),             // create / update db
                                                new DeleteDBHandler(),          // delete db

                                                new GetCollectionsHandler(),    // get collection metadata
                                                new PostCollectionsHandler(),   // not allowed
                                                new PutCollectionsHandler(),    // create / update collection
                                                new DeleteCollectionsHandler(), // delete collection

                                                new GetDocumentHandler(),       // not allowed
                                                new PostDocumentHandler(),      // not allowed !
                                                new PutDocumentHandler(),       // not allowed
                                                new DeleteDocumentHandler()     // not allowed
                                            )
    
    */

    private static void stop()
    {
        server.stop();
    }
    
    private static HttpHandler addSecurity(final HttpHandler toWrap, final IdentityManager identityManager) {
        HttpHandler handler = toWrap;
        handler = new AuthenticationCallHandler(handler);
        handler = new AuthenticationConstraintHandler(handler);
        final List<AuthenticationMechanism> mechanisms = Collections.<AuthenticationMechanism>singletonList(new BasicAuthenticationMechanism("My Realm"));
        handler = new AuthenticationMechanismsHandler(handler, mechanisms);
        handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);
        return handler;
    }
}
