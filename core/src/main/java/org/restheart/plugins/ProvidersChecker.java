/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
import java.util.Set;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ProvidersChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvidersChecker.class);

    private Set<PluginRecord<Provider<?>>> providers = null;

    ProvidersChecker(Set<PluginRecord<Provider<?>>> provides) {
        this.providers = provides;
    }

    /**
     * Providers are instantiated to perform type checking (by checkDependencies)
     */
    // private Set<PluginRecord<Provider<?>>> instantiateProvidersForChecking(ArrayList<PluginDescriptor> pluginDescriptors, Map<String, Map<String, Object>> confs) {
    //     var ret = new LinkedHashSet<PluginRecord<Provider<?>>>();

    //     pluginDescriptors.stream().forEachOrdered(plugin -> {
    //         try {
    //             var clazz = loadPluginClass(plugin);
    //             Plugin i;

    //             var name = name(clazz);
    //             var description = description(clazz);
    //             var secure = secure(clazz);
    //             var enabledByDefault = enabledByDefault(clazz);
    //             var conf = confs != null ? confs.get(name) : null;
    //             var enabled = PluginRecord.isEnabled(enabledByDefault, conf);

    //             if (enabled) {
    //                 i = instantiatePlugin(clazz, "Provider", name, conf);

    //                 var pr = new PluginRecord<Provider<?>>(name, description, secure, enabledByDefault, name, (Provider<?>) i, confs != null ? confs.get(name) : null);

    //                 if (pr.isEnabled()) {
    //                     ret.add(pr);
    //                 }
    //             }
    //         } catch (ClassNotFoundException | ConfigurationException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
    //             // error will be logged later by createPlugins
    //             LOGGER.trace("Error instantiating Provider {}: {}", plugin.clazz(), getRootException(e).getMessage(), e);
    //         }
    //     });

    //     return ret;
    // }

    private void providersGraph() {
        MutableGraph<Provider<?>> providersGraph = GraphBuilder.directed().allowsSelfLoops(false).build();

        PluginsScanner.PROVIDERS.stream()
            .map(p -> provider(p.clazz()))
            .filter(p -> p != null)
            .map(pd -> pd.getInstance())
            .forEach(providersGraph::addNode);

        for (var pd: PluginsScanner.PROVIDERS) {
            pd.injections().stream()
                .filter(i -> i instanceof FieldInjectionDescriptor a)
                .map(i -> (FieldInjectionDescriptor) i)
                .forEach(i -> {
                    var thisProvider = provider(pd.clazz()).getInstance();
                    var otherProviderName = i.annotationParams().get(0).getValue();
                    var _otherProvider = providers.stream().filter(p -> p.getName().equals(otherProviderName)).findFirst();

                    if (_otherProvider.isPresent()) {
                        providersGraph.putEdge(thisProvider, _otherProvider.get().getInstance());
                    } else {
                        providersGraph.removeNode(thisProvider);
                        // invalid provider
                    }
                });
        }

        var _aProviderDecriptor = PluginsScanner.PROVIDERS.stream().findAny().get();
        var aProvider = provider(_aProviderDecriptor.clazz()).getInstance();

        var reachableNodes = Graphs.reachableNodes(providersGraph, aProvider);

        var subGraph = Graphs.inducedSubgraph(providersGraph, reachableNodes);

        if (Graphs.hasCycle(subGraph)) {
            // circular dependency!!!!!!
        }
    }

    PluginRecord<Provider<?>> provider(String className) {
        return this.providers.stream().filter(p -> p.getClassName().equals(className)).findFirst().orElse(null);
    }

    /**
     * checks if the plugin dependencies can be resolved:
     * 1) checks that a Provider exists for each plugin @Inject field
     * 2) checks that the Provider type match the annotated field type
     *
     * @param plugin
     * @return true if all the plugin dependencies can be resolved
     */
    boolean checkDependencies(PluginDescriptor plugin) {
        var ret = true;

        // check Field Injections that require Providers
        var injections = new ArrayList<FieldInjectionDescriptor>();

        plugin.injections().stream()
            .filter(i -> i instanceof FieldInjectionDescriptor fid)
            .map(i -> (FieldInjectionDescriptor) i)
            .forEach(injections::add);

        for (var injection : injections) {
            var providerName = injection.annotationParams().get(0).getValue();

            var _provider = this.providers.stream().filter(p -> p.getName().equals(providerName)).findFirst();

            if (_provider.isEmpty()) {
                LOGGER.error("Plugin {} disabled: no provider found for @Inject(\"{}\")", plugin.clazz(), providerName);
                ret = false;
            } else if(_provider.get().getClassName().equals(plugin.clazz())) {
                LOGGER.error("Provider {} disabled: it depends on itself via @Inject(\"{}\")", plugin.clazz(), providerName);
            } else {
                var provider = _provider.get();

                var providerType = ((Provider<?>)provider.getInstance()).rawType().getName();
                var fieldType = injection.clazz().getName();

                if (!providerType.equals(fieldType)) {
                    LOGGER.error("Plugin {} disabled: the type of the provider for @Inject(\"{}\") is {} but the type of the annotated field {} is {}", plugin.clazz(), providerName, providerType, injection.field(), fieldType);
                    ret = false;
                }
            }
        }

        return ret;
    }
}
