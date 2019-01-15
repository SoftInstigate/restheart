
package org.restheart.handlers.files;

import io.undertow.util.Headers;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Before;
import org.junit.Test;
import org.restheart.hal.Representation;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author mturatti
 */
public class PostBucketHandlerIT extends FileHandlerAbstractIT {

    String bucketUrl;

    public PostBucketHandlerIT() {
    }

    @Before
    public void init() throws Exception {
        bucketUrl = createBucket();
    }

    @Test
    public void testRequestIsOkWhenIsMultipart() throws Exception {
        Response response = adminExecutor.execute(Request.Post(bucketUrl)
                .body(buildMultipartResource()));

        this.check("Should return 200 OK", response, HttpStatus.SC_CREATED);
    }

    @Test
    public void testBadRequestWhenPostIsNotMultipart() throws Exception {
        Response response = adminExecutor
                .execute(Request.Post(bucketUrl)
                        .bodyString("{a:1}", halCT)
                        .addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

        this.check("Should return 400 BAD REQUEST", response, HttpStatus.SC_BAD_REQUEST);
    }

}
