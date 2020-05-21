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
package com.restheart.authenticators;

import org.restheart.plugins.security.PwdCredentialAccount;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface MongoDelegate {
    /**
     * check if specified user collection exists; if not, create it
     *
     * @return true if user collection exists or has been created
     */
     boolean checkUserCollection();
     
     long countAccounts();
     
     void createDefaultAccount();
    
     PwdCredentialAccount findAccount(final String accountId);
    
}
