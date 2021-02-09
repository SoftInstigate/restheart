# RESTHeart native image with GraalVM

## build native image

> GraalVM required version: >= 21.0.0 (https://www.graalvm.org/downloads/)

Build image for local OS

```bash
$ mvn clean package -Pnative
```

Build Linux image

```bash
$ docker run -it --rm \
    -v "$PWD":/opt/app  \
    -v "$HOME"/.m2:/root/.m2 \
    softinstigate/graalvm-maven clean package -Pnative
```

native-image arguments are defined in file `core/src/main/resources/META-INF/native-image/org.restheart/restheart/native-image.properties`

## start native image

Start RESTHeart

```bash
$ ./core/target/restheart-native core/etc/restheart.yml -e core/etc/uber.properties
```

## Generate native-image build configuration

Start RESTHeart with test configuration and the `native-image-agent`

```bash
$ mvn clean package
$ cp test-plugins/target/restheart-test-plugins.jar core/target/plugins
$ java -agentlib:native-image-agent=config-merge-dir=core/src/main/resources/META-INF/native-image/org.restheart/restheart/ -jar core/target/restheart.jar core/etc/test/restheart.yml
```

Execute tests, this makes the `native-image-agent` collecting all needed configuration

```bash
$ cd core
$ mvn surefire:test -DskipITs=false -Dtest=\*IT
```

Stop restheart. this makes the [Assisted Configuration of Native Image Builds](https://github.com/oracle/graal/blob/master/substratevm/BuildConfiguration.md#assisted-configuration-of-native-image-builds) being updated.

The generated configuration are merged into the existing ones in directory `core/src/main/resources/META-INF/native-image/org.restheart/restheart`

> some files need to be manually edited. For instance, remove all references to classes of packages `org.restheart.test`, `org.graalvm` (keep `org.graalvm.polyglot.Value`) and `com.oracle.truffle` from `reflect-config.json`


The following fields must be configured with `allowWrite: true`

```json
{
  "name":"io.undertow.security.impl.BasicAuthenticationMechanism",
  "fields":[{"name":"identityManager", "allowWrite" : true}]
},
{
  "name":"graphql.schema.GraphQLScalarType",
  "fields":[{"name":"coercing", "allowWrite":true}]
},
{
  "name":"io.github.classgraph.ScanResultObject",
  "allDeclaredFields":true,
  "fields":[{"name":"scanResult", "allowWrite":true}]
}
```

## issues

### runtime error due to jboss-threads required by xnio

see:

- https://github.com/SoftInstigate/graalvm-undertow-issue
- https://github.com/oracle/graal/issues/3020
- https://issues.redhat.com/browse/UNDERTOW-1811

fixed by remove unsafe calls and disable jboss enhanced executor on native build: https://github.com/SoftInstigate/graalvm-undertow-issue/commit/ad36bd2d5b5397800f3494613e7fecc22615beab

### build fails due to ClassNotFoundException from mongodb-driver

resolved adding the following dependencies in native profile:

```xml
<dependency>
  <groupId>com.github.jnr</groupId>
  <artifactId>jnr-unixsocket</artifactId>
  <version>0.18</version> <!-- check version at https://github.com/mongodb/mongo-java-driver/blob/master/build.gradle -->
</dependency>
<dependency>
  <groupId>org.mongodb</groupId>
  <artifactId>mongodb-crypt</artifactId>
  <version>1.1.0-beta1</version> <!-- check version at https://github.com/mongodb/mongo-java-driver/blob/master/build.gradle -->
</dependency>
<dependency>
  <groupId>org.xerial.snappy</groupId>
  <artifactId>snappy-java</artifactId>
  <version>1.1.4</version> <!-- check version at https://github.com/mongodb/mongo-java-driver/blob/master/build.gradle -->
</dependency>
<dependency>
  <groupId>com.github.luben</groupId>
  <artifactId>zstd-jni</artifactId>
  <version>1.4.5-1</version>
</dependency>
</dependencies>
```
