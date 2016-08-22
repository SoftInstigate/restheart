package org.restheart.test.integration;

import org.junit.Ignore;
import org.junit.Test;
import org.restheart.Bootstrapper;

public class BootstrapperIT {

    @Ignore @Test
    public void testSingleStartStop() {
        Bootstrapper.startup((String) null);
        Bootstrapper.shutdown();
    }

    @Ignore @Test
    public void testMultipleStartStop() {
        Bootstrapper.startup((String) null);
        Bootstrapper.shutdown();
        Bootstrapper.startup((String) null);
        Bootstrapper.shutdown();
    }

}