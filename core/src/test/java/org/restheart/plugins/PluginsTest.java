/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.restheart.Bootstrapper;
import org.restheart.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginsFactory.class);
    private static List<PluginDescriptor> descriptors;

    private static MockedStatic<PluginsScanner> mockedScanner;
    private static MockedStatic<Bootstrapper> mockedBootstrapper;
    private static MockedStatic<PluginsFactory> mockedPluginsFactory;

    @BeforeAll
    public static void before() {
        mockedBootstrapper = mockBootstrapper();
        descriptors = providerDescriptors();
        mockedScanner = mockPluginsScanner(descriptors);
        mockedPluginsFactory = mockPluginsFactory();
    }

    @AfterAll
    public static void after() {
        if (mockedScanner != null) {
            mockedScanner.close();
        }

        if (mockedBootstrapper != null) {
            mockedBootstrapper.close();
        }

        if (mockedPluginsFactory != null) {
            mockedPluginsFactory.close();
        }
    }

    @Test
    public void allSatisfiedDependencies() {
        var pdD_C = descriptors.stream().filter(d -> d.clazz().equals(ProviderD_C.class.getName())).findAny().get();
        var vps = ProvidersChecker.validProviders(LOGGER, PluginsScanner.providers());
        assertTrue(ProvidersChecker.checkDependencies(LOGGER, vps, pdD_C), "check provider D_C is fine");
    }

    @Test
    public void missingDirectDependency() {
        var pdB = descriptors.stream().filter(d -> d.clazz().equals(ProviderB.class.getName())).findAny().get();
        var vps = ProvidersChecker.validProviders(LOGGER, PluginsScanner.providers());
        assertFalse(ProvidersChecker.checkDependencies(LOGGER, vps, pdB),
                "check provider B is wrong due to missing direct dependency");
    }

    @Test
    public void missingTransitiveDependency() {
        var pdE_B = descriptors.stream().filter(d -> d.clazz().equals(ProviderE_B.class.getName())).findAny().get();
        var vps = ProvidersChecker.validProviders(LOGGER, PluginsScanner.providers());
        assertFalse(ProvidersChecker.checkDependencies(LOGGER, vps, pdE_B),
                "check provider E_B is wrong (missing transitive dependency)");
    }

    @Test
    public void validProviders() {
        var vps = ProvidersChecker.validProviders(LOGGER, PluginsScanner.providers());

        // valid providers
        assertTrue(vps.contains(
                descriptors.stream().filter(d -> d.clazz().equals(ProviderA.class.getName())).findAny().get()));
        assertTrue(vps.contains(
                descriptors.stream().filter(d -> d.clazz().equals(ProviderC_A.class.getName())).findAny().get()));
        assertTrue(vps.contains(
                descriptors.stream().filter(d -> d.clazz().equals(ProviderD_C.class.getName())).findAny().get()));
        assertTrue(vps.contains(
                descriptors.stream().filter(d -> d.clazz().equals(ProviderE_B.class.getName())).findAny().get()));

        // invalid providers
        assertFalse(vps.contains(
                descriptors.stream().filter(d -> d.clazz().equals(ProviderB.class.getName())).findAny().get()));
        assertFalse(vps.contains(
                descriptors.stream().filter(d -> d.clazz().equals(ProviderWT.class.getName())).findAny().get()));
        assertFalse(vps.contains(
                descriptors.stream().filter(d -> d.clazz().equals(ProviderSelf.class.getName())).findAny().get()));
        assertFalse(vps.contains(
                descriptors.stream().filter(d -> d.clazz().equals(ProviderC1.class.getName())).findAny().get()));
        assertFalse(vps.contains(
                descriptors.stream().filter(d -> d.clazz().equals(ProviderC2.class.getName())).findAny().get()));
    }

    private static MockedStatic<PluginsScanner> mockPluginsScanner(List<PluginDescriptor> providerDescriptors) {
        var scanner = mockStatic(PluginsScanner.class);
        scanner.when(PluginsScanner::providers).thenReturn(providerDescriptors);
        return scanner;
    }

    private static MockedStatic<Bootstrapper> mockBootstrapper() {
        var bootrapper = mockStatic(Bootstrapper.class);
        bootrapper.when(Bootstrapper::getConfiguration).thenReturn(Configuration.Builder.build(true, true));
        return bootrapper;
    }

    private static MockedStatic<PluginsFactory> mockPluginsFactory() {
        var providersTypes = new HashMap<String, Class<?>>();
        providersTypes.put("a", new ProviderA().rawType());
        providersTypes.put("b", new ProviderB().rawType());
        providersTypes.put("c_a", new ProviderC_A().rawType());
        providersTypes.put("d_c", new ProviderD_C().rawType());
        providersTypes.put("e_b", new ProviderE_B().rawType());
        providersTypes.put("wrongType", new ProviderWT().rawType());
        providersTypes.put("self", new ProviderSelf().rawType());
        providersTypes.put("c1", new ProviderC1().rawType());
        providersTypes.put("c2", new ProviderC2().rawType());

        var pluginsFactory = mockStatic(PluginsFactory.class);
        pluginsFactory.when(PluginsFactory::providersTypes).thenReturn(providersTypes);
        return pluginsFactory;
    }

    /**
     * * return the following set of provider descriptors
     * A -> C_A -> D_C, B, E->B, WT->A, SELF->SELF, C1->C2->C1
     *
     * B is invalid, because it depends on a not existing provider
     * WT is invalid, because the injected field is of the wrong type
     * SELF is invalid, because it depends on itself
     * C1 and C2 are invalid, due to circular dependency
     *
     * @return
     */
    private static List<PluginDescriptor> providerDescriptors() {
        /*
         * PluginDescriptor
         * ArrayList<InjectionDescriptor> injections
         * ArrayList<AbstractMap.SimpleEntry<String, Object>> annotationParams
         */

        var iC_A = new ArrayList<InjectionDescriptor>();
        var apC_A = new ArrayList<AbstractMap.SimpleEntry<String, Object>>();
        apC_A.add(new AbstractMap.SimpleEntry<String, Object>("value", "a"));
        iC_A.add(new FieldInjectionDescriptor("s", String.class, apC_A, 1));

        var iD_C = new ArrayList<InjectionDescriptor>();
        var apD_C = new ArrayList<AbstractMap.SimpleEntry<String, Object>>();
        apD_C.add(new AbstractMap.SimpleEntry<String, Object>("value", "c_a"));
        iD_C.add(new FieldInjectionDescriptor("s", String.class, apD_C, 2));

        var iB = new ArrayList<InjectionDescriptor>();
        var apB = new ArrayList<AbstractMap.SimpleEntry<String, Object>>();
        apB.add(new AbstractMap.SimpleEntry<String, Object>("value", "notExisting"));
        iB.add(new FieldInjectionDescriptor("s", String.class, apB, 3));

        var iE_B = new ArrayList<InjectionDescriptor>();
        var apE_B = new ArrayList<AbstractMap.SimpleEntry<String, Object>>();
        apE_B.add(new AbstractMap.SimpleEntry<String, Object>("value", "b"));
        iE_B.add(new FieldInjectionDescriptor("s", String.class, apE_B, 4));

        var iWT = new ArrayList<InjectionDescriptor>();
        var apWT = new ArrayList<AbstractMap.SimpleEntry<String, Object>>();
        apWT.add(new AbstractMap.SimpleEntry<String, Object>("value", "a"));
        iWT.add(new FieldInjectionDescriptor("s", Integer.class, apWT, 5));

        var iSelf = new ArrayList<InjectionDescriptor>();
        var apSelf = new ArrayList<AbstractMap.SimpleEntry<String, Object>>();
        apSelf.add(new AbstractMap.SimpleEntry<String, Object>("value", "self"));
        iSelf.add(new FieldInjectionDescriptor("s", String.class, apSelf, 6));

        var iC1 = new ArrayList<InjectionDescriptor>();
        var apC1 = new ArrayList<AbstractMap.SimpleEntry<String, Object>>();
        apC1.add(new AbstractMap.SimpleEntry<String, Object>("value", "c2"));
        iC1.add(new FieldInjectionDescriptor("s", String.class, apC1, 7));

        var iC2 = new ArrayList<InjectionDescriptor>();
        var apC2 = new ArrayList<AbstractMap.SimpleEntry<String, Object>>();
        apC2.add(new AbstractMap.SimpleEntry<String, Object>("value", "c1"));
        iC2.add(new FieldInjectionDescriptor("s", String.class, apC2, 8));

        var providerDescriptors = new ArrayList<PluginDescriptor>();

        providerDescriptors
                .add(new PluginDescriptor("a", ProviderA.class.getName(), true,
                        new ArrayList<InjectionDescriptor>()));
        providerDescriptors.add(new PluginDescriptor("b", ProviderB.class.getName(), true, iB));
        providerDescriptors.add(new PluginDescriptor("c_a", ProviderC_A.class.getName(), true, iC_A));
        providerDescriptors.add(new PluginDescriptor("d_c", ProviderD_C.class.getName(), true, iD_C));
        providerDescriptors.add(new PluginDescriptor("e_b", ProviderE_B.class.getName(), true, iE_B));
        providerDescriptors.add(new PluginDescriptor("wrongType", ProviderWT.class.getName(), true, iWT));
        providerDescriptors.add(new PluginDescriptor("self", ProviderSelf.class.getName(), true, iSelf));
        providerDescriptors.add(new PluginDescriptor("c1", ProviderC1.class.getName(), true, iC1));
        providerDescriptors.add(new PluginDescriptor("c2", ProviderC2.class.getName(), true, iC2));

        return providerDescriptors;
    }
}

