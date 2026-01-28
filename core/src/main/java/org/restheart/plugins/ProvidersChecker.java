/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import static org.restheart.configuration.Utils.getOrDefault;

import org.restheart.Bootstrapper;
import org.slf4j.Logger;

/**
 * Utility class for validating provider dependencies and checking for circular dependencies.
 * 
 * This class provides methods to validate that all provider dependencies can be resolved
 * and that there are no circular dependencies in the provider dependency graph. It ensures
 * that plugins with @Inject annotations have access to the required providers and that
 * those providers are properly enabled and configured.
 * 
 * <p>
 * The validation process includes:
 * <ul>
 * <li>Checking that all required providers exist and are enabled</li>
 * <li>Validating type compatibility between providers and injection points</li>
 * <li>Detecting and preventing circular dependencies between providers</li>
 * <li>Building and analyzing the provider dependency graph</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <strong>Note:</strong> LOGGER is passed as an argument to static methods rather than
 * being a class field because adding a LOGGER field breaks GraalVM native image compilation.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Provider
 * @see Inject
 * @see PluginDescriptor
 * @see FieldInjectionDescriptor
 */
public class ProvidersChecker {
    /**
     * Filters the list of providers to return only those that are enabled.
     * 
     * This method checks each provider's enabled state based on its default configuration
     * and any runtime configuration overrides. Disabled providers are logged and excluded
     * from the returned list.
     * 
     * @param LOGGER the logger instance for reporting disabled providers
     * @param providers the list of all provider descriptors to filter
     * @return a list containing only the enabled provider descriptors
     */
    private static List<PluginDescriptor> enabledProviders(Logger LOGGER, List<PluginDescriptor> providers) {
        return providers.stream()
            .filter(p -> p != null)
            .peek(p ->  { if (!enabled(p)) LOGGER.info("Provider {} disabled", p.name()); })
            .filter(p -> enabled(p))
            .collect(Collectors.toList());
    }

    /**
     * Determines if a plugin is enabled based on its default setting and configuration.
     * 
     * This method checks the plugin's enabledByDefault setting and any configuration
     * overrides to determine the final enabled state.
     * 
     * @param plugin the plugin descriptor to check
     * @return true if the plugin is enabled, false otherwise
     */
    private static boolean enabled(PluginDescriptor plugin) {
        Map<String, Object> pluginConf = getOrDefault(Bootstrapper.getConfiguration(), plugin.name(), null, true);
        return PluginRecord.isEnabled(plugin.enabled(), pluginConf);
    }

    /**
     * Removes providers from the graph that have invalid dependencies.
     * 
     * This method identifies and removes providers that depend on non-existent providers,
     * disabled providers, or providers with incompatible types. It performs type checking
     * to ensure that the provided object type is assignable to the injection field type.
     * 
     * @param LOGGER the logger instance for reporting dependency issues
     * @param providersGraph the mutable graph of provider dependencies to modify
     */
    private static void removeIfWrongDependency(Logger LOGGER, MutableGraph<PluginDescriptor> providersGraph) {
        var toRemove = new ArrayList<PluginDescriptor>();
        providersGraph.nodes().forEach(thisProvider -> {
            thisProvider.injections().stream()
                .filter(i -> i instanceof FieldInjectionDescriptor a)
                .map(i -> (FieldInjectionDescriptor) i)
                .forEach(i -> {
                    var otherProviderName = (String )i.annotationParams().get(0).getValue();
                    var otherProvider = providerDescriptorFromName(otherProviderName);

                    if (otherProvider == null) {
                        LOGGER.error("Provider {} disabled: no provider found for @Inject(\"{}\")", thisProvider.name(), otherProviderName);
                        toRemove.add(thisProvider);
                    } else if (!enabled(otherProvider)) {
                        LOGGER.error("Provider {} disabled: the provider for @Inject(\"{}\") is disabled", thisProvider.name(), otherProvider.name());
                        toRemove.add(thisProvider);
                    } else {
                        // check provided class vs annotated class
                        var providedType = PluginsFactory.providersTypes().get(otherProviderName);
                        var fieldType = i.clazz();

                        if (!fieldType.isAssignableFrom(providedType)) {
                            LOGGER.error("Plugin {} disabled: the type of the provider for @Inject(\"{}\") is {} but the type of the annotated field {} is {}", thisProvider.name(), otherProviderName, providedType, i.field(), fieldType);
                            toRemove.add(thisProvider);
                        }
                }
            });
        });

        toRemove.stream().forEach(providersGraph::removeNode);
    }

    /**
     * Removes providers from the graph that are involved in circular dependencies.
     * 
     * This method analyzes the provider dependency graph to detect cycles and removes
     * providers that participate in circular dependency chains. Each provider is checked
     * by examining its reachable nodes and testing for cycles in the induced subgraph.
     * 
     * @param LOGGER the logger instance for reporting circular dependency issues
     * @param providersGraph the mutable graph of provider dependencies to modify
     */
    private static void removeIfCircularDependency(Logger LOGGER, MutableGraph<PluginDescriptor> providersGraph) {
        var toRemove = new ArrayList<PluginDescriptor>();
        providersGraph.nodes().stream().forEach(provider -> {
            var reachableNodes = Graphs.reachableNodes(providersGraph, provider);
            var subGraph = Graphs.inducedSubgraph(providersGraph, reachableNodes);

            if (Graphs.hasCycle(subGraph)) {
                LOGGER.error("Provider {} disabled due to circular dependency", provider.name());
                toRemove.add(provider);
            }
        });
        toRemove.stream().forEach(providersGraph::removeNode);
    }

