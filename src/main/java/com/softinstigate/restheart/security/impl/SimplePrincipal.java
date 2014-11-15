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
package com.softinstigate.restheart.security.impl;

import java.security.Principal;

/**
 *
 * @author Andrea Di Cesare
 */
public class SimplePrincipal implements Principal {
    private String name;

    /**
     *
     * @param name
     */
    public SimplePrincipal(String name) {
        if (name == null) {
            throw new IllegalArgumentException("argument name cannot be null");
        }

        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
