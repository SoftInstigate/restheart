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
package org.restheart.mqtt.it;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
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
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;

/**
 * Base class for {@code restheart-mqtt}'s own integration tests.
 * <p>
 * Unlike the {@code core}-hosted version of these tests this module ran against until now, this
 * class starts, per test class, both a Mosquitto broker (via Testcontainers) and a genuinely
 * separate RESTHeart instance - the {@code core/target/restheart.jar} that was just built
 * alongside this module in the same reactor run, launched as a plain JVM subprocess, not a
 * container and not a published image. That is deliberate: the mqtt module was taken out of the
 * RESTHeart distribution and out of {@code core}'s own IT sequence, so it now proves itself
 * against the core it was built next to, on its own schedule, with Docker only for the one
 * dependency (Mosquitto) that a subprocess cannot stand in for.
 * </p>
 * <p>
 * <strong>The name deliberately does not end in {@code IT}.</strong> The failsafe plugin's
 * default {@code includes} pattern is {@code **&#47;*IT.java}, so a base class whose name ended
 * in {@code IT} would be collected and run as a test class in its own right, instead of merely
 * being extended by ones that are.
 * </p>
 * <p>
 * Annotated {@code @TestInstance(PER_CLASS)} so that {@link #startTopology()} /
 * {@link #stopTopology()} can be ordinary instance methods, and so a subclass that needs a
 * second, differently configured RESTHeart instance alongside the one started here (see
 * {@code MqttPluginLoadingIT}) can do so with an ordinary instance method call to
 * {@link #startRestheart(Path, List, String)} rather than juggling static state.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class MqttITBase {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "secret";

    /**
     * Basic auth header value for the {@code admin} user {@code it-overrides.yml}'s
     * {@code fileRealmAuthenticator} block defines.
     */
    protected static final String ADMIN_BASIC = "Basic "
        + Base64.getEncoder().encodeToString((ADMIN_USER + ":" + ADMIN_PASSWORD).getBytes());

    // Shared across all requests any mqtt IT makes, exactly as ChangeStreamSseIT.SSE_CLIENT does
    // in core.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    /**
     * The Mosquitto broker container for this test class. Protected, not private: only
     * {@code MqttReconnectIT} needs to reach into it - to stop and restart it via the Docker API
     * directly rather than {@link GenericContainer#stop()} - and every other mqtt IT leaves it
     * alone entirely.
     */
    protected GenericContainer<?> mosquitto;

    /**
     * The host port the broker is published on. Fixed for the life of the class rather than
     * assigned by Testcontainers, so that it survives the stop/start {@code MqttReconnectIT}
     * performs - see {@link #startTopology()}.
     */
    protected int brokerPort;

    /**
     * The primary RESTHeart instance for this test class, started by {@link #startTopology()}
     * with {@link #overridesFile()}'s configuration plus this class's broker port. A subclass
     * that needs a second, differently configured instance (see {@code MqttPluginLoadingIT}'s
     * {@code moduleStaysDormantWhenNotSwitchedOn}) calls {@link #startRestheart(Path, List, String)}
     * directly instead of touching this field.
     */
    protected RestheartInstance restheart;

    private volatile Mqtt3BlockingClient publisher;
    private final Object publisherLock = new Object();

    /**
     * Starts the Mosquitto broker and the primary RESTHeart instance, in that order, before any
     * {@code @Test} in the subclass runs.
     * <p>
     * Between the two, {@link #beforeRestheartStarts()} and then {@link #extraRho()} are called -
     * in that order, and deliberately kept as two separate hooks rather than one that does both
     * jobs implicitly. A subclass that needs a dependency RESTHeart itself must connect to (for
     * example a MongoDB container, whose host port is not known before it starts) starts it in
     * {@link #beforeRestheartStarts()} and only then, in {@link #extraRho()}, can report the port
     * that starting it just assigned.
     * </p>
     *
     * @throws IOException if either the broker or RESTHeart fails to start
     */
    @BeforeAll
    void startTopology() throws IOException {
        // An explicit host port binding, not Testcontainers' usual dynamic publish. This is the
        // one place the harness cannot use a dynamic port: Docker re-allocates a dynamically
        // published host port when a container is stopped and started again (verified: 55020 ->
        // 55021 across a stop/start on Docker 29.x), and MqttReconnectIT does exactly that to
        // simulate a broker outage. RESTHeart's broker-url is fixed when it launches, so a moving
        // port would leave it reconnecting to a port nothing listens on any more - the test would
        // fail for a reason that has nothing to do with reconnection.
        //
        // The port is still not hardcoded: it is probed the same way, and with the same accepted
        // race, as the HTTP port in startRestheart - see its javadoc.
        brokerPort = freePort();

        mosquitto = new GenericContainer<>("eclipse-mosquitto:2")
            .withExposedPorts(1883)
            // eclipse-mosquitto:2 ships /mosquitto-no-auth.conf inside the image, containing
            // exactly "listener 1883" and "allow_anonymous true" - no config file and no volume
            // mount of our own needed, unlike the older, now-deleted version of this class which
            // mounted mqtt/mosquitto.conf.
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .waitingFor(Wait.forListeningPort());
        mosquitto.setPortBindings(List.of(brokerPort + ":1883"));
        mosquitto.start();

        beforeRestheartStarts();

        var rho = new ArrayList<String>();
        rho.add("/mqtt-client/broker-url->\"tcp://localhost:" + brokerPort + "\"");
        rho.addAll(extraRho());

        restheart = startRestheart(overridesFile(), rho, getClass().getSimpleName() + ".log", standalone());
    }

    /**
     * Tears down the primary RESTHeart instance, whatever {@link #afterRestheartStops()} adds and
     * the Mosquitto broker, in that order, so nothing outlives the test class - and makes sure
     * every teardown step runs even if an earlier one throws.
     */
    @AfterAll
    void stopTopology() {
        try {
            disconnectPublisher();
        } finally {
            try {
                if (restheart != null) {
                    restheart.close();
                }
            } finally {
                try {
                    afterRestheartStops();
                } finally {
                    if (mosquitto != null) {
                        mosquitto.stop();
                    }
                }
            }
        }
    }

    /**
     * Whether the primary RESTHeart instance {@link #startTopology()} starts is launched with
     * {@code --standalone}.
     * <p>
     * Defaults to {@code true}, i.e. today's behaviour for every existing subclass: {@code
     * --standalone} makes Bootstrapper load {@code restheart-default-config-no-mongodb.yml}, which
     * has no MongoDB at all. A subclass that needs a real {@code mclient} - because it exercises a
     * plugin that writes to MongoDB, such as {@code mqtt-mongo-writer} - overrides this to {@code
     * false} so the full {@code restheart-default-config.yml} is loaded instead, and supplies the
     * MongoDB connection string itself through {@link #extraRho()}.
     * </p>
     *
     * @return {@code true} to launch with {@code --standalone} (the default), {@code false} to
     *         omit it
     */
    protected boolean standalone() {
        return true;
    }

    /**
     * Starts, before the primary RESTHeart instance launches, whatever extra dependency a
     * subclass's tests need beyond the Mosquitto broker every mqtt IT already gets.
     * <p>
     * Default is a no-op. Kept deliberately separate from {@link #extraRho()} - see
     * {@link #startTopology()}'s javadoc for why - and paired with {@link #afterRestheartStops()}
     * for symmetric teardown of whatever this method starts.
     * </p>
     *
     * @throws IOException if the extra dependency fails to start
     */
    protected void beforeRestheartStarts() throws IOException {
        // no-op by default
    }

    /**
     * Contributes additional {@code RHO} pairs for the primary RESTHeart instance, applied on top
     * of the plugins-directory, port and broker-url pairs {@link #startTopology()} always sets.
     * <p>
     * Default is empty. Called after {@link #beforeRestheartStarts()} so a subclass can report a
     * port or connection string that starting its own dependency just assigned - for example the
     * host port Testcontainers picked for a MongoDB container it started in
     * {@link #beforeRestheartStarts()}.
     * </p>
     *
     * @return additional {@code RHO} pairs, in the same {@code "/path->value"} form documented on
     *         {@link #startRestheart(Path, List, String, boolean)}
     */
    protected List<String> extraRho() {
        return List.of();
    }

    /**
     * Stops whatever {@link #beforeRestheartStarts()} started, after the primary RESTHeart
     * instance has already been closed and before the Mosquitto broker is stopped.
     * <p>
     * Default is a no-op, matching {@link #beforeRestheartStarts()}'s default. Runs inside {@link
     * #stopTopology()}'s own {@code finally} chain, so it still runs even if closing RESTHeart
     * itself threw.
     * </p>
     */
    protected void afterRestheartStops() {
        // no-op by default
    }

    /**
     * The RESTHeart overrides file applied, via {@code -o}, to the primary instance
     * {@link #startTopology()} starts.
     * <p>
     * Protected rather than a constant so a subclass could in principle point at a different
     * file, though none of the five ported classes need to: {@code MqttPluginLoadingIT} instead
     * builds its second, dormant instance directly with {@link #startRestheart(Path, List, String)}
     * and {@code it-overrides-dormant.yml}, rather than overriding this method.
     * </p>
     *
     * @return the absolute path to {@code src/test/resources/etc/it-overrides.yml}
     */
    protected Path overridesFile() {
        return Path.of("src", "test", "resources", "etc", "it-overrides.yml").toAbsolutePath().normalize();
    }

    /**
     * Starts a RESTHeart instance as a subprocess of {@code core/target/restheart.jar} - the jar
     * that was just built alongside this module in the same reactor run - and waits for it to
     * become ready.
     * <p>
     * This is the loud failure that replaces what would otherwise be a Maven dependency on
     * {@code core}: there deliberately is none (see {@code mqtt/pom.xml}'s {@code mqtt-it}
     * profile comment), so a missing jar is caught here, with an actionable message, rather than
     * surfacing as a confusing {@link IOException} from {@link ProcessBuilder#start()}.
     * </p>
     * <p>
     * The jar's manifest {@code Class-Path} is a list of {@code lib/...} entries, relative to the
     * jar itself rather than to the JVM's working directory - which is exactly why
     * {@code -jar core/target/restheart.jar} resolves its dependencies correctly regardless of
     * what directory this test process happens to run from.
     * </p>
     * <p>
     * The {@code RHO} environment variable is applied by RESTHeart <strong>after</strong> the
     * {@code -o} overrides file and wins over it wherever both set the same key. That ordering is
     * what lets {@code overridesFile} carry everything static while this method supplies, at
     * runtime, the handful of values that cannot be: the plugins directory (has to be absolute,
     * so it does not resolve against the jar's own directory instead of this module's), the HTTP
     * port (allocated per instance, see below) and, when given, the broker URL (its port is
     * assigned by Testcontainers and cannot be known before the container starts).
     * </p>
     * <p>
     * The port is never hardcoded to 8080: two RESTHeart instances exist at once in
     * {@code MqttPluginLoadingIT}. A free port is probed with a {@code new ServerSocket(0)} that
     * is opened and immediately closed again - the OS could in principle hand the freed port to
     * some other process before RESTHeart itself binds it, but that race is the same one every
     * "find a free port" helper in this kind of test harness accepts, and re-running an
     * occasionally flaky IT is far cheaper than plumbing port selection through RESTHeart itself.
     * </p>
     *
     * @param overridesFile the {@code -o} overrides file to launch RESTHeart with
     * @param extraRho additional {@code RHO} pairs (for example the broker URL), applied on top
     *        of the plugins-directory and port pairs this method always sets; omit the broker URL
     *        entirely for an instance that must have no {@code mqtt-*} configuration at all
     * @param logFileName the file name (not path) of the per-instance log this instance's stdout
     *        and stderr are redirected to, under {@code target/it-logs/}, created fresh
     * @return the started, ready instance
     * @throws IOException if the process cannot be started or the log file cannot be prepared
     */
    protected static RestheartInstance startRestheart(Path overridesFile, List<String> extraRho, String logFileName)
            throws IOException {
        return startRestheart(overridesFile, extraRho, logFileName, true);
    }

    /**
     * Same as {@link #startRestheart(Path, List, String)}, with control over whether the instance
     * is launched with {@code --standalone} - see {@link #standalone()} for why a subclass would
     * want it omitted.
     *
     * @param overridesFile the {@code -o} overrides file to launch RESTHeart with
     * @param extraRho additional {@code RHO} pairs (for example the broker URL), applied on top
     *        of the plugins-directory and port pairs this method always sets; omit the broker URL
     *        entirely for an instance that must have no {@code mqtt-*} configuration at all
     * @param logFileName the file name (not path) of the per-instance log this instance's stdout
     *        and stderr are redirected to, under {@code target/it-logs/}, created fresh
     * @param standalone whether to launch with {@code --standalone} (loads {@code
     *        restheart-default-config-no-mongodb.yml}) or without it (loads {@code
     *        restheart-default-config.yml}, with MongoDB)
     * @return the started, ready instance
     * @throws IOException if the process cannot be started or the log file cannot be prepared
     */
    protected static RestheartInstance startRestheart(Path overridesFile, List<String> extraRho, String logFileName,
            boolean standalone) throws IOException {
        var jar = coreJar();
        var port = freePort();
        var logFile = freshLogFile(logFileName);

        var rho = new ArrayList<String>();
        rho.add("/core/plugins-directory->\"" + itPluginsDir() + "\"");
        rho.add("/http-listener/port->" + port);
        rho.addAll(extraRho);

        var args = new ArrayList<String>();
        args.add(javaExecutable());
        args.add("-jar");
        args.add(jar.toString());
        if (standalone) {
            args.add("--standalone");
        }
        args.add("-o");
        args.add(overridesFile.toAbsolutePath().toString());

        var pb = new ProcessBuilder(args);
        pb.environment().put("RHO", String.join(";", rho));
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));

        var process = pb.start();

        try {
            awaitReady(process, port, logFile);
        } catch (RuntimeException e) {
            process.destroyForcibly();
            throw e;
        }

        return new RestheartInstance(process, port, logFile);
    }

    /**
     * Polls {@code GET /ping} - registered {@code secure = false}, so no credentials are needed -
     * until it answers 200, up to 90 seconds.
     *
     * @param process the RESTHeart subprocess, checked for an early exit so a crash fails fast
     *        rather than waiting out the full timeout
     * @param port the port RESTHeart was told to listen on
     * @param logFile the log file to name in the failure message, so the real startup error is
     *        one {@code cat} away
     * @throws IllegalStateException if RESTHeart is not ready within 90 seconds, or exits early
     */
    private static void awaitReady(Process process, int port, Path logFile) {
        var uri = URI.create("http://localhost:" + port + "/ping");
        var deadline = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(
                    "RESTHeart exited before becoming ready (exit code " + process.exitValue()
                        + "); see " + logFile.toAbsolutePath());
            }
            try {
                var req = HttpRequest.newBuilder(uri).GET().build();
                var resp = HTTP_CLIENT.send(req, BodyHandlers.discarding());
                if (resp.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // not up yet
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted waiting for RESTHeart to become ready", e);
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted waiting for RESTHeart to become ready", e);
            }
        }
        throw new IllegalStateException(
            "RESTHeart did not answer 200 on GET /ping within 90s; see " + logFile.toAbsolutePath());
    }

    /**
     * @return the {@code java} executable of the very JVM running this test, so this harness
     *         never hardcodes {@code "java"} and relies on it being on {@code PATH}
     */
    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
            .orElseGet(() -> System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
    }

    /**
     * @return {@code core/target/restheart.jar}, the jar built alongside this module in the same
     *         reactor run
     * @throws IllegalStateException if the jar is missing - the loud failure documented on
     *         {@link #startRestheart(Path, List, String)}
     */
    private static Path coreJar() {
        var jar = Path.of("..", "core", "target", "restheart.jar").toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(
                "core/target/restheart.jar not found at " + jar + "; the mqtt ITs run against the core "
                    + "module built alongside them in the same reactor run - build core first with "
                    + "'./mvnw -pl mqtt -am verify -Pmqtt-it'.");
        }
        return jar;
    }

    /**
     * @return the absolute path to {@code mqtt/target/it-plugins}, the directory
     *         {@code mqtt/pom.xml}'s {@code stage-it-plugins} antrun execution populates with
     *         both {@code core}'s plugins and this module's own
     */
    private static Path itPluginsDir() {
        return Path.of("target", "it-plugins").toAbsolutePath().normalize();
    }

    /**
     * @return a port nothing was listening on at the moment this method returned - see the race
     *         documented on {@link #startRestheart(Path, List, String)}
     * @throws IOException if no ephemeral port could be probed at all
     */
    private static int freePort() throws IOException {
        try (var s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /**
     * @param name the log file's simple name, typically the test class's simple name
     * @return a fresh (deleted if it already existed), not-yet-written {@code target/it-logs/<name>}
     * @throws IOException if the {@code target/it-logs} directory cannot be created
     */
    private static Path freshLogFile(String name) throws IOException {
        var dir = Path.of("target", "it-logs").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        var file = dir.resolve(name);
        Files.deleteIfExists(file);
        return file;
    }

    /**
     * @return the base URL of the primary RESTHeart instance {@link #startTopology()} started
     */
    protected String baseUrl() {
        return restheart.baseUrl();
    }

    /**
     * The shared HTTP client every mqtt IT sends requests with.
     *
     * @return the shared HTTP client
     */
    protected static HttpClient httpClient() {
        return HTTP_CLIENT;
    }

    /**
     * A GET request builder against the primary RESTHeart instance, with the {@code admin}
     * credentials set explicitly.
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
        return readSseLinesUntil(req, line -> false, count, timeoutSec);
    }

    /**
     * Reads SSE lines until one satisfies {@code done}, until {@code maxLines} have been
     * collected, or until the deadline expires - whichever comes first - and returns everything
     * collected.
     * <p>
     * Prefer this over {@link #readSseLines(HttpRequest, int, int)} whenever a connection is held
     * open across several publishes. A line count is the wrong stopping condition there: it has
     * to be padded to absorb a possible duplicate event, and a padded count that the connection
     * never reaches costs the caller the entire timeout on every run, whether the test passes or
     * fails. Stopping on the content the test is actually waiting for returns as soon as the
     * assertion could succeed, and leaves the timeout to do its real job of failing a hang.
     * </p>
     *
     * @param req the SSE request to send
     * @param done predicate on each non-blank line; the first line satisfying it ends the read
     * @param maxLines an upper bound on lines collected, so a never-satisfied predicate still ends
     * @param timeoutSec the deadline, in seconds, after which partial results are returned
     * @return the non-blank lines read, in order
     * @throws Exception if sending the request itself fails
     */
    protected List<String> readSseLinesUntil(HttpRequest req, Predicate<String> done, int maxLines,
            int timeoutSec) throws Exception {
        var resp = HTTP_CLIENT.send(req, BodyHandlers.ofInputStream());
        InputStream is = resp.body();

        // Shared so partial results survive a timeout.
        var lines = Collections.synchronizedList(new ArrayList<String>());
        var future = new CompletableFuture<List<String>>();
        Thread.ofVirtual().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null && lines.size() < maxLines) {
                    if (!line.isBlank()) {
                        lines.add(line);
                        if (done.test(line)) {
                            break;
                        }
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
     * Publishes a message to this test class's broker at QoS 1, blocking until the broker
     * acknowledges it, so this helper returns only once the message is genuinely at the broker.
     * Every {@code Thread.sleep} in the ported tests is calibrated against that guarantee.
     * <p>
     * Connects the class-shared {@link Mqtt3BlockingClient} lazily, on first use, guarded against
     * a concurrent double-connect. Deliberately MQTT 3.1.1 ({@code useMqttVersion3()}), matching
     * the server side: {@code mqtt-client}'s {@code protocol-version} defaults to 3, and
     * {@code it-overrides.yml} does not change it.
     * </p>
     *
     * @param topic the topic to publish to
     * @param payload the message payload
     */
    protected void publish(String topic, String payload) {
        publisher().publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload(payload.getBytes())
            .send();
    }

    /**
     * @return the shared, connected publisher client for this test class, (re)connecting it
     *         first if this is the first publish in this class, or if a previous outage left it
     *         disconnected
     */
    private Mqtt3BlockingClient publisher() {
        var p = publisher;
        if (p == null || !p.getConfig().getState().isConnected()) {
            synchronized (publisherLock) {
                p = publisher;
                if (p == null) {
                    p = MqttClient.builder()
                        .useMqttVersion3()
                        .identifier("mqtt-it-publisher-" + System.nanoTime())
                        .serverHost("localhost")
                        .serverPort(brokerPort)
                        .buildBlocking();
                    publisher = p;
                }
                if (!p.getConfig().getState().isConnected()) {
                    p.connect();
                }
            }
        }
        return p;
    }

    /**
     * Forces the shared publisher client to reconnect, rather than relying on {@link #publish}'s
     * own lazy, state-based check.
     * <p>
     * Exists for {@code MqttReconnectIT}, and only makes sense there: a client that is idle
     * across a broker outage it deliberately caused - unlike the module's own {@code mqtt-client},
     * whose automatic-reconnect keeps probing the connection - sends and receives nothing while
     * the broker is down, so nothing prompts it to notice the outage. {@link
     * com.hivemq.client.mqtt.MqttClientConfig#getState()} can therefore still read
     * {@code CONNECTED} for a socket the restarted broker has never heard of, and a publish
     * attempted through it is not reliably delivered - it is this scenario, not the ordinary
     * "not connected yet" case {@link #publisher()} already handles, that motivates a caller
     * being able to force the issue rather than trust the cached state. Call this only once the
     * broker is known to be accepting connections again, since {@link Mqtt3BlockingClient#connect()}
     * itself is what fails loudly if the broker is not actually up yet.
     * </p>
     */
    protected void reconnectPublisher() {
        synchronized (publisherLock) {
            var p = publisher;
            if (p == null) {
                // Nothing has published yet in this class: publisher() will connect it lazily on
                // first use, same as ever.
                return;
            }
            if (p.getConfig().getState().isConnected()) {
                p.disconnect();
            }
            p.connect();
        }
    }

    /**
     * Disconnects the shared publisher client, if it was ever connected, so this test class does
     * not leave a live broker connection behind it.
     */
    private void disconnectPublisher() {
        var p = publisher;
        // Only if still connected: MqttReconnectIT's broker outage can leave this client
        // disconnected on its own, and disconnect() is meaningless - and, on some client states,
        // rejected - once that has already happened.
        if (p != null && p.getConfig().getState().isConnected()) {
            p.disconnect();
        }
    }

    /**
     * @return the content of the primary RESTHeart instance's log file
     */
    protected String restheartLogs() {
        return restheart.logs();
    }

    /**
     * A started, ready RESTHeart subprocess plus what a test needs to talk to it and read its log.
     * <p>
     * Returned by {@link MqttITBase#startRestheart(Path, List, String)} so a test class can start
     * more than one instance - see {@code MqttPluginLoadingIT}'s {@code moduleStaysDormantWhenNotSwitchedOn},
     * which builds a second one directly rather than reconfiguring {@link MqttITBase#restheart}.
     * </p>
     *
     * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
     */
    protected static final class RestheartInstance implements AutoCloseable {
        private final Process process;
        private final int port;
        private final Path logFile;

        private RestheartInstance(Process process, int port, Path logFile) {
            this.process = process;
            this.port = port;
            this.logFile = logFile;
        }

        /**
         * @return this instance's base URL
         */
        protected String baseUrl() {
            return "http://localhost:" + port;
        }

        /**
         * @return the content of this instance's log file
         * @throws IllegalStateException if the log file cannot be read
         */
        protected String logs() {
            try {
                return Files.readString(logFile);
            } catch (IOException e) {
                throw new IllegalStateException("failed to read " + logFile.toAbsolutePath(), e);
            }
        }

        /**
         * Destroys the subprocess: a graceful {@link Process#destroy()} first, waiting up to 10
         * seconds, then {@link Process#destroyForcibly()} if it is still alive - so a hung
         * RESTHeart shutdown never leaves this test run itself hanging.
         */
        @Override
        public void close() {
            process.destroy();
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