// name=a
class ProviderA implements Provider<String> {
    @Override
    public String get(PluginRecord<?> caller) {
        return "A";
    }
}

// name=b
// this has an not existing dependency
class ProviderB implements Provider<String> {
    @Inject("notExisting")
    String s;

    @Override
    public String get(PluginRecord<?> caller) {
        return "foo";
    }
}

// name=c_a
class ProviderC_A implements Provider<String> {
    @Inject("a")
    String s;

    @Override
    public String get(PluginRecord<?> caller) {
        return s;
    }
}

// name=d_c
class ProviderD_C implements Provider<String> {
    @Inject("c_a")
    String s;

    @Override
    public String get(PluginRecord<?> caller) {
        return s;
    }
}

// name=e_b
class ProviderE_B implements Provider<String> {
    @Inject("b")
    String s;

    @Override
    public String get(PluginRecord<?> caller) {
        return s;
    }
}

// name=wrongType
class ProviderWT implements Provider<Integer> {
    @Inject("a") // a provides a String
    Integer n;

    @Override
    public Integer get(PluginRecord<?> caller) {
        return n;
    }
}

// name=self
class ProviderSelf implements Provider<String> {
    @Inject("self")
    String s;

    @Override
    public String get(PluginRecord<?> caller) {
        return s;
    }
}

// name=c1
class ProviderC1 implements Provider<String> {
    @Inject("c2")
    String s;

    @Override
    public String get(PluginRecord<?> caller) {
        return s;
    }
}

// name=c2
class ProviderC2 implements Provider<String> {
    @Inject("c1")
    String s;

    @Override
    public String get(PluginRecord<?> caller) {
        return s;
    }
}
