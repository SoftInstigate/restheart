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

import java.util.List;


/**
 * The syntax tree of an ACL predicate, as far as the boolean structure and the atoms go.
 *
 * <p>Undertow's {@code Predicate} is a composed function: it answers a request and says nothing
 * about why. Deciding what a catalog listing should contain needs the opposite — the atoms, one by
 * one, so that the ones resolvable before a call happens can be evaluated and the others left
 * undetermined. Hence a tree of our own.
 *
 * <p>It carries no semantics: {@link PredicateSyntax} builds it, {@code ListingEvaluator} gives it
 * meaning.
 */
public sealed interface PredicateExpression {

    /** {@code a and b}. */
    record And(PredicateExpression left, PredicateExpression right) implements PredicateExpression {
    }

    /** {@code a or b}. */
    record Or(PredicateExpression left, PredicateExpression right) implements PredicateExpression {
    }

    /** {@code not a}. */
    record Not(PredicateExpression operand) implements PredicateExpression {
    }

    /** The literals {@code true} and {@code false}, which the language has. */
    record Literal(boolean value) implements PredicateExpression {
    }

    /**
     * One predicate invocation with its arguments — {@code path('/orders')},
     * {@code equals(%{q,x}, 'y')}, {@code secure}.
     *
     * @param name the predicate name, as written
     * @param args its arguments, in order
     * @param text the atom as it appears in the source, for diagnostics
     */
    record Atom(String name, List<Arg> args, String text) implements PredicateExpression {

        /**
         * The values of the argument called {@code name}.
         *
         * <p>When none carries that name, the ones written without a name are taken instead, all of
         * them and in order: the language lets the default parameter be given positionally, and an
         * array-valued one be spread — {@code equals(a, b)} is {@code equals(value={a, b})}.
         */
        public List<String> valuesOf(String name) {
            return args.stream()
                    .filter(a -> name.equals(a.name()))
                    .findFirst()
                    .map(Arg::values)
                    .orElseGet(() -> unnamed().stream().flatMap(a -> a.values().stream()).toList());
        }

        /** The arguments written without a name, in order. */
        public List<Arg> unnamed() {
            return args.stream().filter(a -> a.name() == null).toList();
        }

        /** Every value of every argument, whatever its name. */
        public List<String> allValues() {
            return args.stream().flatMap(a -> a.values().stream()).toList();
        }
    }

    /**
     * One argument of an atom.
     *
     * @param name   its name, or {@code null} when written without one
     * @param values its values — more than one only for the language's {@code {a, b}} array form
     * @param quoted whether each value was written quoted, in the same order as {@code values}: a
     *               quoted {@code '%{q,x}'} is a literal, an unquoted one is an exchange attribute,
     *               and the difference decides the atom's class
     */
    record Arg(String name, List<String> values, List<Boolean> quoted) {

        public Arg(String name, List<String> values, List<Boolean> quoted) {
            this.name = name;
            this.values = List.copyOf(values);
            this.quoted = List.copyOf(quoted);
        }

        /** Whether the value at {@code i} was written quoted. */
        public boolean isQuoted(int i) {
            return i < quoted.size() && quoted.get(i);
        }
    }
}
