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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.restheart.security.analysis.PredicateExpression.And;
import org.restheart.security.analysis.PredicateExpression.Atom;
import org.restheart.security.analysis.PredicateExpression.Not;
import org.restheart.security.analysis.PredicateExpression.Or;

class PredicateSyntaxTest {

    @Test
    void anAtomWithoutArgumentsNeedsNoBrackets() {
        var parsed = PredicateSyntax.parse("secure");

        assertEquals(new Atom("secure", java.util.List.of(), "secure"), parsed);
    }

    @Test
    void bothBracketFormsAreTheSameAtom() {
        var round = assertInstanceOf(Atom.class, PredicateSyntax.parse("path('/orders')"));
        var square = assertInstanceOf(Atom.class, PredicateSyntax.parse("path['/orders']"));

        assertEquals(round.name(), square.name());
        assertEquals(round.args(), square.args());
    }

    @Test
    void notBindsTighterThanAndWhichBindsTighterThanOr() {
        var parsed = PredicateSyntax.parse("not a and b or c");

        var or = assertInstanceOf(Or.class, parsed);
        var and = assertInstanceOf(And.class, or.left());

        assertInstanceOf(Not.class, and.left());
        assertEquals("c", assertInstanceOf(Atom.class, or.right()).name());
    }

    @Test
    void parenthesesOverrideThePrecedence() {
        var parsed = PredicateSyntax.parse("a and (b or c)");

        assertInstanceOf(Or.class, assertInstanceOf(And.class, parsed).right());
    }

    /**
     * The tokenizer must not see an operator inside a quoted value, or a permission on a path that
     * happens to contain one would be read as a different predicate altogether.
     */
    @Test
    void anOperatorInsideAQuotedValueIsJustText() {
        var atom = assertInstanceOf(Atom.class, PredicateSyntax.parse("path('/and/or/not')"));

        assertEquals("/and/or/not", atom.args().get(0).values().get(0));
    }

    @Test
    void namedAndUnnamedArgumentsAreToldApart() {
        var atom = assertInstanceOf(Atom.class,
                PredicateSyntax.parse("regex(pattern='/a.*', value=%{RELATIVE_PATH}, full-match=true)"));

        assertEquals("/a.*", atom.valuesOf("pattern").get(0));
        assertEquals("%{RELATIVE_PATH}", atom.valuesOf("value").get(0));
        assertEquals("true", atom.valuesOf("full-match").get(0));
    }

    @Test
    void anArrayIsOneArgumentWithSeveralValues() {
        var atom = assertInstanceOf(Atom.class, PredicateSyntax.parse("method(value={GET, POST})"));

        assertEquals(java.util.List.of("GET", "POST"), atom.valuesOf("value"));
    }

    /** An exchange attribute keeps its braces, commas included: {@code %{q,page}} is one token. */
    @Test
    void anExchangeAttributeSurvivesItsCommas() {
        var atom = assertInstanceOf(Atom.class, PredicateSyntax.parse("equals(%{q,page}, '1')"));

        assertEquals(java.util.List.of("%{q,page}", "1"), atom.args().stream()
                .flatMap(a -> a.values().stream()).toList());
    }

    /**
     * Square brackets are the language's own argument syntax, so an ACL variable indexed with them
     * has to be read as one token — this is what made a permission with {@code @qparams} fail to
     * parse before (#742).
     */
    @Test
    void anAclVariableKeepsItsIndex() {
        var atom = assertInstanceOf(Atom.class, PredicateSyntax.parse("equals(@qparams['trader'], 'bob')"));

        assertEquals("@qparams['trader']", atom.args().get(0).values().get(0));
        assertEquals("bob", atom.args().get(1).values().get(0));
    }

    @Test
    void aQuotedValueIsMarkedAsSuch() {
        var atom = assertInstanceOf(Atom.class, PredicateSyntax.parse("equals('%{q,x}', %{q,x})"));

        assertTrue(atom.args().get(0).isQuoted(0));
        assertTrue(!atom.args().get(1).isQuoted(0));
    }

    @Test
    void aMissingBracketIsAnError() {
        assertThrows(PredicateSyntax.SyntaxException.class, () -> PredicateSyntax.parse("path('/x'"));
    }

    @Test
    void trailingRubbishIsAnError() {
        assertThrows(PredicateSyntax.SyntaxException.class, () -> PredicateSyntax.parse("path('/x') )"));
    }
}
