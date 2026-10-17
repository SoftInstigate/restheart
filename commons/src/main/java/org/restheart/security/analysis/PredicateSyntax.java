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

import java.util.ArrayList;
import java.util.List;

import org.restheart.security.analysis.PredicateExpression.And;
import org.restheart.security.analysis.PredicateExpression.Arg;
import org.restheart.security.analysis.PredicateExpression.Atom;
import org.restheart.security.analysis.PredicateExpression.Literal;
import org.restheart.security.analysis.PredicateExpression.Not;
import org.restheart.security.analysis.PredicateExpression.Or;

/**
 * Reads an ACL predicate into a {@link PredicateExpression}.
 *
 * <p>The grammar is Undertow's, and this parser follows its tokenizer's rules so that the two agree
 * on what a token is — quoting with {@code '} or {@code "} with {@code \} escaping the delimiter,
 * {@code %{...}} and {@code ${...}} as single tokens whatever they contain, {@code -} as an ordinary
 * word character. Undertow's own parser cannot be reused: it is package private and it returns a
 * built {@code Predicate}, which is the thing we need to avoid.
 *
 * <p>It also reads the {@code @} variables, which Undertow never sees — by the time the predicate
 * reaches its parser they have been substituted with values. Here they must survive to be
 * classified, so {@code @qparams['id']} is one token, brackets included.
 *
 * <p>What is parsed is the boolean structure and the atoms. Everything inside an atom's argument
 * list stays as text: an argument is an exchange attribute, a variable or a literal, and telling
 * them apart is a question of meaning, not of syntax.
 */
public final class PredicateSyntax {

    private PredicateSyntax() {
    }

