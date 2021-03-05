/*-
 * ========================LICENSE_START=================================
 * restheart-core
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
package org.restheart.mongodb.handlers.files;

import io.undertow.util.Headers;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Before;
import org.junit.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class PostBucketHandlerIT extends FileHandlerAbstractIT {

    String bucketUrl;

    /**
     *
     */
    public PostBucketHandlerIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Before
    public void init() throws Exception {
        bucketUrl = createBucket();
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testRequestIsOkWhenIsMultipart() throws Exception {
        Response response = adminExecutor.execute(Request.Post(bucketUrl)
                .body(buildMultipartResource()));

        this.check("Should return 201 CREATED", response, HttpStatus.SC_CREATED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBadRequestWhenPostIsNotMultipart() throws Exception {
        Response response = adminExecutor
                .execute(Request.Post(bucketUrl)
                        .bodyString("{a:1}", halCT)
                        .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        this.check("Should return 400 BAD REQUEST", response, HttpStatus.SC_BAD_REQUEST);
    }

}
