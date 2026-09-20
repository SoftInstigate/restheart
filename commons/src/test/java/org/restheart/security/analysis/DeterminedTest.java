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
 * =========================LICENSE_END==================================
 */
package org.restheart.security.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeterminedTest {

    @Test
    void aLiteralIsKnown() {
        assertTrue(Determined.isKnown("gold", true));
        assertTrue(Determined.isKnown("42", false));
    }

    @Test
    void theCandidateResourceIsKnown() {
        assertTrue(Determined.isKnown("%{RELATIVE_PATH}", false));
        assertTrue(Determined.isKnown("%m", false));
    }

    @Test
    void theSessionIsKnown() {
        assertTrue(Determined.isKnown("%u", false));
        assertTrue(Determined.isKnown("@user.plan", false));
        assertTrue(Determined.isKnown("@roles", false));
    }

    /** There is no call yet to read them from. */
    @Test
    void whatBelongsToTheCallIsNot() {
        assertFalse(Determined.isKnown("%{q,page}", false));
        assertFalse(Determined.isKnown("%b", false));
        assertFalse(Determined.isKnown("@qparams['id']", false));
        assertFalse(Determined.isKnown("@request.body.owner", false));
    }

    /** Quoting makes it a literal whatever it looks like. */
    @Test
    void aQuotedAttributeIsJustText() {
        assertTrue(Determined.isKnown("%{q,page}", true));
    }
}
