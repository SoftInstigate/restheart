package org.restheart.graal;

/**
 * this is used by PluginsScanner to determine if scanning is occurring at build
 * or run time. When running with java, Bootstrapper calls
 * NativeImageBuildTimeChecker.atRunting() making the variable bt=false. When
 * building with native-image, the class is initialized at build time (due to
 * native-image.properties that sets PluginScanner to be initialized at build
 * time and tranistively NativeImageBuildTimeChecker, since PluginScanner
 * references it) making bt=false
 */
public class NativeImageBuildTimeChecker {
    private static boolean bt = true;

    public static boolean isBuildTime() {
        return bt;
    }

    public static void atRuntime() {
        bt = false;
    }
}
