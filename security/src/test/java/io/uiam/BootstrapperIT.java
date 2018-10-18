package io.uiam;

import io.uiam.Bootstrapper;

public class BootstrapperIT {

    //@Test
    public void testSingleStartStop() {
        Bootstrapper.startup((String) null);
        Bootstrapper.shutdown(null);
    }

    //@Test
    public void testMultipleStartStop() {
        Bootstrapper.startup((String) null);
        Bootstrapper.shutdown(null);
        Bootstrapper.startup((String) null);
        Bootstrapper.shutdown(null);
    }

}
