# RESTHeart native image with GraalVM

Work in progress.

We are able to build native image and start RESTHeart **without** plugins.

## build native image

```bash
$ mvn clean package -Pnative
```

native-image arguments are defined in file `core/src/main/resources/META-INF/native-image/org.restheart/restheart/native-image.properties`

## start native image

Must remove all plugins

```
$ mv core/target/plugins/* /tmp
```

Start RESTHeart

```
./core/target/restheart-native core/etc/graal.yml -e core/etc/dev.properties
```

`graal.yml` defines two services:

- `/anything` that proxies `https://httpbin.org/anything`
- `/` that serve the project directory as a static resource (shows LICENSE.txt as welcome file)


## Generate native-image configuration

Start RESTHeart with test configuration and the `native-image-agent`

```
$ mvn clean package
$ cp test-plugins/target/restheart-test-plugins.jar core/target/plugins
$ java -agentlib:native-image-agent=config-merge-dir=core/src/main/resources/META-INF/native-image/org.restheart/restheart/ -jar core/target/restheart.jar core/etc/test/restheart.yml
```

Execute tests, this makes the `native-image-agent` collecting all needed configuration

```
$ cd core
$ mvn -Dtest=karate.RunnerIT surefire:test
```

The generated configuration are merged into the existing ones in directory `core/src/main/resources/META-INF/native-image/org.restheart/restheart`

> some files need to be manually edited. For instance, remote all references to classes of package`org.restheart.test` and `org.graalvm` from `reflect-config.json`

## issues

### check bundled resources

