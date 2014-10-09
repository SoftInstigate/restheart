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
package com.softinstigate.restheart.test;

import com.softinstigate.restheart.Configuration;
import java.net.URI;
import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.utils.URIBuilder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

/**
 *
 * @author uji
 */
public abstract class AbstactIT
{
    protected static final String confFilePath = "etc/restheart.yml";
    protected static Configuration conf = null;
    protected static Executor adminExecutor = null;
    protected static Executor userExecutor = null;
    protected static Executor unauthExecutor = null;
    
    protected static URI rootUri;
    
    @Before
    public void setUp() throws Exception
    {
        conf = new Configuration(confFilePath);
        
        rootUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/")
                .build();

        adminExecutor = Executor.newInstance().auth(new HttpHost(conf.getHttpHost()), "a", "a");
        userExecutor = Executor.newInstance().auth(new HttpHost(conf.getHttpHost()), "user", "changeit");
        unauthExecutor= Executor.newInstance();
    }
    
    public AbstactIT()
    {
    }
    
    @BeforeClass
    public static void setUpClass()
    {
    }
    
    @AfterClass
    public static void tearDownClass()
    {
    }
    
    @After
    public void tearDown()
    {
    }
}