    /** Thrown when a predicate cannot be read. Its message names the position. */
    public static class SyntaxException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SyntaxException(String message) {
            super(message);
        }
    }

    /**
     * @param predicate the predicate as written in the permission, variables included
     * @return its syntax tree
     * @throws SyntaxException if it cannot be read
     */
    public static PredicateExpression parse(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            throw new SyntaxException("empty predicate");
        }

        var tokens = tokenize(predicate);
        var parser = new Parser(tokens);
        var expression = parser.or();

        if (!parser.done()) {
            throw new SyntaxException("unexpected '" + parser.peek().text() + "' at " + parser.peek().position());
        }

        return expression;
    }

    // ---------------------------------------------------------------- tokens

    private record Token(String text, boolean quoted, int position) {

        boolean is(String s) {
            return !quoted && text.equals(s);
        }

        boolean isWord(String s) {
            return !quoted && text.equalsIgnoreCase(s);
        }

        boolean isSpecial() {
            return !quoted && text.length() == 1 && "()[]{},=".indexOf(text.charAt(0)) >= 0;
        }
    }

    /**
     * Splits the source into words, quoted words and the language's special characters.
     *
     * <p>Three regions suspend the special characters, because inside them they are data:
     * a quoted string, an exchange attribute written {@code %{...}} or {@code ${...}}, and the
     * index of a variable written {@code @name[...]} or {@code @name(...)}.
     */
    private static List<Token> tokenize(String s) {
        var tokens = new ArrayList<Token>();
        var current = new StringBuilder();
        var start = 0;
        var i = 0;

        while (i < s.length()) {
            var c = s.charAt(i);

            if (c == '\'' || c == '"') {
                if (!current.isEmpty()) {
                    tokens.add(new Token(current.toString(), false, start));
                    current.setLength(0);
                }
                i = readQuoted(s, i, tokens);
                continue;
            }

            if ((c == '%' || c == '$') && i + 1 < s.length() && s.charAt(i + 1) == '{') {
                if (!current.isEmpty()) {
                    tokens.add(new Token(current.toString(), false, start));
                    current.setLength(0);
                }
                i = readUntil(s, i, '}', tokens);
                continue;
            }

            if (c == '@') {
                if (!current.isEmpty()) {
                    tokens.add(new Token(current.toString(), false, start));
                    current.setLength(0);
                }
                i = readVariable(s, i, tokens);
                continue;
            }

            if (Character.isWhitespace(c)) {
                if (!current.isEmpty()) {
                    tokens.add(new Token(current.toString(), false, start));
                    current.setLength(0);
                }
                i++;
                continue;
            }

            if ("()[]{},=".indexOf(c) >= 0) {
                if (!current.isEmpty()) {
                    tokens.add(new Token(current.toString(), false, start));
                    current.setLength(0);
                }
                tokens.add(new Token(String.valueOf(c), false, i));
                i++;
                continue;
            }

            if (current.isEmpty()) {
                start = i;
            }
            current.append(c);
            i++;
        }

        if (!current.isEmpty()) {
            tokens.add(new Token(current.toString(), false, start));
        }

        return tokens;
    }

    /** Reads a quoted string, honouring {@code \} before the delimiter. Returns the next position. */
    private static int readQuoted(String s, int from, List<Token> tokens) {
        var delimiter = s.charAt(from);
        var value = new StringBuilder();
        var i = from + 1;

        while (i < s.length()) {
            var c = s.charAt(i);

            if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == delimiter) {
                value.append(delimiter);
                i += 2;
                continue;
            }

            if (c == delimiter) {
                tokens.add(new Token(value.toString(), true, from));
                return i + 1;
            }

            value.append(c);
            i++;
        }

        throw new SyntaxException("unterminated string at " + from);
    }

    /** Reads {@code %{...}} / {@code ${...}} whole, closing character included. */
    private static int readUntil(String s, int from, char closing, List<Token> tokens) {
        var end = s.indexOf(closing, from);

        if (end < 0) {
            throw new SyntaxException("unterminated '" + s.charAt(from) + "{' at " + from);
        }

        tokens.add(new Token(s.substring(from, end + 1), false, from));

        return end + 1;
    }

    /**
     * Reads an ACL variable: {@code @user}, {@code @user.profile.name}, {@code @qparams['id']},
     * {@code @rnd(256)}. The bracketed form is the reason this cannot be left to the ordinary word
     * rule — square brackets are the language's own argument syntax.
     */
    private static int readVariable(String s, int from, List<Token> tokens) {
        var i = from + 1;

        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) {
            i++;
        }

        if (i < s.length() && (s.charAt(i) == '[' || s.charAt(i) == '(')) {
            var closing = s.charAt(i) == '[' ? ']' : ')';
            var end = s.indexOf(closing, i);

            if (end < 0) {
                throw new SyntaxException("unterminated variable index at " + from);
            }

            i = end + 1;
        }

        tokens.add(new Token(s.substring(from, i), false, from));

        return i;
    }

    // ---------------------------------------------------------------- parser

    /**
     * Recursive descent, with Undertow's precedence: {@code not} binds tighter than {@code and},
     * which binds tighter than {@code or}.
     */
    private static final class Parser {
        private final List<Token> tokens;
        private int at = 0;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        boolean done() {
            return at >= tokens.size();
        }

        Token peek() {
            if (done()) {
                throw new SyntaxException("unexpected end of predicate");
            }
            return tokens.get(at);
        }

        Token next() {
            var token = peek();
            at++;
            return token;
        }

        boolean lookingAt(String word) {
            return !done() && peek().isWord(word);
        }

        PredicateExpression or() {
            var left = and();

            while (lookingAt("or")) {
                next();
                left = new Or(left, and());
            }

            return left;
        }

        PredicateExpression and() {
            var left = unary();

            while (lookingAt("and")) {
                next();
                left = new And(left, unary());
            }

            return left;
        }

        PredicateExpression unary() {
            if (lookingAt("not")) {
                next();
                return new Not(unary());
            }

            return primary();
        }

        PredicateExpression primary() {
            var token = peek();

            if (token.is("(")) {
                next();
                var inner = or();

                if (done() || !peek().is(")")) {
                    throw new SyntaxException("missing ')' at " + token.position());
                }

                next();
                return inner;
            }

            if (token.isWord("true")) {
                next();
                return new Literal(true);
            }

            if (token.isWord("false")) {
                next();
                return new Literal(false);
            }

            return atom();
        }

        PredicateExpression atom() {
            var name = next();

            if (name.isSpecial()) {
                throw new SyntaxException("unexpected '" + name.text() + "' at " + name.position());
            }

            if (done() || !(peek().is("[") || peek().is("("))) {
                return new Atom(name.text(), List.of(), name.text());
            }

            var opening = next();
            var closing = opening.is("[") ? "]" : ")";
            var args = new ArrayList<Arg>();
            var text = new StringBuilder(name.text()).append(opening.text());

            while (!done() && !peek().is(closing)) {
                args.add(argument(text));

                if (!done() && peek().is(",")) {
                    next();
                    text.append(", ");
                }
            }

            if (done()) {
                throw new SyntaxException("missing '" + closing + "' at " + opening.position());
            }

            next();
            text.append(closing);

            return new Atom(name.text(), args, text.toString());
        }

        Arg argument(StringBuilder text) {
            String name = null;
            var first = next();

            if (!done() && peek().is("=")) {
                next();
                name = first.text();
                text.append(name).append('=');
                first = next();
            }

            if (first.is("{")) {
                var values = new ArrayList<String>();
                var quoted = new ArrayList<Boolean>();
                text.append('{');

                while (!done() && !peek().is("}")) {
                    var value = next();
                    values.add(value.text());
                    quoted.add(value.quoted());
                    text.append(value.text());

                    if (!done() && peek().is(",")) {
                        next();
                        text.append(", ");
                    }
                }

                if (done()) {
                    throw new SyntaxException("missing '}' at " + first.position());
                }

                next();
                text.append('}');

                return new Arg(name, values, quoted);
            }

            text.append(first.text());

            return new Arg(name, List.of(first.text()), List.of(first.quoted()));
        }
    }
}
