/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.ai.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;

public class RequestOverridesTest {

    private static final String KEY = "override-ai-some-key";

    // -- str --------------------------------------------------------------

    @Test
    public void str_nullRequest_returnsDefault() {
        assertEquals("default", RequestOverrides.str(null, KEY, "default"));
    }

    @Test
    public void str_noAttachedParam_returnsDefault() {
        var req = mock(Request.class);
        when(req.attachedParam(KEY)).thenReturn(null);
        assertEquals("default", RequestOverrides.str(req, KEY, "default"));
    }

    @Test
    public void str_blankAttachedParam_returnsDefault() {
        var req = mock(Request.class);
        when(req.attachedParam(KEY)).thenReturn("   ");
        assertEquals("default", RequestOverrides.str(req, KEY, "default"));
    }

    @Test
    public void str_nonStringAttachedParam_returnsDefault() {
        var req = mock(Request.class);
        when(req.attachedParam(KEY)).thenReturn(42);
        assertEquals("default", RequestOverrides.str(req, KEY, "default"));
    }

    @Test
    public void str_validAttachedParam_takesPrecedenceOverDefault() {
        var req = mock(Request.class);
        when(req.attachedParam(KEY)).thenReturn("overridden");
        assertEquals("overridden", RequestOverrides.str(req, KEY, "default"));
    }

    // -- intVal -------------------------------------------------------------

    @Test
    public void intVal_nullRequest_returnsDefault() {
        assertEquals(7, RequestOverrides.intVal(null, KEY, 7));
    }

    @Test
    public void intVal_noAttachedParam_returnsDefault() {
        var req = mock(Request.class);
        when(req.attachedParam(KEY)).thenReturn(null);
        assertEquals(7, RequestOverrides.intVal(req, KEY, 7));
    }

    @Test
    public void intVal_attachedAsInteger_takesPrecedence() {
        var req = mock(Request.class);
        when(req.attachedParam(KEY)).thenReturn(42);
        assertEquals(42, RequestOverrides.intVal(req, KEY, 7));
    }

    @Test
    public void intVal_attachedAsNumericString_isParsed() {
        var req = mock(Request.class);
        when(req.attachedParam(KEY)).thenReturn("42");
        assertEquals(42, RequestOverrides.intVal(req, KEY, 7));
    }

    @Test
    public void intVal_attachedAsNonNumericString_returnsDefault() {
        var req = mock(Request.class);
        when(req.attachedParam(KEY)).thenReturn("not-a-number");
        assertEquals(7, RequestOverrides.intVal(req, KEY, 7));
    }
}
