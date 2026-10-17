/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.analysis;

/**
 * Three truth values, because a listing is composed before the call it describes exists.
 *
 * <p>{@link #UNDETERMINED} means "this could go either way once the call is made". It makes the
 * whole evaluation an upper bound: {@link #mayBeTrue()} never answers false for something that
 * could have been allowed, and at worst answers true for something that will be refused.
 */
public enum Truth {
    TRUE,
    FALSE,
    UNDETERMINED;

    public static Truth of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public Truth and(Truth other) {
        if (this == FALSE || other == FALSE) {
            return FALSE;
        }
        return this == TRUE && other == TRUE ? TRUE : UNDETERMINED;
    }

    public Truth or(Truth other) {
        if (this == TRUE || other == TRUE) {
            return TRUE;
        }
        return this == FALSE && other == FALSE ? FALSE : UNDETERMINED;
    }

    /**
     * Negation leaves {@link #UNDETERMINED} where it is — which is what keeps a negated unknown
     * from hiding anything, and why a non monotone predicate costs precision and not correctness.
     */
    public Truth not() {
        return switch (this) {
            case TRUE -> FALSE;
            case FALSE -> TRUE;
            case UNDETERMINED -> UNDETERMINED;
        };
    }

    /** Whether some call could satisfy this. The question a catalog listing actually asks. */
    public boolean mayBeTrue() {
        return this != FALSE;
    }
}
