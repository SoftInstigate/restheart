/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.handlers.metrics;

import static org.junit.Assert.*;
import org.junit.Test;
import org.restheart.mongodb.handlers.metrics.MetricsHandler.ResponseType;
import org.restheart.mongodb.handlers.metrics.MetricsHandler.ResponseType.AcceptHeaderEntry;
public class ResponseTypeTest {

    /**
     *
     * @throws Exception
     */
    @Test
    public void testIsAcceptableFor() throws Exception {
        assertTrue(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("application/json")));
        assertTrue(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("application/*")));
        assertFalse(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("text/*")));
        assertTrue(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("*/*")));
        assertTrue(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("application/json; q=0.9")));
        assertFalse(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("application/json; q=0.9; extraparameter=foobar")));
        assertFalse(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("text/plain")));
        assertFalse(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("text/plain; version=0.0.4")));
        assertFalse(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("text/plain; version=0.0.4; q=1.0")));
        assertFalse(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("text/plain; version= 0.0.3")));
        assertFalse(ResponseType.JSON.isAcceptableFor(AcceptHeaderEntry.of("text/plain; version= 0.0.5")));


        assertFalse(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("application/json")));
        assertFalse(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("application/*")));
        assertTrue(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("text/*")));
        assertTrue(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("*/*")));
        assertFalse(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("application/json; q=0.9")));
        assertFalse(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("application/json; q=0.9; extraparameter=foobar")));
        assertTrue(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("text/plain")));
        assertTrue(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("text/plain; version=0.0.4")));
        assertTrue(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("text/plain; version=0.0.4; q=1.0")));
        assertFalse(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("text/plain; version= 0.0.3")));
        assertFalse(ResponseType.PROMETHEUS.isAcceptableFor(AcceptHeaderEntry.of("text/plain; version= 0.0.5")));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testCalculateMediaRange() throws Exception {
        assertEquals("application/*", ResponseType.calculateMediaRange("application/json"));
        assertEquals("text/*", ResponseType.calculateMediaRange("text/plain"));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testResponseTypeCreation() throws Exception {
        assertEquals(ResponseType.JSON, ResponseType.forAcceptHeader("application/json"));
        assertEquals(ResponseType.PROMETHEUS, ResponseType.forAcceptHeader("text/plain"));
        assertEquals(ResponseType.PROMETHEUS, ResponseType.forAcceptHeader("text/plain,application/json"));
        assertEquals(ResponseType.PROMETHEUS, ResponseType.forAcceptHeader("text/plain, application/json"));
        assertEquals(ResponseType.JSON, ResponseType.forAcceptHeader("text/plain; q=0.1, application/json"));
        assertEquals(ResponseType.JSON, ResponseType.forAcceptHeader("text/plain; q=0.1, application/json; q=1.0"));

        assertEquals(ResponseType.JSON, ResponseType.forQueryParameter("PLAIN_JSON"));
        assertEquals(ResponseType.JSON, ResponseType.forQueryParameter("PJ"));
        assertEquals(null, ResponseType.forQueryParameter("foobar"));
    }
}