See [substratevm/Resources.md](https://github.com/oracle/graal/blob/master/substratevm/Resources.md)

### RegisterPlugin annotation parameter (with default value) are null

for instance, the param priority for `DigestAuthMechanism` is null

```
 18:52:49.394 [main] DEBUG org.restheart.plugins.PluginsFactory - ******** REGISTER_PLUGIN_CLASS_NAME org.restheart.plugins.RegisterPlugin, ci public class org.restheart.security.plugins.mechanisms.DigestAuthMechanism implements org.restheart.plugins.security.AuthMechanism, param priority, value null
```

setting explicitly the value lead to ClassNotFoundException

```
 18:56:41.954 [main] ERROR org.restheart.Bootstrapper - Error instantiating plugins
 java.lang.IllegalArgumentException: Could not load class org.restheart.mongodb.handlers.changestreams.ChangeStreamsActivator : java.lang.ClassNotFoundException: org.restheart.mongodb.handlers.changestreams.ChangeStreamsActivator
	at io.github.classgraph.ScanResult.loadClass(ScanResult.java:1205)
	at io.github.classgraph.ScanResultObject.loadClass(ScanResultObject.java:215)
	at io.github.classgraph.ClassInfo.loadClass(ClassInfo.java:2705)
	at org.restheart.plugins.PluginsFactory.instantiatePlugin(PluginsFactory.java:360)
	at org.restheart.plugins.PluginsFactory.lambda$createPlugins$3(PluginsFactory.java:309)
	at java.util.ArrayList$ArrayListSpliterator.forEachRemaining(ArrayList.java:1655)
	at java.util.stream.ReferencePipeline$Head.forEachOrdered(ReferencePipeline.java:668)
	at org.restheart.plugins.PluginsFactory.createPlugins(PluginsFactory.java:292)
	at org.restheart.plugins.PluginsFactory.initializers(PluginsFactory.java:224)
	at org.restheart.plugins.PluginsRegistryImpl.getInitializers(PluginsRegistryImpl.java:188)
	at org.restheart.plugins.PluginsRegistryImpl.instantiateAll(PluginsRegistryImpl.java:82)
	at org.restheart.Bootstrapper.startServer(Bootstrapper.java:514)
	at org.restheart.Bootstrapper.run(Bootstrapper.java:256)
	at org.restheart.Bootstrapper.main(Bootstrapper.java:220)
```

A native image build time we get the following warnings:

```
WARNING: Could not resolve org.restheart.mongodb.MongoService for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.MongoServiceInitializer for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.hal.HALRepresentation for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.handlers.changestreams.ChangeStreamsActivator for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.handlers.metrics.MetricsInstrumentationInterceptor for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.handlers.sessions.TxnsActivator for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.interceptors.AddRequestProperties for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.interceptors.CollectionPropsInjector for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.interceptors.ContentSizeChecker for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.interceptors.DbPropsInjector for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.interceptors.JsonSchemaAfterWriteChecker for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.interceptors.JsonSchemaBeforeWriteChecker for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.interceptors.NamespacesResponseFlattener for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.services.CacheInvalidator for reflection configuration.
WARNING: Could not resolve org.restheart.mongodb.services.CsvLoader for reflection configuration.
WARNING: Could not resolve org.xnio.nio.NioXnioWorker$NioWorkerMetrics for reflection configuration.
```

### classpath not resolved

`native-image.properties` specifies the following classpath.

```
-cp mongodb/target/restheart-mongodb.jar:security/target/restheart-security.jar
```

but `-cp` is ingored with `-jar`

probably need to build uber-jar with shade plugin

see https://stackoverflow.com/questions/61212087/working-graalvm-native-image-command-line-with-custom-classpath

### Failure in building native image of `restheart-mongodb.jar` plugin

To reproduce

```
$ $GRAALVM_HOME/bin/native-image -cp core/target/restheart.jar:security/target/restheart-security.jar:mongodb/target/restheart-mongodb.jar org.restheart.Bootstrapper
```

error

```
Fatal error:java.lang.NoClassDefFoundError
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)
	at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
	at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:490)
	at java.base/java.util.concurrent.ForkJoinTask.getThrowableException(ForkJoinTask.java:603)
	at java.base/java.util.concurrent.ForkJoinTask.get(ForkJoinTask.java:1006)
	at com.oracle.svm.hosted.NativeImageGenerator.run(NativeImageGenerator.java:480)
	at com.oracle.svm.hosted.NativeImageGeneratorRunner.buildImage(NativeImageGeneratorRunner.java:349)
	at com.oracle.svm.hosted.NativeImageGeneratorRunner.build(NativeImageGeneratorRunner.java:508)
	at com.oracle.svm.hosted.NativeImageGeneratorRunner.main(NativeImageGeneratorRunner.java:114)
	at com.oracle.svm.hosted.NativeImageGeneratorRunner$JDK9Plus.main(NativeImageGeneratorRunner.java:537)
Caused by: java.lang.NoClassDefFoundError: jnr/unixsocket/UnixSocketAddress
```

Adding dependency leads to same error with ?? Mongo Crypto class

Adding `mongodb/target/restheart-mongodb.jar` leads to error.

### no version logged from native image

```
 10:41:54.204 [main] INFO  org.restheart.Bootstrapper - Version unknown, not packaged
```

### Need to downgrade org.jboss.xnio

Starting from version ??, `org.jboss.xnio` depends on `org.jboss.threads` version ??

This leads to following error at native image build time:

```
org.jboss.threads.EnhancedQueueExecutor uses unsupported Unsafe operations
```

`EnhancedQueueExecutor` uses `Unsafe` in static initializer. The error is with `unsafe.staticFieldBase(EnhancedQueueExecutor.class.getDeclaredField("sequence"));` at row 291

```
static {
        try {
            terminationWaitersOffset = unsafe.objectFieldOffset(EnhancedQueueExecutor.class.getDeclaredField("terminationWaiters"));

            queueSizeOffset = unsafe.objectFieldOffset(EnhancedQueueExecutor.class.getDeclaredField("queueSize"));

            peakThreadCountOffset = unsafe.objectFieldOffset(EnhancedQueueExecutor.class.getDeclaredField("peakThreadCount"));
            activeCountOffset = unsafe.objectFieldOffset(EnhancedQueueExecutor.class.getDeclaredField("activeCount"));
            peakQueueSizeOffset = unsafe.objectFieldOffset(EnhancedQueueExecutor.class.getDeclaredField("peakQueueSize"));

            sequenceBase = unsafe.staticFieldBase(EnhancedQueueExecutor.class.getDeclaredField("sequence"));
            sequenceOffset = unsafe.staticFieldOffset(EnhancedQueueExecutor.class.getDeclaredField("sequence"));
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }
```

### Fails to dynamically load plugins from plugins directory (RESOLVED)

> resolved adding to `PluginsFactory`

```
	this.scanResult = new ClassGraph()
			.disableModuleScanning()              // added for GraalVM
			.disableDirScanning()                 // added for GraalVM
			.disableNestedJarScanning()           // added for GraalVM
			.disableRuntimeInvisibleAnnotations() // added for GraalVM
```

RESTHeart uses [classgraph](https://github.com/classgraph/classgraph) to load the plugins jars form the plugins directory that heavily uses reflection.

GraalVM doesn't like reflection, and leads to the following error at runtime if the plugins directory contains any jar.

```
 11:53:38.816 [main] ERROR org.restheart.Bootstrapper - Linkage error instantiating plugins Check that all plugins were compiled against restheart-commons of correct version
 java.lang.ExceptionInInitializerError: null
	at com.oracle.svm.core.classinitialization.ClassInitializationInfo.initialize(ClassInitializationInfo.java:291)
	at org.restheart.plugins.PluginsRegistryImpl.instantiateAll(PluginsRegistryImpl.java:81)
	at org.restheart.Bootstrapper.startServer(Bootstrapper.java:514)
	at org.restheart.Bootstrapper.run(Bootstrapper.java:256)
	at org.restheart.Bootstrapper.main(Bootstrapper.java:220)
Caused by: io.github.classgraph.ClassGraphException: Uncaught exception during scan
	at io.github.classgraph.ClassGraph.scan(ClassGraph.java:1319)
	at io.github.classgraph.ClassGraph.scan(ClassGraph.java:1337)
	at io.github.classgraph.ClassGraph.scan(ClassGraph.java:1350)
	at org.restheart.plugins.PluginsFactory.<init>(PluginsFactory.java:155)
	at org.restheart.plugins.PluginsFactory.<clinit>(PluginsFactory.java:69)
	at com.oracle.svm.core.classinitialization.ClassInitializationInfo.invokeClassInitializer(ClassInitializationInfo.java:351)
	at com.oracle.svm.core.classinitialization.ClassInitializationInfo.initialize(ClassInitializationInfo.java:271)
	... 4 common frames omitted
Caused by: java.lang.IllegalArgumentException: Exception while invoking method "list"
	at nonapi.io.github.classgraph.utils.ReflectionUtils.invokeMethod(ReflectionUtils.java:268)
	at nonapi.io.github.classgraph.utils.ReflectionUtils.invokeMethod(ReflectionUtils.java:301)
	at io.github.classgraph.ModuleReaderProxy.list(ModuleReaderProxy.java:107)
	at io.github.classgraph.ClasspathElementModule.scanPaths(ClasspathElementModule.java:277)
	at io.github.classgraph.Scanner$5.processWorkUnit(Scanner.java:1026)
	at io.github.classgraph.Scanner$5.processWorkUnit(Scanner.java:1020)
	at nonapi.io.github.classgraph.concurrency.WorkQueue.runWorkLoop(WorkQueue.java:246)
	at nonapi.io.github.classgraph.concurrency.WorkQueue.runWorkQueue(WorkQueue.java:161)
	at io.github.classgraph.Scanner.processWorkUnits(Scanner.java:342)
	at io.github.classgraph.Scanner.openClasspathElementsThenScan(Scanner.java:1018)
	at io.github.classgraph.Scanner.call(Scanner.java:1078)
	at io.github.classgraph.Scanner.call(Scanner.java:78)
	at java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	at java.lang.Thread.run(Thread.java:834)
	at com.oracle.svm.core.thread.JavaThreads.threadStartRoutine(JavaThreads.java:517)
	at com.oracle.svm.core.posix.thread.PosixJavaThreads.pthreadStartRoutine(PosixJavaThreads.java:192)
Caused by: java.lang.reflect.InvocationTargetException: null
	at java.lang.reflect.Method.invoke(Method.java:566)
	at nonapi.io.github.classgraph.utils.ReflectionUtils.invokeMethod(ReflectionUtils.java:260)
	... 17 common frames omitted
Caused by: com.oracle.svm.core.jdk.UnsupportedFeatureError: Unsupported method jdk.internal.module.SystemModuleFinders$SystemImage.reader() is reachable
	at com.oracle.svm.core.util.VMError.unsupportedFeature(VMError.java:86)
	at jdk.internal.module.SystemModuleFinders$SystemImage.reader(SystemModuleFinders.java:385)
	at jdk.internal.module.SystemModuleFinders$ModuleContentSpliterator.<init>(SystemModuleFinders.java:508)
	at jdk.internal.module.SystemModuleFinders$SystemModuleReader.list(SystemModuleFinders.java:483)
	... 19 common frames omitted
```