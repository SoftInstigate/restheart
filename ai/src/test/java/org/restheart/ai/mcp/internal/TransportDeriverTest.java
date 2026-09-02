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
package org.restheart.ai.mcp.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.restheart.ai.mcp.api.McpResource.Transport;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.SseService;
import org.restheart.plugins.StringService;

import io.undertow.server.handlers.sse.ServerSentEventConnection;

public class TransportDeriverTest {

    private static final class Plain {
    }

    private static final class Json implements JsonService {
        @Override
        public void handle(JsonRequest request, JsonResponse response) {
        }
    }

    private static final class Bson implements BsonService {
        @Override
        public void handle(BsonRequest request, BsonResponse response) {
        }
    }

    private static final class ByteArray implements ByteArrayService {
        @Override
        public void handle(ByteArrayRequest request, ByteArrayResponse response) {
        }
    }

    private static final class Str implements StringService {
        @Override
        public void handle(StringRequest request, StringResponse response) {
        }
    }

    private static final class Sse implements SseService {
        @Override
        public void onConnect(ServerSentEventConnection connection, String lastEventId) {
        }
    }

    @Test
    public void plainObject_derivesNoTransport() {
        assertTrue(TransportDeriver.derive(new Plain()).isEmpty());
    }

    @Test
    public void jsonService_derivesHttp() {
        assertEquals(Set.of(Transport.HTTP), TransportDeriver.derive(new Json()));
    }

    @Test
    public void bsonService_derivesHttp() {
        assertEquals(Set.of(Transport.HTTP), TransportDeriver.derive(new Bson()));
    }

    @Test
    public void byteArrayService_derivesHttp() {
        assertEquals(Set.of(Transport.HTTP), TransportDeriver.derive(new ByteArray()));
    }

    @Test
    public void stringService_derivesHttp() {
        assertEquals(Set.of(Transport.HTTP), TransportDeriver.derive(new Str()));
    }

    @Test
    public void sseService_derivesSse() {
        assertEquals(Set.of(Transport.SSE), TransportDeriver.derive(new Sse()));
    }
}
