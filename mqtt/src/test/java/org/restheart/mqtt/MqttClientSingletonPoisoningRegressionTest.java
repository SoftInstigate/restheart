package org.restheart.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/**
 * Regression test for the {@code MqttClientSingletonHolder} class-initialization poisoning
 * defect: the private {@link MqttClientSingleton} constructor used to throw
 * {@code IllegalStateException} when {@code getInstance()} was called before {@code init(...)}.
 * Because that throw happened inside a static initializer (the holder idiom), the JVM marked
 * {@code MqttClientSingletonHolder} erroneous permanently, so every later call to
 * {@code getInstance()} in that classloader failed with {@code NoClassDefFoundError} instead of
 * the original, actionable exception - even after a subsequent, valid {@code init(...)} call.
 * <p>
 * {@code MqttClientSingleton} is a JVM-wide singleton (held both in a holder class and mirrored
 * into system properties), so by the time this test class runs, other test classes in the same
 * forked JVM will typically already have triggered - and safely survived - the holder's static
 * initializer. Simply calling {@code getInstance()} here would therefore not exercise the "first
 * ever access happens before init()" scenario that actually reproduces the defect: the holder
 * class would already be initialized from an earlier test.
 * </p>
 * <p>
 * To make this test deterministic regardless of what other tests ran before it - and therefore
 * to give it real teeth against a regression - it loads a completely fresh copy of
 * {@code MqttClientSingleton} (and its dependency {@code MqttConfig}) into an isolated
 * {@link ClassLoader} for every test method, guaranteeing that the holder's static initializer
 * has never run in that loader before. All interaction with the isolated copy goes through
 * reflection, since the isolated class is a distinct {@code Class} object from the one used
 * elsewhere in this test suite.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttClientSingletonPoisoningRegressionTest {

    /**
     * A classloader that loads every {@code org.restheart.mqtt.*} class fresh (bypassing
     * parent delegation and any already-loaded copy), while delegating everything else
     * (JDK, HiveMQ client, SLF4J, ...) to the parent classloader. This forces the static
     * initializer of {@code MqttClientSingletonHolder} to run again, from scratch, every
     * time an instance of this loader is used.
     */
    private static final class IsolatingClassLoader extends ClassLoader {

        private final ClassLoader delegate;

        IsolatingClassLoader(ClassLoader delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> alreadyLoaded = findLoadedClass(name);
                if (alreadyLoaded != null) {
                    if (resolve) {
                        resolveClass(alreadyLoaded);
                    }
                    return alreadyLoaded;
                }

                if (name.startsWith("org.restheart.mqtt.")) {
                    Class<?> freshlyDefined = findClass(name);
                    if (resolve) {
                        resolveClass(freshlyDefined);
                    }
                    return freshlyDefined;
                }

                return super.loadClass(name, resolve);
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            final String resourcePath = name.replace('.', '/') + ".class";
            try (InputStream in = delegate.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                final byte[] bytecode = in.readAllBytes();
                return defineClass(name, bytecode, 0, bytecode.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    /**
     * Builds a minimal, valid {@code MqttConfig} instance using only classes loaded through
     * the given isolated classloader, entirely via reflection.
     */
    private static Object buildMinimalConfig(ClassLoader isolated) throws Exception {
        Class<?> builderClass = Class.forName("org.restheart.mqtt.MqttConfig$Builder", true, isolated);
        Object builder = builderClass.getDeclaredConstructor().newInstance();

        builder = builderClass.getMethod("brokerUrl", String.class).invoke(builder, "tcp://localhost:1883");
        builder = builderClass.getMethod("protocolVersion", int.class).invoke(builder, 3);
        builder = builderClass.getMethod("keepAliveSeconds", int.class).invoke(builder, 60);
        builder = builderClass.getMethod("connectTimeoutSeconds", int.class).invoke(builder, 5);

        return builderClass.getMethod("build").invoke(builder);
    }

    /**
     * Reproduces the exact defect described in the regression report: with the throw restored
     * in the constructor, the very first call to {@code getInstance()} - made here before any
     * {@code init(...)} - poisons {@code MqttClientSingletonHolder} for the rest of the isolated
     * classloader's lifetime, so a subsequent, valid {@code init(...)} can never make
     * {@code getInstance()} usable again.
     * <p>
     * With the fix (constructor never throws), all three steps below succeed, in this order,
     * in the same isolated classloader:
     * </p>
     * <ol>
     *   <li>{@code getInstance()} before any {@code init(...)} returns a non-null instance and
     *       does not throw;</li>
     *   <li>{@code getClient()} on that instance still throws {@code IllegalStateException}
     *       naming {@code mqtt-client}, because configuration is genuinely required for that
     *       method;</li>
     *   <li>{@code init(...)} with a valid configuration, followed by {@code getInstance()},
     *       still works and returns the very same instance obtained in step 1.</li>
     * </ol>
     */
    @Test
    void testGetInstanceBeforeInitDoesNotPoisonTheHolderClass() throws Exception {
        // MqttClientSingletonHolder mirrors its INSTANCE into System properties, keyed by the
        // *string* "org.restheart.mqtt.MqttClientSingleton", to unify the singleton across
        // classloaders in plugin environments. That key is shared with the "real"
        // MqttClientSingleton used by every other test in this JVM, even though our isolated
        // copy is a distinct Class object: an incompatible value left behind by another test
        // would make our isolated holder's cast fail. Save and clear it before the test, then
        // restore it afterwards so the rest of the suite is unaffected.
        final String sysPropKey = "org.restheart.mqtt.MqttClientSingleton";
        final Object previousSysPropValue = System.getProperties().remove(sysPropKey);
        try {
            ClassLoader isolated = new IsolatingClassLoader(Thread.currentThread().getContextClassLoader());
            Class<?> singletonClass = Class.forName("org.restheart.mqtt.MqttClientSingleton", true, isolated);
            Class<?> configClass = Class.forName("org.restheart.mqtt.MqttConfig", true, isolated);

            Method getInstance = singletonClass.getMethod("getInstance");
            Method getClient = singletonClass.getMethod("getClient");
            Method init = singletonClass.getMethod("init", configClass);

            // step 1: getInstance() before any init() must return a non-null instance and not throw
            Object instance = assertDoesNotThrowReflectively(() -> getInstance.invoke(null));
            assertNotNull(instance, "getInstance() must never return null, even before init()");

            // step 2: getClient() still requires configuration and must throw IllegalStateException
            // naming the 'mqtt-client' plugin
            try {
                getClient.invoke(instance);
                fail("getClient() must throw IllegalStateException before init() has been called");
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getCause();
                assertInstanceOf(IllegalStateException.class, cause);
                assertTrue(cause.getMessage().contains("mqtt-client"),
                    "exception message must name the 'mqtt-client' plugin, was: " + cause.getMessage());
            }

            // step 3: a subsequent, valid init() must not be poisoned by step 1; getInstance() must
            // keep returning the same instance
            Object config = buildMinimalConfig(isolated);
            init.invoke(null, config);

            Object instanceAfterInit = assertDoesNotThrowReflectively(() -> getInstance.invoke(null));
            assertSame(instance, instanceAfterInit,
                "getInstance() must keep returning the same instance after init()");

            Method isInitialized = singletonClass.getMethod("isInitialized");
            assertEquals(Boolean.TRUE, isInitialized.invoke(null));
        } finally {
            if (previousSysPropValue != null) {
                System.getProperties().put(sysPropKey, previousSysPropValue);
            } else {
                System.getProperties().remove(sysPropKey);
            }
        }
    }

    @FunctionalInterface
    private interface ReflectiveInvocation {
        Object invoke() throws Exception;
    }

    private static Object assertDoesNotThrowReflectively(ReflectiveInvocation invocation) throws Exception {
        try {
            return invocation.invoke();
        } catch (InvocationTargetException ite) {
            throw new AssertionError("unexpected exception: " + ite.getCause(), ite.getCause());
        }
    }
}
