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
package com.softinstigate.restheart.security.handlers;

import com.softinstigate.restheart.security.AccessManager;
import io.undertow.predicate.Predicate;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.Set;

/**
 *
 * @author uji
 */
public class PredicateAuthenticationConstraintHandler extends AuthenticationConstraintHandler
{
    AccessManager am;

    public PredicateAuthenticationConstraintHandler(HttpHandler next, AccessManager am)
    {
        super(next);
        this.am = am;
    }

    @Override
    protected boolean isAuthenticationRequired(final HttpServerExchange exchange)
    {
        Set<Predicate> ps = am.getAcl().get("$unauthenticated");

        if (ps == null)
        {
            return true;
        }
        else
        {
            return !ps.stream().anyMatch(p -> p.resolve(exchange));
        }
    }
}