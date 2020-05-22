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
package com.restheart.changestreams;

import com.mongodb.MongoClientURI;
import com.restheart.changestreams.db.MongoDBReactiveClientSingleton;
import com.restheart.changestreams.handlers.GetChangeStreamHandler;
import com.restheart.utils.LogUtils;
import com.softinstigate.lickeys.CommLicense;
import com.softinstigate.lickeys.CommLicense.STATUS;
import java.util.Map;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.mongodb.handlers.RequestDispatcherHandler;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
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
@RegisterPlugin(name = "changeStreamActivator",
        description = "activates support for change streams",
        priority = Integer.MIN_VALUE + 1)
public class Activator implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    MongoClientURI mongoURI;
    
    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public void setConf(Map<String, Object> args) {
        this.mongoURI = new MongoClientURI(argValue(args, "mongo-uri"));
    }
    
    @Override
    public void init() {
        if (CommLicense.getStatus() == STATUS.OK) {
            if (!MongoClientSingleton.getInstance().isReplicaSet()) {
                LogUtils.boxedWarn(LOGGER,
                        "MongoDB is a standalone instance.",
                        "",
                        "Change Streams require a Replica Set.",
                        "",
                        "More information at https://restheart.org/docs/setup/");
            } else {
                enableChangeStreams();
            }
        } else {
            LOGGER.warn("License key has not been activated. "
                    + "RESTHeart Platform features disabled.");
        }
    }

    private void enableChangeStreams() {
        var dispatcher = RequestDispatcherHandler.getInstance();

        // *** init MongoDBReactiveClient
        try {
            MongoDBReactiveClientSingleton.init(this.mongoURI);
            // force setup
            MongoDBReactiveClientSingleton.getInstance();

            // *** Change Stream handler
            dispatcher.putHandler(TYPE.CHANGE_STREAM, METHOD.GET,
                    new GetChangeStreamHandler());
        } catch (Throwable t) {
            LOGGER.error("Change streams disabled due to error "
                    + "in MongoDB reactive client : {}", t.getMessage());
        }
    }
}
