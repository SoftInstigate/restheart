# RESTHeart native image with GraalVM

## prerequisites

> GraalVM required version: >= 22.1.0 Java 17 based (https://www.graalvm.org/downloads/)

Also install `native-image`

```bash
$ gu install native-image
```

## build native image

Build image for local OS

```bash
$ ./mvnw clean package -Pnative
```

Build Linux image

```bash
$ docker run -it --rm \
    -v "$PWD":/opt/app  \
    -v "$HOME"/.m2:/root/.m2 \
    softinstigate/graalvm-maven clean package -Pnative
```

native-image arguments are defined in file `core/src/main/resources/META-INF/native-image/org.restheart/restheart/native-image.properties`

### build linux based container

```bash
$ cd core
$ docker build -f Dockerfile.native . -t softinstigate/restheart:7.0.0-native -t softinstigate/restheart:latest-native
$ docker push softinstigate/restheart:7.0.0-native
$ docker push softinstigate/restheart:latest-native
```

## start native image

Start RESTHeart

```bash
$ ./core/target/restheart
```

## Generate native-image build configuration

Start RESTHeart with test configuration and the `native-image-agent`

```bash
$ ./mvnw clean package
$ cp test-plugins/target/restheart-test-plugins.jar core/target/plugins
$ java -agentlib:native-image-agent=config-merge-dir=core/src/main/resources/META-INF/native-image/org.restheart/restheart/ -jar core/target/restheart.jar core/etc/test/restheart.yml
```

Execute tests, this makes the `native-image-agent` collecting all needed configuration

```bash
$ cd core
$ ../mvnw test surefire:test -DskipITs=false -Dtest=\*IT
```

Stop restheart. this makes the [Assisted Configuration of Native Image Builds](https://github.com/oracle/graal/blob/master/substratevm/BuildConfiguration.md#assisted-configuration-of-native-image-builds) being updated.

The generated configuration are merged into the existing ones in directory `core/src/main/resources/META-INF/native-image/org.restheart/restheart`

*Some files need to be manually edited*

Remove all references to classes of packages `org.restheart.test`, `org.graalvm` (keep `org.graalvm.polyglot.Value`) and `com.oracle.truffle` from `reflect-config.json`

Also make sure to move:

- references to classes in package `org.restheart.security` to `security/src/main/resources/META-INF/native-image/org.restheart/restheart-security/reflect-config.json`

- references to classes in package `org.restheart.mongodb` to `mongodb/src/main/resources/META-INF/native-image/org.restheart/restheart-mongodb/reflect-config.json`

- references to classes in packages `org.restheart.ployglot`, `graphql` to `ployglot/src/main/resources/META-INF/native-image/org.restheart/restheart-ployglot/reflect-config.json`


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

#### Generate reflect configuration needed to access Java types from JavaScript

Run

```bash
$ java -cp core/target/restheart.jar org.restheart.graal.GenerateGraalvmReflectConfig
```

And add output to `commons/src/main/resources/META-INF/native-image/org.restheart/restheart-commons/reflect-config.json`

### allow restheart js plugins access java classes via reflection

restheart js plugins access java classes via reflection, the following utility generates the reflect-config entries to add to the native image's `reflect-config.json`

```bash
$ java -cp core/target/restheart.jar org.restheart.utils.GenerateGraalvmReflectConfig
```

it will print out the entries to add to `commons/src/main/resources/META-INF/native-image/org.restheart/restheart-commons/reflect-config.json`

## issues

### [FIXED] build fails due to ClassNotFoundException from mongodb-driver

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