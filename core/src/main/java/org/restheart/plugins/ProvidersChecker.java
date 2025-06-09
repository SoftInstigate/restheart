/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
 * NOTE: LOGGER is an argument of the class static methods
 * because adding a LOGGER field breaks native image compilation
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ProvidersChecker {
    private static List<PluginDescriptor> enabledProviders(Logger LOGGER, List<PluginDescriptor> providers) {
        return providers.stream()
            .filter(p -> p != null)
            .peek(p ->  { if (!enabled(p)) LOGGER.info("Provider {} disabled", p.name()); })
            .filter(p -> enabled(p))
            .collect(Collectors.toList());
    }

    /*
     * @return true if the plugin is actually enabled, taking into account enabledByDefault and configuration
     */
    private static boolean enabled(PluginDescriptor plugin) {
        Map<String, Object> pluginConf = getOrDefault(Bootstrapper.getConfiguration(), plugin.name(), null, true);
        return PluginRecord.isEnabled(plugin.enabled(), pluginConf);
    }

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
     * WIP
     * checks if there are cycles in the providers graph (mutual dependencies)
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

    static PluginDescriptor providerDescriptorFromClass(String className) {
        return PluginsScanner.providers().stream().filter(p -> p.clazz().equals(className)).findFirst().orElse(null);
    }

    static PluginDescriptor providerDescriptorFromName(String name) {
        return PluginsScanner.providers().stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }

    /**
     * checks that a Provider exists and is enabled for each plugin @Inject field
     *
     * @param plugin
     * @return true if all the plugin dependencies can be resolved
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
