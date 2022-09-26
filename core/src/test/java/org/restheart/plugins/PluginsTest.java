package org.restheart.plugins;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PluginsTest {
    private static ProvidersChecker checker;
    private static List<PluginDescriptor> descriptors;

    private static MockedStatic<PluginsScanner> mockedScanner;

    @BeforeClass
    public static void before() {
        descriptors = providerDescriptors();
        mockedScanner = mockPluginsScanner(descriptors);
        checker = new ProvidersChecker(providerRecords());
    }

    @AfterClass
    public static void after() {
        if (mockedScanner != null) {
            mockedScanner.close();
        }
    }

    @Test
    public void allSatisfiedDependencies() {
        var pdD_C = descriptors.stream().filter(d -> d.clazz().equals(ProviderD_C.class.getName())).findAny().get();
        Assert.assertTrue("check provider D_C is fine", checker.checkDependencies(pdD_C));
    }

    @Test
    public void missingDirectDependency() {
        var pdB = descriptors.stream().filter(d -> d.clazz().equals(ProviderB.class.getName())).findAny().get();
        Assert.assertFalse("check provider B is wrong due to missing direct dependency", checker.checkDependencies(pdB));
    }

    @Test
    public void missingTransitiveDependency() {
        var pdE_B = descriptors.stream().filter(d -> d.clazz().equals(ProviderE_B.class.getName())).findAny().get();
        Assert.assertFalse("check provider E_B is wrong (missing transitive dependency)", checker.checkDependencies(pdE_B));
    }

    private static MockedStatic<PluginsScanner> mockPluginsScanner(List<PluginDescriptor> providerDescriptors) {
        var scanner = mockStatic(PluginsScanner.class);
        scanner.when(PluginsScanner::providers).thenReturn(providerDescriptors);
        return scanner;
    }

    /**
     * * return the following set of provider descriptors
     * A -> C_A -> D_C, B, E->B
     *
     * B is invalid, because it depends on a not existing provider
     * @return
     */
    private static List<PluginDescriptor> providerDescriptors() {
        /*
         * PluginDescriptor
         *  ArrayList<InjectionDescriptor> injections
         *      ArrayList<AbstractMap.SimpleEntry<String, Object>> annotationParams
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

        var providerDescriptors = new ArrayList<PluginDescriptor>();

        providerDescriptors.add(new PluginDescriptor(ProviderA.class.getName(), new ArrayList<InjectionDescriptor>()));
        providerDescriptors.add(new PluginDescriptor(ProviderB.class.getName(), iB));
        providerDescriptors.add(new PluginDescriptor(ProviderC_A.class.getName(), iC_A));
        providerDescriptors.add(new PluginDescriptor(ProviderD_C.class.getName(), iD_C));
        providerDescriptors.add(new PluginDescriptor(ProviderE_B.class.getName(), iE_B));

        return providerDescriptors;
    }

    /**
     * return the following set of provider records
     * A -> C_A -> D_C, B, E->B
     * @return
     */
    private static Set<PluginRecord<Provider<?>>> providerRecords() {
        var providersRecords = new HashSet<PluginRecord<Provider<?>>>();
        providersRecords.add(new PluginRecord<Provider<?>>("a", "A", false, true, ProviderA.class.getName(), new ProviderA(), new HashMap<String, Object>()));
        providersRecords.add(new PluginRecord<Provider<?>>("b", "B", false, true, ProviderB.class.getName(), new ProviderB(), new HashMap<String, Object>()));
        providersRecords.add(new PluginRecord<Provider<?>>("c_a", "C_A", false, true, ProviderC_A.class.getName(), new ProviderC_A(), new HashMap<String, Object>()));
        providersRecords.add(new PluginRecord<Provider<?>>("d_c", "D_C", false, true, ProviderD_C.class.getName(), new ProviderD_C(), new HashMap<String, Object>()));
        providersRecords.add(new PluginRecord<Provider<?>>("e_b", "E_B", false, true, ProviderE_B.class.getName(), new ProviderE_B(), new HashMap<String, Object>()));
        return providersRecords;
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