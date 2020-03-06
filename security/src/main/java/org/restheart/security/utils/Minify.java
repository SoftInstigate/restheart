/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
/**
 * ---------------------- Minify.java 2015-10-04 ----------------------
 *
 * Copyright (c) 2015 Charles Bihis (www.whoischarles.com)
 *
 * This work is an adaptation of JSMin.java published by John Reilly which is a
 * translation from C to Java of jsmin.c published by Douglas Crockford.
 * Permission is hereby granted to use this Java version under the same
 * conditions as the original jsmin.c on which all of these derivatives are
 * based.
 *
 *
 *
 * --------------------- JSMin.java 2006-02-13 ---------------------
 *
 * Copyright (c) 2006 John Reilly (www.inconspicuous.org)
 *
 * This work is a translation from C to Java of jsmin.c published by Douglas
 * Crockford. Permission is hereby granted to use the Java version under the
 * same conditions as the jsmin.c on which it is based.
 *
 *
 *
 * ------------------ jsmin.c 2003-04-21 ------------------
 *
 * Copyright (c) 2002 Douglas Crockford (www.crockford.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
/**
 * Minify.java is written by Charles Bihis (www.whoischarles.com) and is adapted
 * from JSMin.java written by John Reilly (www.inconspicuous.org) which is
 * itself a translation of jsmin.c written by Douglas Crockford
 * (www.crockford.com).
 *
 * @see
 * <a href="http://www.unl.edu/ucomm/templatedependents/JSMin.java">http://www.unl.edu/ucomm/templatedependents/JSMin.java</a>
 * @see
 * <a href="http://www.crockford.com/javascript/jsmin.c">http://www.crockford.com/javascript/jsmin.c</a>
 */
public class Minify {

    private static final int EOF = -1;

    private PushbackInputStream in;
    private OutputStream out;
    private int currChar;
    private int nextChar;
    private int line;
    private int column;

    public Minify() {
        this.in = null;
        this.out = null;
    }

    /**
     * Minifies the input JSON string.
     *
     * Takes the input JSON string and deletes the characters which are
     * insignificant to JavaScipt. Comments will be removed, tabs will be
     * replaced with spaces, carriage returns will be replaced with line feeds,
     * and most spaces and line feeds will be removed. The result will be
     * returned.
     *
     * @param json The JSON string for which to minify
     * @return A minified, yet functionally identical, version of the input JSON
     * string
     */
    public String minify(String json) {
        InputStream ins = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        try {
            minify(ins, bout);
        } catch (IOException
                | UnterminatedCommentException
                | UnterminatedRegExpLiteralException
                | UnterminatedStringLiteralException e) {
            return null;
        }

        return bout.toString().trim();
    }

    /**
     * Takes an input stream to a JSON string and outputs minified JSON to the
     * output stream.
     *
     * Takes the input JSON via the input stream and deletes the characters
     * which are insignificant to JavaScript. Comments will be removed, tabs
     * will be replaced with spaaces, carriage returns will be replaced with
     * line feeds, and most spaces and line feeds will be removed. The result is
     * streamed to the output stream.
     *
     * @param in The <code>InputStream</code> from which to get the un-minified
     * JSON
     * @param out The <code>OutputStream</code> where the resulting minified
     * JSON will be streamed to
     * @throws IOException
     * @throws UnterminatedRegExpLiteralException
     * @throws UnterminatedCommentException
     * @throws UnterminatedStringLiteralException
     */
    public void minify(InputStream in, OutputStream out) throws IOException, UnterminatedRegExpLiteralException,
            UnterminatedCommentException,
            UnterminatedStringLiteralException {

        // Initialize
        this.in = new PushbackInputStream(in);
        this.out = out;
        this.line = 0;
        this.column = 0;
        currChar = '\n';
        action(Action.DELETE_NEXT);

        // Process input
        while (currChar != EOF) {
            switch (currChar) {

                case ' ':
                    if (isAlphanum(nextChar)) {
                        action(Action.OUTPUT_CURR);
                    } else {
                        action(Action.DELETE_CURR);
                    }
                    break;

                case '\n':
                    switch (nextChar) {
                        case '{':
                        case '[':
                        case '(':
                        case '+':
                        case '-':
                            action(Action.OUTPUT_CURR);
                            break;
                        case ' ':
                            action(Action.DELETE_NEXT);
                            break;
                        default:
                            if (isAlphanum(nextChar)) {
                                action(Action.OUTPUT_CURR);
                            } else {
                                action(Action.DELETE_CURR);
                            }
                    }
                    break;

                default:
                    switch (nextChar) {
                        case ' ':
                            if (isAlphanum(currChar)) {
                                action(Action.OUTPUT_CURR);
                                break;
                            }
                            action(Action.DELETE_NEXT);
                            break;
                        case '\n':
                            switch (currChar) {
                                case '}':
                                case ']':
                                case ')':
                                case '+':
                                case '-':
                                case '"':
                                case '\'':
                                    action(Action.OUTPUT_CURR);
                                    break;
                                default:
                                    if (isAlphanum(currChar)) {
                                        action(Action.OUTPUT_CURR);
                                    } else {
                                        action(Action.DELETE_NEXT);
                                    }
                            }
                            break;
                        default:
                            action(Action.OUTPUT_CURR);
                            break;
                    }
            }
        }
        out.flush();
    }

