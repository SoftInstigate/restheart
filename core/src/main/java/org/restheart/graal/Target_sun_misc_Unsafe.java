package org.restheart.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.lang.reflect.Field;
/**
 * see https://github.com/oracle/graal/issues/3020#issuecomment-768231442
 */
@TargetClass(value = sun.misc.Unsafe.class)
final class Target_sun_misc_Unsafe {

    @Substitute
    public Object staticFieldBase(Field f) {
        return null;
    }

    @Substitute
    public long staticFieldOffset(Field f) {
        return -1;
    }
}