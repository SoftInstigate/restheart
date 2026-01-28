/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb.handlers.injectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.interceptors.CollectionPropsInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class CollectionPropsInjectorHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionPropsInjectorHandlerTest.class);

    /**
     *
     */
    // @Rule
    // public TestRule watcher = new TestWatcher() {
    // @Override
    // protected void starting(Description description) {
    // LOG.info("executing test {}", description.toString());
    // }
    // };

    /**
     *
     */
    @Test
    public void testCheckCollectionPut() {
        var context = createRequest("/db/collection", "PUT");

        assertEquals(TYPE.COLLECTION, context.getType());
        assertEquals(METHOD.PUT, context.getMethod());
        assertEquals(false, CollectionPropsInjector.checkCollection(context));
    }

    /**
     *
     */
    @Test
    public void testCheckCollectionFilesPost() {
        var context = createRequest("/db/fs.files", "POST");

        assertEquals(TYPE.FILES_BUCKET, context.getType());
        assertEquals(METHOD.POST, context.getMethod());
        assertEquals(true, CollectionPropsInjector.checkCollection(context));
    }

    /**
     *
     */
    @Test
    public void testCheckCollectionRoot() {
        var context = createRequest("/", "PUT");

        assertEquals(TYPE.ROOT, context.getType());
        assertEquals(METHOD.PUT, context.getMethod());
        assertEquals(false, CollectionPropsInjector.checkCollection(context));
    }

    private MongoRequest createRequest(String requestPath, String httpMethod) {
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setRequestPath(requestPath);
        exchange.setRequestMethod(new HttpString(httpMethod));
        return MongoRequest.init(exchange, "/", "*");
    }

}
