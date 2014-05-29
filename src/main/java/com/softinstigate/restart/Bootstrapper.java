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
                //.setWorkerThreads(50)
                .setHandler(new RESTHandler())
                .build();
        server.start();
    }
    
    private static void stop()
    {
        server.stop();
    }
}
