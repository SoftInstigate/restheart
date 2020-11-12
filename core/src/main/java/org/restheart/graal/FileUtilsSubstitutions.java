package org.restheart.graal;

import com.oracle.svm.core.annotate.TargetClass;

import java.nio.file.Path;

import com.oracle.svm.core.annotate.Substitute;
import org.restheart.utils.FileUtils;

/**
 * TODO check https://www.graalvm.org/reference-manual/native-image/Limitations/#jni-java-native-interface
 * createPidFile uses LIBC class and JNI
 */
@TargetClass(FileUtils.class)
public final class FileUtilsSubstitutions {
    @Substitute
    public static void createPidFile(Path pidFile) {

    }
}


