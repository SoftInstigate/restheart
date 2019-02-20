/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package io.uiam.utils;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class LambdaUtils {
    /**
     * allows to throw Checked exception from a Consumer
     * @see https://www.baeldung.com/java-sneaky-throws
     * @param ex 
     */
    public static void throwsSneakyExcpetion(Throwable ex) {
        sneakyThrow(ex);
    }
    
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}
