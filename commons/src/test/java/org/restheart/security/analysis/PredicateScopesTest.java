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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.restheart.security.EvaluationScope.Scope;

class PredicateScopesTest {

    @Test
    void undertowsPathAndMethodAreDetermined() {
        assertEquals(Optional.of(Scope.LISTING), PredicateScopes.of("path"));
        assertEquals(Optional.of(Scope.LISTING), PredicateScopes.of("path-template"));
        assertEquals(Optional.of(Scope.LISTING), PredicateScopes.of("method"));
    }

    @Test
    void theComparisonsTakeTheClassOfTheirArguments() {
        assertEquals(Optional.of(Scope.ARGUMENTS), PredicateScopes.of("equals"));
        assertEquals(Optional.of(Scope.ARGUMENTS), PredicateScopes.of("regex"));
    }

    /** It asks something of the request being made, not of the session composing the listing. */
    @Test
    void authRequiredBelongsToTheCall() {
        assertEquals(Optional.of(Scope.CALL), PredicateScopes.of("auth-required"));
    }

    @Test
    void aPredicateNobodyDeclaresIsOpaque() {
        assertTrue(PredicateScopes.of("is-gold-customer").isEmpty());
    }
}
