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
package com.softinstigate.restheart.handlers.applicationlogic;

import com.softinstigate.restheart.handlers.*;
import java.util.Map;

/**
 *
 * @author uji
 */
public abstract class ApplicationLogicHandler extends PipedHttpHandler {
    protected final Map<String, Object> args;

    public ApplicationLogicHandler(PipedHttpHandler next, Map<String, Object> args) {
        super(next);
        this.args = args;
    }
}
