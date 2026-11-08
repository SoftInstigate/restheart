/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.undertow.Version;
import io.undertow.server.handlers.HostHeaderHandler;

/**
 * Guards the copy of Undertow's {@link HostHeaderHandler} in restheart-core (#801).
 *
 * <p>The copy is Undertow 2.4.4.Final's class without the IPv4 regex match. When
 * Undertow is upgraded this test fails: compare the new upstream class with the
 * copy and align it, or remove the copy if upstream dropped the regex.</p>
 */
public class HostHeaderHandlerShadowTest {
    private static final String SHADOWED_UNDERTOW_VERSION = "2.4.4.Final";

    @Test
    public void undertowVersionMatchesTheShadowedClass() {
        assertEquals(SHADOWED_UNDERTOW_VERSION, Version.getVersionString(),
                "Undertow was upgraded: align core/src/main/java/io/undertow/server/handlers/HostHeaderHandler.java "
                        + "with the new upstream class, or remove it if upstream dropped the IPv4 regex");
    }

    @Test
    public void restheartCopyIsTheLoadedClass() {
        var fields = Arrays.stream(HostHeaderHandler.class.getDeclaredFields()).map(f -> f.getName()).toList();

        assertFalse(fields.contains("IP4_EXACT"),
                "the loaded HostHeaderHandler is Undertow's, not RESTHeart's copy: check the class path order");
    }
}
