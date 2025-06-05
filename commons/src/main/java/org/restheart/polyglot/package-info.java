/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
/**
 * Polyglot programming support for RESTHeart using GraalVM.
 * 
 * <p>This package provides infrastructure for executing plugins and services written
 * in multiple programming languages through GraalVM's polyglot capabilities. It enables
 * RESTHeart to support JavaScript, Python, Ruby, R, and other GraalVM-supported languages
 * for plugin development.</p>
 * 
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.restheart.polyglot.NodeQueue} - Queue implementation for managing
 *       polyglot execution contexts and tasks</li>
 * </ul>
 * 
 * <h2>Supported Languages</h2>
 * <p>Through GraalVM, RESTHeart can execute plugins written in:</p>
 * <ul>
 *   <li>JavaScript (including Node.js compatibility)</li>
 *   <li>Python</li>
 *   <li>Ruby</li>
 *   <li>R</li>
 *   <li>Java (via interop)</li>
 *   <li>LLVM-based languages</li>
 * </ul>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li>Multi-language plugin support</li>
 *   <li>Sandboxed execution environments</li>
 *   <li>Cross-language interoperability</li>
 *   <li>Efficient context management</li>
 *   <li>Thread-safe execution</li>
 * </ul>
 * 
 * <h2>Architecture</h2>
 * <p>The polyglot support is built on GraalVM's Truffle framework, which provides:</p>
 * <ul>
 *   <li>High-performance language implementations</li>
 *   <li>Language-agnostic APIs for embedding</li>
 *   <li>Security boundaries between host and guest languages</li>
 *   <li>Resource management and limits</li>
 * </ul>
 * 
 * <h2>Usage Patterns</h2>
 * <p>Polyglot plugins can be used for:</p>
 * <ul>
 *   <li>Rapid prototyping of services</li>
 *   <li>Integration with existing scripts</li>
 *   <li>Leveraging language-specific libraries</li>
 *   <li>Dynamic business logic implementation</li>
 * </ul>
 * 
 * <h2>Performance Considerations</h2>
 * <p>While polyglot execution provides flexibility, consider:</p>
 * <ul>
 *   <li>Initial context creation overhead</li>
 *   <li>Cross-language boundary crossing costs</li>
 *   <li>Memory usage of language runtimes</li>
 *   <li>JIT compilation warm-up time</li>
 * </ul>
 * 
 * <h2>Security</h2>
 * <p>Polyglot execution includes security features:</p>
 * <ul>
 *   <li>Sandboxed execution environments</li>
 *   <li>Resource limits (CPU, memory, I/O)</li>
 *   <li>Access control to host capabilities</li>
 *   <li>Isolated contexts per plugin</li>
 * </ul>
 * 
 * <p>This package enables RESTHeart to be a truly polyglot platform, allowing developers
 * to choose the best language for each specific task while maintaining a unified
 * plugin architecture.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
package org.restheart.polyglot;