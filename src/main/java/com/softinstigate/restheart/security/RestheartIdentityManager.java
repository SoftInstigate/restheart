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
package com.softinstigate.restheart.security;

import io.undertow.security.idm.IdentityManager;

/**
 *
 * @author Andrea Di Cesare
 */
public abstract class RestheartIdentityManager implements IdentityManager {

    /**
     *
     */
    public static final String RESTHEART_REALM = "RestHeart Realm";
}
