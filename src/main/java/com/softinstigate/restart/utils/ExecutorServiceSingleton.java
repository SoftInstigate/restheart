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

package com.softinstigate.restart.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author uji
 */
public class ExecutorServiceSingleton
{
    private final ExecutorService executorService;
    
    private ExecutorServiceSingleton()
    {
        this.executorService = Executors.newFixedThreadPool(100);
    }
    
    public static ExecutorServiceSingleton getInstance()
    {
        return ExecutorServiceSingletonHolder.INSTANCE;
    }

    /**
     * @return the executorService
     */
    public ExecutorService getExecutorService()
    {
        return executorService;
    }
    
    private static class ExecutorServiceSingletonHolder
    {
        private static final ExecutorServiceSingleton INSTANCE = new ExecutorServiceSingleton();
    }
}
