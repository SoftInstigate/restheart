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

import com.softinstigate.restart.handlers.RequestDispacherHandler;
import com.softinstigate.restart.handlers.ErrorHandler;
import com.softinstigate.restart.handlers.SchemaEnforcerHandler;
import com.softinstigate.restart.handlers.collections.DeleteCollectionsHandler;
import com.softinstigate.restart.handlers.collections.GetCollectionsHandler;
import com.softinstigate.restart.handlers.collections.PostCollectionsHandler;
import com.softinstigate.restart.handlers.collections.PutCollectionsHandler;
import com.softinstigate.restart.handlers.databases.DeleteDBHandler;
import com.softinstigate.restart.handlers.databases.GetDBHandler;
import com.softinstigate.restart.handlers.databases.PostDBHandler;
import com.softinstigate.restart.handlers.databases.PutDBHandler;
import io.undertow.Undertow;
import java.util.Scanner;

/**
 *
 * @author uji
 */
public class Bootstrapper
{
    private static Undertow server;

    public static void main(final String[] args)
    {
        deployAndStart();

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
                deployAndStart();
                started = true;

                System.out.println("started");
            }

            in.nextLine();
        }
    }

    private static void deployAndStart()
    {
        server = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setWorkerThreads(50)
                .setHandler(new ErrorHandler(
                                new SchemaEnforcerHandler(
                                        new RequestDispacherHandler(
                                                new GetDBHandler(), // get collections
                                                new PostDBHandler(), // create collection !!!
                                                new PutDBHandler(), // create db
                                                new DeleteDBHandler(), // delete db

                                                new GetCollectionsHandler(), // get documents
                                                new PostCollectionsHandler(), // create document
                                                new PutCollectionsHandler(), // create collection
                                                new DeleteCollectionsHandler(), // delete collection

                                                new GetCollectionsHandler(), // get document
                                                new PostCollectionsHandler(), // ???
                                                new PutCollectionsHandler(), // create/update document
                                                new DeleteCollectionsHandler() // delete document
                                        )
                                )
                        )
                )
                .build();
        server.start();
    }

    private static void stop()
    {
        server.stop();
    }
}
