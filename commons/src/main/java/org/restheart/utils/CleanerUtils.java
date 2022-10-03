package org.restheart.utils;

import java.lang.ref.Cleaner;

public class CleanerUtils {
    private static CleanerUtils instance = null;
    private Cleaner cleaner;

    private CleanerUtils() {
        this.cleaner = Cleaner.create();
    }

    public static CleanerUtils get() {
        if (instance == null) {
            instance = new CleanerUtils();
        }
        return instance;
    }

    public Cleaner cleaner() {
        return cleaner;
    }
}