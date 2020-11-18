package org.restheart.graal;

public class NativeImageBuildTimeChecker {
    private static boolean bt = true;

    public static boolean isBuildTime() {
        return bt;
    }

    public static void atRuntime() {
        bt = false;
    }
}
