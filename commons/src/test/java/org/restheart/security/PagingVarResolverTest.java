package org.restheart.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;

/**
 * {@code @page}, {@code @pagesize}, {@code @limit} and {@code @skip} are resolved from the request
 * (restheart#808): they used to be supplied among the values of the variables, which a {@code @}
 * name does not read since restheart#727.
 */
public class PagingVarResolverTest {

    private static MongoRequest page(int page, int pagesize) {
        var request = mock(MongoRequest.class);
        when(request.getPage()).thenReturn(page);
        when(request.getPagesize()).thenReturn(pagesize);
        return request;
    }

    @Test
    public void theVariablesAreRegistered() {
        assertTrue(AclVarsInterpolator.isRegisteredVar("@page"));
        assertTrue(AclVarsInterpolator.isRegisteredVar("@pagesize"));
        assertTrue(AclVarsInterpolator.isRegisteredVar("@limit"));
        assertTrue(AclVarsInterpolator.isRegisteredVar("@skip"));
    }

    @Test
    public void theThirdPageOfTen() {
        var request = page(3, 10);

        assertEquals(3, AclVarsInterpolator.resolveVar(request, "@page").asInt32().getValue());
        assertEquals(10, AclVarsInterpolator.resolveVar(request, "@pagesize").asInt32().getValue());
        assertEquals(10, AclVarsInterpolator.resolveVar(request, "@limit").asInt32().getValue());
        assertEquals(20, AclVarsInterpolator.resolveVar(request, "@skip").asInt32().getValue());
    }

    @Test
    public void theFirstPageSkipsNothing() {
        assertEquals(0, AclVarsInterpolator.resolveVar(page(1, 100), "@skip").asInt32().getValue());
    }

    @Test
    public void unboundOutsideAMongoRequest() {
        var request = mock(Request.class);

        assertTrue(AclVarsInterpolator.resolveVar(request, "@skip").isNull());
    }
}
