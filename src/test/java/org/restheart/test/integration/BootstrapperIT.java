package org.restheart;

import org.junit.Test;

public class BootstrapperTest {

    @Test
    public void testSingleStartStop() {
        Bootstrapper.startup((String) null);
        Bootstrapper.shutdown();
    }

    @Test
    public void testMultipleStartStop() {
        Bootstrapper.startup((String) null);
        Bootstrapper.shutdown();
        Bootstrapper.startup((String) null);
        Bootstrapper.shutdown();
    }

}