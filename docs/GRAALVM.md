# RESTHeart native image with GraalVM

## prerequisites

> GraalVM required version: >= 25-graal

```bash
sdk install java 25-graalce
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

## Testing the native image

To test the native image with integration tests, follow these steps:

1. Uncomment the test plugins dependency in `core/pom.xml` (marked with `<!-- uncomment to add test plugins to the native image -->`)

2. Build the native image:

```bash
./mvnw clean package -Pnative
```

3. Start RESTHeart with the test configuration:

```bash
./core/target/restheart -o core/src/test/resources/etc/conf-overrides.yml
```

4. Run the integration tests:

```bash
cd core
../mvnw test surefire:test -DskipITs=false -Dtest=\*IT
```

## How-tos

### Generate native-image build configuration

Run the integration tests with RESTHeart under the GraalVM `native-image-agent`, which records every class reached through reflection, JNI or resources.

1. Build, and start MongoDB with a replica set (here the official image on 27017):

```bash
./mvnw clean package -DskipTests
docker run -d --name mongo-agent -p 27017:27017 mongo:9.0 --replSet rs0
docker exec mongo-agent mongosh --quiet --eval 'rs.initiate({_id: "rs0", members: [{_id: 0, host: "localhost:27017"}]})'
cp test-plugins/target/restheart-test-plugins.jar core/target/plugins
```

2. Start RESTHeart with the agent, using a GraalVM JDK. Don't pass `--fork`: the forked process is a new JVM, without the agent. Write the output to a separate directory, not over the files in the repo:

```bash
cd core
$GRAALVM_HOME/bin/java -agentlib:native-image-agent=config-output-dir=/tmp/rh-agent \
  --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED \
  -jar target/restheart.jar -o src/test/resources/etc/conf-overrides.yml
```

3. In another shell, from the repository root, run the integration tests against it. The profiles turned off here are the ones that would start their own MongoDB and RESTHeart:

```bash
./mvnw verify -pl core -am -P-mongodb,-mongodb-classic,-start-server
```

4. Stop RESTHeart with Ctrl-C (or SIGTERM): the agent writes `/tmp/rh-agent/reachability-metadata.json` when the JVM exits.

5. Compare it with the `reachability-metadata.json` of each module and add what is missing to the module that owns the class:

- `org.restheart.security` to `security/src/main/resources/META-INF/native-image/org.restheart/restheart-security/`
- `org.restheart.mongodb` to `mongodb/src/main/resources/META-INF/native-image/org.restheart/restheart-mongodb/`
- `org.restheart.polyglot` to `polyglot/src/main/resources/META-INF/native-image/org.restheart/restheart-polyglot/`, `graphql` to `graphql/...`
- the rest to `core/` or `commons/`

Leave out `org.restheart.test`, `org.graalvm` (except `org.graalvm.polyglot.Value`) and `com.oracle.truffle`. The MongoDB driver and BSON jars ship their own metadata: a driver class the agent records is already covered when it is in their `META-INF/native-image/reflect-config.json`.

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