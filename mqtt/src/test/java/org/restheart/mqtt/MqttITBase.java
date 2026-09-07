/*-
 * ========================LICENSE_START=================================
 * restheart-mqtt
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

package org.restheart.mqtt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for {@code mqtt} module integration tests, built on Testcontainers.
 * <p>
 * Starts a two-container topology - a Mosquitto broker and a RESTHeart instance with this
 * module's freshly built plugin jar mounted in - on a shared, isolated Docker network, mirroring
 * {@code mqtt/docker-compose.yml}.
 * </p>
 * <p>
 * <strong>The name deliberately does not end in {@code IT}.</strong> The failsafe plugin's
 * default {@code includes} pattern is {@code **&#47;*IT.java}, so a base class whose name ended
 * in {@code IT} would be collected and run as a test class in its own right, instead of merely
 * being extended by ones that are.
 * </p>
 * <p>
 * Annotated {@code @TestInstance(PER_CLASS)} so that {@link #startTopology()} /
 * {@link #stopTopology()} can be ordinary instance methods - rather than {@code static} with
 * {@code static} container fields - which in turn lets a subclass override {@link #rho()} as an
 * instance method and have it picked up by {@link #startTopology()}. Containers are instance
 * fields, started in {@code @BeforeAll} and stopped in {@code @AfterAll}, so they live and die
 * with the test class rather than being shared across classes.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class MqttITBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttITBase.class);

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "secret";

    /**
     * Basic auth header value for the {@code admin} user set up by {@link #rho()}.
     */
    protected static final String ADMIN_BASIC = "Basic "
        + Base64.getEncoder().encodeToString((ADMIN_USER + ":" + ADMIN_PASSWORD).getBytes());

    // Shared across all requests an IT makes, exactly as ChangeStreamSseIT.SSE_CLIENT does.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    // A Testcontainers-managed bridge network, never host networking: core/build.xml starts its
    // own containers with `docker run --net host`, and putting these containers on the host
    // network too would let that setup collide with or interfere with this one.
    private Network network;

    private GenericContainer<?> mosquitto;

    /**
     * The RESTHeart container started by {@link #startTopology()} with {@link #rho()}'s overrides.
     * A subclass that needs a second, differently configured instance builds one directly with
     * {@link #newRestheartContainer(List)} instead of reconfiguring this one.
     */
    protected GenericContainer<?> restheart;

    /**
     * Starts the shared network, the Mosquitto broker and the RESTHeart container, in that order,
     * before any {@code @Test} runs.
     */
    @BeforeAll
    void startTopology() {
        // Fail fast, before any container is created, if the plugin has not been built.
        pluginBuildDir();

        network = Network.newNetwork();

        mosquitto = new GenericContainer<>("eclipse-mosquitto:2")
            .withNetwork(network)
            // "mosquitto" is the hostname the RESTHeart container's broker-url (set via rho())
            // resolves against, on the shared Testcontainers network.
            .withNetworkAliases("mosquitto")
            // Never a fixed host port: core's own build already binds MongoDB to the fixed host
            // port 27017, and a fixed port here would be free to collide with anything else
            // running on the host. getMappedPort() below is how a test reaches this container.
            .withExposedPorts(1883)
            // Mosquitto 2.x refuses every anonymous connection with no config at all; reuse the
            // module's existing mosquitto.conf rather than writing a second one.
            .withCopyFileToContainer(
                MountableFile.forHostPath(moduleDir().resolve("mosquitto.conf")),
                "/mosquitto/config/mosquitto.conf")
            .waitingFor(Wait.forLogMessage(".*mosquitto version .* running.*", 1)
                .withStartupTimeout(Duration.ofSeconds(30)));
        mosquitto.start();

        restheart = newRestheartContainer(rho());
        restheart.start();
    }

    /**
     * Stops the RESTHeart container, the Mosquitto container and closes the shared network, so
     * nothing from this test class outlives it.
     */
    @AfterAll
    void stopTopology() {
        if (restheart != null) {
            restheart.stop();
        }
        if (mosquitto != null) {
            mosquitto.stop();
        }
        if (network != null) {
            // Never withReuse(true) anywhere in this class: containers must not outlive the test
            // class, and closing the network here is part of that guarantee. Ryuk (never disabled
            // here) is the backstop if this teardown itself does not run.
            network.close();
        }
    }

    /**
     * The RHO (RESTHeart Overrides) pairs applied to the RESTHeart container started by
     * {@link #startTopology()}. A subclass overrides this to add to or replace them, typically as
     * {@code Stream.concat(super.rho().stream(), Stream.of(...)).toList()}.
     * <p>
     * Reasoning for each entry mirrors the comments in {@code mqtt/docker-compose.yml}.
     * </p>
     *
     * @return the RHO override pairs, in application order
     */
    protected List<String> rho() {
        return List.of(
            // http-listener defaults to host "localhost", unreachable from outside the container.
            "/http-listener/host->\"0.0.0.0\"",
            // fileRealmAuthenticator's built-in "admin" user ships with password: null (disabled).
            "/fileRealmAuthenticator/users[userid='admin']/password->\"secret\"",
            // mqtt-client is enabledByDefault=false: the module is dormant on installation.
            "/mqtt-client/enabled->true",
            // Default broker-url is tcp://localhost:1883, which from inside the RESTHeart
            // container is the RESTHeart container itself, not the broker.
            "/mqtt-client/broker-url->\"tcp://mosquitto:1883\"",
            // mqtt-sse is enabledByDefault=false, a second, separate opt-in on top of mqtt-client.
            "/mqtt-sse/enabled->true",
            // mqtt-topic-authorizer stays enabled and denies every request with 403 until an acl
            // is configured, so the "admin" role is granted "sensors/#".
            "/mqtt-topic-authorizer/acl->{\"admin\":[\"sensors/#\"]}");
    }

    /**
     * Builds - but does not start - a RESTHeart container on the shared network, configured with
     * the given RHO overrides and this module's freshly built plugin jar. Exists so a test can
     * start a second, differently configured instance (for example one where the module is left
     * disabled) alongside the one {@link #startTopology()} already started.
     *
     * @param rho the RHO override pairs to join with {@code ";"} into the {@code RHO} env var
     * @return a configured but not-yet-started RESTHeart container
     */
    protected GenericContainer<?> newRestheartContainer(List<String> rho) {
        var target = pluginBuildDir();

        // softinstigate/restheart-snapshot:latest, not a released softinstigate/restheart:*
        // image: per-topic ACL enforcement on /mqtt-sse needs the mqtt-topic-authorizer /
        // SseWildcardInterceptorsExecutor fix, which is on unreleased master and therefore in
        // restheart-snapshot but not yet in any released restheart:* image - the same reason
        // mqtt/docker-compose.yml gives for pinning the same image.
        var image = System.getProperty("restheart.image", "softinstigate/restheart-snapshot:latest");

        var container = new GenericContainer<>(image)
            .withNetwork(network)
            .withNetworkAliases("restheart")
            // Never a fixed host port, for the same reason as Mosquitto's 1883 above.
            .withExposedPorts(8080)
            // The layout PluginsScanner expects: the plugin jar plus a "lib" directory (its
            // runtime dependencies, which must not themselves be scanned for plugins) alongside
            // it - the same layout mqtt/docker-compose.yml already proves works.
            .withCopyFileToContainer(
                MountableFile.forHostPath(target.resolve("restheart-mqtt.jar")),
                "/opt/restheart/plugins/custom/restheart-mqtt.jar")
            .withCopyFileToContainer(
                MountableFile.forHostPath(target.resolve("lib")),
                "/opt/restheart/plugins/custom/lib")
            .withEnv("RHO", String.join(";", rho))
            // So a failing IT shows the server log.
            .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix("restheart"))
            // /ping is registered with secure = false, so it needs no credentials - this is what
            // core/build.xml waits on too.
            .waitingFor(Wait.forHttp("/ping").forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(90)));

        var command = restheartCommand();
        if (!command.isEmpty()) {
            container.withCommand(command.toArray(String[]::new));
        }

        return container;
    }

    /**
     * The arguments appended to the container's {@code java -jar restheart.jar} entrypoint, which
     * has no CMD of its own.
     * <p>
     * Defaults to {@code --standalone}, which makes Bootstrapper load
     * {@code restheart-default-config-no-mongodb.yml}: no MongoDB is needed, and
     * {@code fileRealmAuthenticator} provides the {@code admin} user whose password
     * {@link #rho()} sets. That default is deliberate rather than incidental. Of this module's
     * six plugins only {@code mqtt-mongo-writer} needs MongoDB, so running every other IT without
     * one keeps proving something users actually rely on - that the streaming and REST surfaces
     * work in a RESTHeart with no database behind it. An IT that does need MongoDB overrides this
     * to return an empty list and supplies {@code /mclient/connection-string} through
     * {@link #rho()}, remembering that authentication then goes through
     * {@code mongoRealmAuthenticator} rather than the file realm.
     * </p>
     *
     * @return the command arguments, or an empty list to leave the image's own default
     */
    protected List<String> restheartCommand() {
        return List.of("--standalone");
    }

    /**
     * The module directory, resolved the same way both Maven (failsafe) and an IDE runner set
     * {@code user.dir}: to the module root.
     *
     * @return the {@code mqtt} module directory
     */
    private static Path moduleDir() {
        return Path.of(System.getProperty("user.dir"));
    }

    /**
     * Verifies {@code target/restheart-mqtt.jar} and {@code target/lib} exist before any container
     * is started, so a missing build fails with an actionable message instead of a confusing
     * Testcontainers copy error.
     *
     * @return the module's {@code target} directory
     * @throws IllegalStateException if the plugin has not been built
     */
    private static Path pluginBuildDir() {
        var target = moduleDir().resolve("target");
        var jar = target.resolve("restheart-mqtt.jar");
        var lib = target.resolve("lib");
        if (!Files.isRegularFile(jar) || !Files.isDirectory(lib)) {
            throw new IllegalStateException(
                "mqtt/target/restheart-mqtt.jar and/or mqtt/target/lib are missing; build the "
                    + "plugin first with './mvnw -pl mqtt package'");
        }
        return target;
    }

    /**
     * The HTTP client shared by every request an IT makes, so a subclass does not build a second
     * one. Configured exactly as {@code ChangeStreamSseIT.SSE_CLIENT} is.
     *
     * @return the shared HTTP client
     */
    protected HttpClient httpClient() {
        return HTTP_CLIENT;
    }

    /**
     * @return the base URL of the RESTHeart container started by {@link #startTopology()},
     *         using its Docker-assigned host and mapped port
     */
    protected String baseUrl() {
        return "http://" + restheart.getHost() + ":" + restheart.getMappedPort(8080);
    }

    /**
     * A GET request builder against the RESTHeart container, with the {@code admin} credentials
     * set explicitly.
     * <p>
     * The JDK {@link HttpClient} does not send basic credentials pre-emptively: its
     * {@link java.net.Authenticator} only answers a 401 challenge, which would cost every request
     * an extra round trip and would not work at all against an SSE endpoint whose first response
     * never completes. The header is therefore set by hand instead.
     * </p>
     *
     * @param path the request path, including any query string
     * @return a GET request builder with the {@code Authorization} header set
     */
    protected HttpRequest.Builder authedRequest(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path))
            .header("Authorization", ADMIN_BASIC)
            .GET();
    }

    /**
     * Opens an SSE connection and collects non-blank lines until {@code count} lines have been
     * read or the {@code timeoutSec} deadline is reached. Returns whatever was collected so far
     * if the deadline expires. Mirrors {@code readSseLines} in
     * {@code core/src/test/java/org/restheart/test/integration/ChangeStreamSseIT.java}.
     *
     * @param req the SSE request to send
     * @param count the number of non-blank lines to collect before returning early
     * @param timeoutSec the deadline, in seconds, after which partial results are returned
     * @return the non-blank lines read, in order
     * @throws Exception if sending the request itself fails
     */
    protected List<String> readSseLines(HttpRequest req, int count, int timeoutSec) throws Exception {
        var resp = HTTP_CLIENT.send(req, BodyHandlers.ofInputStream());
        InputStream is = resp.body();

        // Shared so partial results survive a timeout.
        var lines = Collections.synchronizedList(new ArrayList<String>());
        var future = new CompletableFuture<List<String>>();
        Thread.ofVirtual().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null && lines.size() < count) {
                    if (!line.isBlank()) {
                        lines.add(line);
                    }
                }
                future.complete(lines);
            } catch (Exception e) {
                future.complete(lines);
            }
        });

        try {
            // A CompletableFuture with a timeout, so a hang fails the test instead of blocking it.
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return new ArrayList<>(lines); // return whatever arrived before the deadline
        } finally {
            try {
                is.close();
            } catch (Exception ignored) {
                // NOSONAR
            }
        }
    }

    /**
     * Publishes a message to the broker by running {@code mosquitto_pub} inside the Mosquitto
     * container, rather than opening a second MQTT client connection from the test JVM.
     *
     * @param topic the topic to publish to
     * @param payload the message payload
     * @throws IOException if the exec itself fails
     * @throws InterruptedException if the exec is interrupted
     * @throws IllegalStateException if {@code mosquitto_pub} exits with a non-zero status
     */
    protected void publish(String topic, String payload) throws IOException, InterruptedException {
        var result = mosquitto.execInContainer("mosquitto_pub", "-t", topic, "-m", payload);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(
                "mosquitto_pub -t " + topic + " -m " + payload + " exited with " + result.getExitCode()
                    + ": " + result.getStderr());
        }
    }

    /**
     * Runs {@code task} on its own dedicated virtual thread and returns a future for its result,
     * completing with an empty list instead of failing the future if {@code task} throws.
     * <p>
     * Deliberately not {@link CompletableFuture#supplyAsync(java.util.function.Supplier)}, which
     * defaults to the shared {@code ForkJoinPool.commonPool()}: that pool is shared with every
     * other test in the whole Maven JVM, and a test opening two or more concurrent SSE
     * connections needs each one to start reading immediately, not whenever a commonPool worker
     * happens to become free.
     * </p>
     *
     * @param task the blocking SSE read to run
     * @return a future for the lines {@code task} collected
     */
    protected static CompletableFuture<List<String>> subscribeAsync(Callable<List<String>> task) {
        var future = new CompletableFuture<List<String>>();
        Thread.ofVirtual().start(() -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.complete(List.of());
            }
        });
        return future;
    }

    /**
     * @return the RESTHeart container's captured log output, so a test can assert on what the
     *         server logged
     */
    protected String restheartLogs() {
        return restheart.getLogs();
    }
}
