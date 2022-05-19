package org.restheart.examples;

import org.apache.commons.lang3.RandomStringUtils;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

@RegisterPlugin(
        name = "randomStringService",
        description = "returns a random string",
        enabledByDefault = true,
        defaultURI = "/rndStr")
public class RandomStringService implements ByteArrayService {

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        if (request.isGet()) {
            var rnd = RandomStringUtils.randomAlphabetic(10);

            response.setContent(rnd.getBytes());
            response.setContentType("application/txt");
            response.setStatusCode(HttpStatus.SC_OK);
        } else {
            // Any other HTTP verb is a bad request
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
