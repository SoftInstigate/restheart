package org.restheart.handlers;

import org.junit.Test;

import static org.junit.Assert.*;

public class TracingInstrumentationHandlerTest {

    @Test
    public void generateRandomTraceId() {
        for (int i=0; i<100; i++) {
            String traceId = TracingInstrumentationHandler.generateRandomTraceId();
            String regex = "[a-f0-9]+";
            assertTrue(traceId + "did not match " + regex, traceId.matches(regex));
            assertEquals(traceId + " did not have length 16!", 16, traceId.length());
        }
    }
}