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
package com.restheart;

import com.restheart.db.MongoDBReactiveClientSingleton;
import com.restheart.db.txns.TxnClientSessionFactory;
import com.restheart.handlers.sessions.txns.DeleteTxnHandler;
import com.restheart.handlers.sessions.txns.GetTxnHandler;
import com.restheart.handlers.sessions.txns.PatchTxnHandler;
import com.restheart.handlers.sessions.txns.PostTxnsHandler;
import com.restheart.handlers.stream.GetChangeStreamHandler;
import com.softinstigate.lickeys.CommLicense;
import com.softinstigate.lickeys.CommLicense.STATUS;
import java.util.Map;
import org.restheart.Bootstrapper;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestDispatcherHandler;
import static org.restheart.handlers.RequestDispatcherHandler.DEFAULT_RESP_TRANFORMERS;
import org.restheart.handlers.ResponseSenderHandler;
import org.restheart.handlers.injectors.ClientSessionInjectorHandler;
import org.restheart.handlers.metadata.RequestTransformerHandler;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.LogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "rhPlatformActivator",
        description = "Activates RESTHeart Platform",
        priority = Integer.MIN_VALUE + 1)
public class Activator implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    @Override
    public void init(Map<String, Object> confArgs) {
        if (CommLicense.getStatus() == STATUS.OK) {
            if (!MongoDBClientSingleton.isReplicaSet()) {
                LogUtils.boxedWarn(LOGGER,
                        "MongoDB is a standalone instance.",
                        "",
                        "Transactions and Change Streams require a Replica Set.",
                        "",
                        "More information at https://restheart.org/docs/setup/");
            } else {
                // TODO check if listner is ajp and show warning message about change stream
                if (!Bootstrapper.getConfiguration()
                        .isHttpListener() && !Bootstrapper.getConfiguration()
                                .isHttpsListener()) {
                    LogUtils.boxedWarn(LOGGER,
                            "Only the AJP listener is enabled.",
                            "",
                            "Change Streams require the HTTP(S) listener.",
                            "",
                            "More information at https://restheart.org/docs/setup/");
                } else {
                    enableChangeStreams();
                }

                enableTxns();
            }
        } else {
            LOGGER.warn("License key has not been activated. "
                    + "RESTHeart Platform features disabled.");
        }
    }

    private void enableTxns() {
        var dispatcher = RequestDispatcherHandler.getInstance();

        ClientSessionInjectorHandler.getInstance()
                .setClientSessionFactory(TxnClientSessionFactory
                        .getInstance());

        // *** Txns handlers
        dispatcher.putPipedHttpHandler(RequestContext.TYPE.TRANSACTIONS,
                RequestContext.METHOD.POST,
                new RequestTransformerHandler(
                        new PostTxnsHandler(DEFAULT_RESP_TRANFORMERS)));

        dispatcher.putPipedHttpHandler(RequestContext.TYPE.TRANSACTIONS,
                RequestContext.METHOD.GET,
                new RequestTransformerHandler(
                        new GetTxnHandler(DEFAULT_RESP_TRANFORMERS)));

        dispatcher.putPipedHttpHandler(RequestContext.TYPE.TRANSACTION,
                RequestContext.METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteTxnHandler(DEFAULT_RESP_TRANFORMERS)));

        dispatcher.putPipedHttpHandler(RequestContext.TYPE.TRANSACTION,
                RequestContext.METHOD.PATCH,
                new RequestTransformerHandler(
                        new PatchTxnHandler(DEFAULT_RESP_TRANFORMERS)));
    }

    private void enableChangeStreams() {
        var dispatcher = RequestDispatcherHandler.getInstance();

        // *** init MongoDBReactiveClient
        try {
            MongoDBReactiveClientSingleton.init(Bootstrapper.getConfiguration().getMongoUri());
            // force setup
            MongoDBReactiveClientSingleton.getInstance();

            // *** Change Stream handler
            dispatcher.putPipedHttpHandler(RequestContext.TYPE.CHANGE_STREAM,
                    RequestContext.METHOD.GET,
                    new RequestTransformerHandler(
                            new GetChangeStreamHandler(
                                    new ResponseSenderHandler(null))));
        } catch (Throwable t) {
            LOGGER.error("Change streams disabled due to error "
                    + "in MongoDB reactive client : {}", t.getMessage());
        }
    }
}
