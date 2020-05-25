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
package com.restheart.txns.db;

import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoClient;
import com.mongodb.MongoQueryException;
import static com.mongodb.client.model.Filters.eq;
import java.util.UUID;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.mongodb.db.sessions.ClientSessionImpl;
import org.restheart.mongodb.db.sessions.ServerSessionImpl;
import org.restheart.mongodb.db.sessions.Sid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class TxnsUtils {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(TxnsUtils.class);
    
    private static final MongoClient MCLIENT = MongoClientSingleton
            .getInstance().getClient();
    
    /**
     * Warn: requires two round trips to server
     *
     * @param sid
     * @return the txn status from server
     */
    public static Txn getTxnServerStatus(UUID sid) {
        var options = Sid.getSessionOptions(sid);

        var cso = ClientSessionOptions
                .builder()
                .causallyConsistent(options.isCausallyConsistent())
                .build();

        var cs = TxnClientSessionFactory.getInstance().createClientSession(
                sid,
                cso,
                MCLIENT.getReadConcern(),
                MCLIENT.getWriteConcern(),
                MCLIENT.getReadPreference(),
                null);

        // set txnId on ServerSession
        if (cs.getServerSession().getTransactionNumber()
                < 1) {
            ((ServerSessionImpl) cs.getServerSession())
                    .setTransactionNumber(1);
        }

        try {
            if (!cs.hasActiveTransaction()) {
                cs.setMessageSentInCurrentTransaction(true);
                cs.startTransaction();
            }
            propagateSession(cs);
            return new Txn(1, Txn.TransactionStatus.IN);
        } catch (MongoQueryException mqe) {
            var num = getTxnNumFromExc(mqe);

            if (num > 1) {
                cs.setServerSessionTransactionNumber(num);
                try {
                    propagateSession(cs);
                    return new Txn(num, Txn.TransactionStatus.IN);
                } catch (MongoQueryException mqe2) {
                    return new Txn(num, getTxnStateFromExc(mqe2));
                }
            } else {
                return new Txn(num, getTxnStateFromExc(mqe));
            }
        }
    }

    /**
     * Warn: requires a round trip to the server
     *
     * @param cs
     * @throws MongoQueryException
     */
    public static void propagateSession(ClientSessionImpl cs)
            throws MongoQueryException {
        LOGGER.trace("*********** round trip to server to propagate session");
        MCLIENT
                .getDatabase("foo")
                .getCollection("bar")
                .find(cs)
                .limit(1)
                .projection(eq("_id", 1))
                .first();
    }

    private static final String TXT_NUM_ERROR_MSG_PREFIX_STARTED = "because a newer transaction ";
    private static final String TXT_NUM_ERROR_MSG_SUFFIX_STARTED = " has already started";

    private static final String TXT_NUM_ERROR_MSG_PREFIX_ABORTED = "Transaction ";
    private static final String TXT_NUM_ERROR_MSG_SUFFIX_ABORTED = " has been aborted";

    private static final String TXT_NUM_ERROR_MSG_PREFIX_COMMITTED = "Transaction ";
    private static final String TXT_NUM_ERROR_MSG_SUFFIX_COMMITTED = " has been committed";

    private static final String TXT_NUM_ERROR_MSG_PREFIX_NONE = "Given transaction number ";
    private static final String TXT_NUM_ERROR_MSG_SUFFIX_NONE = " does not match any in-progress transactions";

    /**
     * gets the actual txn id from error messages
     *
     * @param mqe
     * @return
     */
    private static long getTxnNumFromExc(MongoQueryException mqe) {
        if (mqe.getErrorCode() == 225 && mqe.getErrorMessage() != null) {
            int start = mqe.getErrorMessage().indexOf(TXT_NUM_ERROR_MSG_PREFIX_STARTED)
                    + TXT_NUM_ERROR_MSG_PREFIX_STARTED.length();
            int end = mqe.getErrorMessage().indexOf(TXT_NUM_ERROR_MSG_SUFFIX_STARTED);

            if (start >= 0 && end >= 0) {
                String numStr = mqe.getErrorMessage().substring(start, end).trim();

                return Long.parseLong(numStr);
            }
        } else if (mqe.getErrorCode() == 251 && mqe.getErrorMessage() != null) {
            int start = mqe.getErrorMessage().indexOf(TXT_NUM_ERROR_MSG_PREFIX_ABORTED)
                    + TXT_NUM_ERROR_MSG_PREFIX_ABORTED.length();
            int end = mqe.getErrorMessage().indexOf(TXT_NUM_ERROR_MSG_SUFFIX_ABORTED);

            if (start >= 0 && end >= 0) {
                String numStr = mqe.getErrorMessage().substring(start, end).trim();

                return Long.parseLong(numStr);
            }

            start = mqe.getErrorMessage().indexOf(TXT_NUM_ERROR_MSG_PREFIX_NONE)
                    + TXT_NUM_ERROR_MSG_PREFIX_NONE.length();
            end = mqe.getErrorMessage().indexOf(TXT_NUM_ERROR_MSG_SUFFIX_NONE);

            if (start >= 0 && end >= 0) {
                String numStr = mqe.getErrorMessage().substring(start, end).trim();

                return Long.parseLong(numStr);
            }
        } else if (mqe.getErrorCode() == 256 && mqe.getErrorMessage() != null) {
            int start = mqe.getErrorMessage().indexOf(TXT_NUM_ERROR_MSG_PREFIX_COMMITTED)
                    + TXT_NUM_ERROR_MSG_PREFIX_COMMITTED.length();
            int end = mqe.getErrorMessage().indexOf(TXT_NUM_ERROR_MSG_SUFFIX_COMMITTED);

            if (start >= 0 && end >= 0) {
                String numStr = mqe.getErrorMessage().substring(start, end).trim();

                return Long.parseLong(numStr);
            }
        }

        LOGGER.trace("***** query error not handled {}: {}",
                mqe.getErrorCode(),
                mqe.getErrorMessage());

        throw mqe;
    }

    /**
     * get txn status from error messag
     *
     * @param mqe
     * @return
     */
    private static Txn.TransactionStatus getTxnStateFromExc(MongoQueryException mqe) {
        if (mqe.getErrorCode() == 251) {
            if (mqe.getErrorMessage().contains(
                    "does not match any in-progress transactions")) {
                return Txn.TransactionStatus.NONE;
            } else if (mqe.getErrorMessage().contains(
                    "has been aborted")) {
                return Txn.TransactionStatus.ABORTED;
            }
        } else if (mqe.getErrorCode() == 256) {
            if (mqe.getErrorMessage().contains(
                    "has been committed")) {
                return Txn.TransactionStatus.COMMITTED;
            }
        }

        LOGGER.trace("***** query error not handled {}: {}",
                mqe.getErrorCode(),
                mqe.getErrorMessage());

        throw mqe;
    }
}
