# RESTHeart native image with GraalVM

## prerequisites

> GraalVM required version: >= 21.0.2-graal

```bash
sdk install java 21.0.2-graalce
```

## build native image

Build image for local OS

```bash
./mvnw clean package -Pnative -DskipTests
```

Build Linux image

```bash
docker run -it --rm \
    -v "$PWD":/opt/app  \
    -v "$HOME"/.m2:/root/.m2 \
    softinstigate/graalvm-maven \
    clean package -Pnative -DskipTests -Dnative.gc="--gc=G1"
```

native-image arguments are defined in file `core/src/main/resources/META-INF/native-image/org.restheart/restheart/native-image.properties`

__Note__: Linux needs to use the `G1` garbage collector. This is obtained by passing the `-Dnative.gc="--gc=G1"` property to maven.

### build linux based container

```bash
RH_VERSION=$( mvn help:evaluate -Dexpression=project.version -q -DforceStdout ) && echo version=$RH_VERSION, major version=${RH_VERSION%.*}
cd core
docker build -f Dockerfile.native . -t softinstigate/restheart:${RH_VERSION}-native -t softinstigate/restheart:latest-native -t softinstigate/restheart:${RH_VERSION%.*}-native
docker push softinstigate/restheart:${RH_VERSION}-native
docker push softinstigate/restheart:latest-native
docker push softinstigate/restheart:${RH_VERSION%.*}-native
```

## start native image

Start RESTHeart

```bash
./core/target/restheart
```

## How-tos

### Generate native-image build configuration

Start RESTHeart with test configuration and the `native-image-agent`

```bash
./mvnw clean package
cp test-plugins/target/restheart-test-plugins.jar core/target/plugins
java -agentlib:native-image-agent=config-merge-dir=core/src/main/resources/META-INF/native-image/org.restheart/restheart/ -jar core/target/restheart.jar -o core/src/test/resources/etc/conf-overrides.yml
```

Execute tests, this makes the `native-image-agent` collecting all needed configuration

```bash
cd core
../mvnw test surefire:test -DskipITs=false -Dtest=\*IT
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

### Generate reflect configuration needed to access Java types from JavaScript

Run

```bash
java -cp core/target/restheart.jar org.restheart.graal.GenerateGraalvmReflectConfig
```

And add output to `commons/src/main/resources/META-INF/native-image/org.restheart/restheart-commons/reflect-config.json`