    /**
     * Process the current character with an appropriate action.
     *
     * The action that occurs is determined by the current character. The
     * options are:
     *
     * 1. Output currChar: output currChar, copy nextChar to currChar, get the
     * next character and save it to nextChar 2. Delete currChar: copy nextChar
     * to currChar, get the next character and save it to nextChar 3. Delete
     * nextChar: get the next character and save it to nextChar
     *
     * This method essentially treats a string as a single character. Also
     * recognizes regular expressions if they are preceded by '(', ',', or '='.
     *
     * @param action The action to perform
     * @throws IOException
     * @throws UnterminatedRegExpLiteralException
     * @throws UnterminatedCommentException
     * @throws UnterminatedStringLiteralException
     */
    private void action(Action action) throws IOException, UnterminatedRegExpLiteralException, UnterminatedCommentException,
            UnterminatedStringLiteralException {

        // Process action
        switch (action) {

            case OUTPUT_CURR:
                out.write(currChar);

            case DELETE_CURR:
                currChar = nextChar;

                if (currChar == '\'' || currChar == '"') {
                    for (;;) {
                        out.write(currChar);
                        currChar = get();
                        if (currChar == nextChar) {
                            break;
                        }
                        if (currChar <= '\n') {
                            throw new UnterminatedStringLiteralException(line,
                                    column);
                        }
                        if (currChar == '\\') {
                            out.write(currChar);
                            currChar = get();
                        }
                    }
                }

            case DELETE_NEXT:
                nextChar = next();
                if (nextChar == '/'
                        && (currChar == '(' || currChar == ',' || currChar == '=' || currChar == ':')) {
                    out.write(currChar);
                    out.write(nextChar);
                    for (;;) {
                        currChar = get();
                        if (currChar == '/') {
                            break;
                        } else if (currChar == '\\') {
                            out.write(currChar);
                            currChar = get();
                        } else if (currChar <= '\n') {
                            throw new UnterminatedRegExpLiteralException(line,
                                    column);
                        }
                        out.write(currChar);
                    }
                    nextChar = next();
                }
        }
    }

    /**
     * Determines whether a given character is a letter, digit, underscore,
     * dollar sign, or non-ASCII character.
     *
     * @param c The character to compare
     * @return True if the character is a letter, digit, underscore, dollar
     * sign, or non-ASCII character. False otherwise.
     */
    private boolean isAlphanum(int c) {
        return ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')
                || c == '_' || c == '$' || c == '\\' || c > 126);
    }

    /**
     * Returns the next character from the input stream.
     *
     * Will pop the next character from the input stack. If the character is a
     * control character, translate it to a space or line feed.
     *
     * @return The next character from the input stream
     * @throws IOException
     */
    private int get() throws IOException {
        int c = in.read();

        if (c == '\n') {
            line++;
            column = 0;
        } else {
            column++;
        }

        if (c >= ' ' || c == '\n' || c == EOF) {
            return c;
        }

        if (c == '\r') {
            column = 0;
            return '\n';
        }

        return ' ';
    }

    /**
     * Returns the next character from the input stream without popping it from
     * the stack.
     *
     * @return The next character from the input stream
     * @throws IOException
     */
    private int peek() throws IOException {
        int lookaheadChar = in.read();
        in.unread(lookaheadChar);
        return lookaheadChar;
    }

    /**
     * Get the next character from the input stream, excluding comments.
     *
     * Will read from the input stream via the <code>get()</code> method. Will
     * exclude characters that are part of comments.  <code>peek()</code> is used
     * to se if a '/' is followed by a '/' or a '*' for the purpose of
     * identifying comments.
     *
     * @return The next character from the input stream, excluding characters
     * from comments
     * @throws IOException
     * @throws UnterminatedCommentException
     */
    private int next() throws IOException, UnterminatedCommentException {
        int c = get();

        if (c == '/') {
            switch (peek()) {

                case '/':
                    for (;;) {
                        c = get();
                        if (c <= '\n') {
                            return c;
                        }
                    }

                case '*':
                    get();
                    for (;;) {
                        switch (get()) {
                            case '*':
                                if (peek() == '/') {
                                    get();
                                    return ' ';
                                }
                                break;
                            case EOF:
                                throw new UnterminatedCommentException(line, column);
                        }
                    }

                default:
                    return c;
            }

        }
        return c;
    }

    public static enum Action {
        OUTPUT_CURR, DELETE_CURR, DELETE_NEXT
    }

    /**
     * Exception to be thrown when an unterminated comment appears in the input.
     */
    public static class UnterminatedCommentException extends Exception {
        private static final long serialVersionUID = -459033305952553241L;

        public UnterminatedCommentException(int line, int column) {
            super("Unterminated comment at line " + line + " and column " + column);
        }
    }

    /**
     * Exception to be thrown when an unterminated string literal appears in the
     * input.
     */
    public static class UnterminatedStringLiteralException extends Exception {
        private static final long serialVersionUID = -3267464275514481782L;

        public UnterminatedStringLiteralException(int line, int column) {
            super("Unterminated string literal at line " + line + " and column " + column);
        }
    }

    /**
     * Exception to be thrown when an unterminated regular expression literal
     * appears in the input.
     */
    public static class UnterminatedRegExpLiteralException extends Exception {
        private static final long serialVersionUID = -998596173973803801L;

        public UnterminatedRegExpLiteralException(int line, int column) {
            super("Unterminated regular expression at line " + line + " and column " + column);
        }
    }
}