    /**
     * Validates provider dependencies and returns the set of valid providers.
     * 
     * This method builds a directed graph of provider dependencies and performs validation
     * to ensure that all dependencies can be resolved without circular references. It
     * iteratively removes providers with invalid dependencies until the graph stabilizes.
     * 
     * <p>
     * The validation process:
     * <ol>
     * <li>Creates a directed graph with enabled providers as nodes</li>
     * <li>Adds edges representing dependency relationships</li>
     * <li>Removes providers with missing, disabled, or type-incompatible dependencies</li>
     * <li>Removes providers involved in circular dependencies</li>
     * <li>Repeats until no more changes occur</li>
     * </ol>
     * </p>
     * 
     * @param LOGGER the logger instance for reporting validation issues
     * @param providers the list of all provider descriptors to validate
     * @return a set of provider descriptors that have valid, resolvable dependencies
     */
    static Set<PluginDescriptor> validProviders(Logger LOGGER, List<PluginDescriptor> providers) {
        MutableGraph<PluginDescriptor> providersGraph = GraphBuilder.directed().allowsSelfLoops(true).build();

        // add nodes
        enabledProviders(LOGGER, providers).stream().forEach(providersGraph::addNode);

        // add edges
        for (var thisProvider: providers) {
            thisProvider.injections().stream()
                .filter(i -> i instanceof FieldInjectionDescriptor a)
                .map(i -> (FieldInjectionDescriptor) i)
                .forEach(i -> {
                    var otherProviderName = (String) i.annotationParams().get(0).getValue();
                    var otherProvider = providerDescriptorFromName(otherProviderName);

                    if (otherProvider != null) {
                        providersGraph.putEdge(thisProvider, otherProvider);
                    }
                });
        }

        // remove nodes that have disabled dependencies
        // keep removing until it finds wrong providers
        var count = providersGraph.edges().size();
        removeIfWrongDependency(LOGGER, providersGraph);
        // remove nodes with circular dependencies
        removeIfCircularDependency(LOGGER, providersGraph);
        int newCount = providersGraph.edges().size();
        while(newCount < count) {
            count = providersGraph.edges().size();
            removeIfWrongDependency(LOGGER, providersGraph);
            // remove nodes that have circular dependencies
            removeIfCircularDependency(LOGGER, providersGraph);
            newCount = providersGraph.edges().size();
        };

        return providersGraph.nodes();
    }

    /**
     * Finds a provider descriptor by its class name.
     * 
     * @param className the fully qualified class name of the provider to find
     * @return the provider descriptor with the matching class name, or null if not found
     */
    static PluginDescriptor providerDescriptorFromClass(String className) {
        return PluginsScanner.providers().stream().filter(p -> p.clazz().equals(className)).findFirst().orElse(null);
    }

    /**
     * Finds a provider descriptor by its plugin name.
     * 
     * @param name the plugin name of the provider to find
     * @return the provider descriptor with the matching name, or null if not found
     */
    static PluginDescriptor providerDescriptorFromName(String name) {
        return PluginsScanner.providers().stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }

    /**
     * Checks that all dependencies for a plugin can be resolved.
     * 
     * This method validates that each @Inject field in the plugin has a corresponding
     * provider that exists, is enabled, and is not the plugin itself (to prevent
     * self-dependency). This is used to determine if a plugin should be instantiated
     * or disabled due to unresolvable dependencies.
     * 
     * @param LOGGER the logger instance for reporting dependency issues
     * @param validProviders the set of validated provider descriptors available for injection
     * @param plugin the plugin descriptor to check dependencies for
     * @return true if all plugin dependencies can be resolved, false otherwise
     */
    static boolean checkDependencies(Logger LOGGER, Set<PluginDescriptor> validProviders, PluginDescriptor plugin) {
        // don't check disabled plugins
        if (!enabled(plugin)) {
            return true;
        }

        var ret = true;

        // check Field Injections that require Providers
        var injections = new ArrayList<FieldInjectionDescriptor>();

        plugin.injections().stream()
            .filter(i -> i instanceof FieldInjectionDescriptor fid)
            .map(i -> (FieldInjectionDescriptor) i)
            .forEach(injections::add);

        for (var injection : injections) {
            var providerName = injection.annotationParams().get(0).getValue();

            var _provider = validProviders.stream().filter(p -> p.name().equals(providerName)).findFirst();

            if (_provider.isEmpty()) {
                LOGGER.error("Plugin {} disabled: no provider found for @Inject(\"{}\")", plugin.name(), providerName);
                ret = false;
            } else if(_provider.get().clazz().equals(plugin.clazz())) {
                LOGGER.error("Provider {} disabled: it depends on itself via @Inject(\"{}\")", plugin.name(), providerName);
            } else {
                var provider = _provider.get();

                if (!enabled(provider)) {
                    LOGGER.error("Plugin {} disabled: the provider for @Inject(\"{}\") is disabled", plugin.name(), providerName);
                    return false;
                }
            }
        }

        return ret;
    }
}
