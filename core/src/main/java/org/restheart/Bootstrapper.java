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
package org.restheart;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.ansi;
import org.fusesource.jansi.AnsiConsole;
import org.restheart.buffers.ThreadAwareByteBufferPool;
import org.restheart.configuration.Configuration;
import org.restheart.configuration.ConfigurationException;
import org.restheart.configuration.ProxiedResource;
import org.restheart.configuration.Utils;
import org.restheart.exchange.Exchange;
import static org.restheart.exchange.Exchange.MAX_CONTENT_SIZE;
import org.restheart.exchange.ExchangeKeys;
import org.restheart.exchange.PipelineInfo;
import static org.restheart.exchange.PipelineInfo.PIPELINE_TYPE.PROXY;
import static org.restheart.exchange.PipelineInfo.PIPELINE_TYPE.STATIC_RESOURCE;
import org.restheart.handlers.BeforeExchangeInitInterceptorsExecutor;
import org.restheart.handlers.ConfigurableEncodingHandler;
import org.restheart.handlers.ErrorHandler;
import org.restheart.handlers.PipelinedHandler;
import static org.restheart.handlers.PipelinedHandler.pipe;
import org.restheart.handlers.PipelinedWrappingHandler;
import org.restheart.handlers.ProxyExchangeBuffersCloser;
import org.restheart.handlers.QueryStringRebuilder;
import org.restheart.handlers.RequestInterceptorsExecutor;
import org.restheart.handlers.RequestLogger;
import org.restheart.handlers.RequestNotManagedHandler;
import org.restheart.handlers.TracingInstrumentationHandler;
import org.restheart.handlers.WorkingThreadsPoolDispatcher;
import org.restheart.handlers.injectors.AuthHeadersRemover;
import org.restheart.handlers.injectors.ConduitInjector;
import org.restheart.handlers.injectors.PipelineInfoInjector;
import org.restheart.handlers.injectors.RequestContentInjector;
import static org.restheart.handlers.injectors.RequestContentInjector.Policy.ON_REQUIRES_CONTENT_AFTER_AUTH;
import static org.restheart.handlers.injectors.RequestContentInjector.Policy.ON_REQUIRES_CONTENT_BEFORE_AUTH;
import org.restheart.handlers.injectors.XForwardedHeadersInjector;
import static org.restheart.plugins.InitPoint.AFTER_STARTUP;
import static org.restheart.plugins.InitPoint.BEFORE_STARTUP;
import static org.restheart.plugins.InterceptPoint.REQUEST_AFTER_AUTH;
import static org.restheart.plugins.InterceptPoint.REQUEST_BEFORE_AUTH;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Authorizer.TYPE;
import org.restheart.plugins.security.TokenManager;
import org.restheart.security.handlers.SecurityHandler;
import static org.restheart.utils.BootstrapperUtils.checkPidFile;
import static org.restheart.utils.BootstrapperUtils.initLogging;
import static org.restheart.utils.BootstrapperUtils.logLoggingConfiguration;
import static org.restheart.utils.BootstrapperUtils.logStartMessages;
import static org.restheart.utils.BootstrapperUtils.pidFile;
import static org.restheart.utils.BootstrapperUtils.setJsonpathDefaults;
import org.restheart.utils.FileUtils;
import org.restheart.utils.LoggingInitializer;
import org.restheart.utils.OSChecker;
import org.restheart.utils.PluginUtils;
import static org.restheart.utils.PluginUtils.defaultURI;
import static org.restheart.utils.PluginUtils.initPoint;
import static org.restheart.utils.PluginUtils.uriMatchPolicy;
import org.restheart.utils.RESTHeartDaemon;
import org.restheart.utils.ResourcesExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;
import org.xnio.Xnio;

import static io.undertow.Handlers.resource;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.handlers.AllowedMethodsHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.HttpContinueAcceptingHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.util.HttpString;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public final class Bootstrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrapper.class);

    private static boolean IS_FORKED;

    private static final Map<String, File> TMP_EXTRACTED_FILES = new HashMap<>();

    private static Path CONFIGURATION_FILE_PATH;
    private static Path CONF_OVERRIDES_FILE_PATH;
    private static boolean printConfiguration = false;
    private static boolean printConfigurationTemplate = false;
    private static boolean standaloneConfiguration = false;

    private static GracefulShutdownHandler HANDLERS = null;
    private static Configuration configuration;
    private static Undertow undertowServer;

    private static final String EXITING = ", exiting...";
    private static final String RESTHEART = "RESTHeart";

    /**
     *
     * @return the global configuration
     */
    public static Configuration getConfiguration() {
        return configuration;
    }

    private static void parseCommandLineParameters(final String[] args) {
        var parameters = new Args();
        var cmd = new CommandLine(parameters);

        try {
            cmd.parseArgs(args);
            if (cmd.isUsageHelpRequested()) {
                cmd.usage(System.out);
                System.exit(0);
            }

            if (cmd.isVersionHelpRequested()) {
                var version = Version.getInstance().getVersion() == null
                    ? "unknown (not packaged)"
                    : Version.getInstance().getVersion();

                System.out.println(RESTHEART
                    .concat(" Version ")
                    .concat(version)
                    .concat(" Build-Time ")
                    .concat(DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Version.getInstance().getBuildTime())));

                System.exit(0);
            }

            // print configuration options
            printConfiguration = parameters.printConfiguration;
            printConfigurationTemplate = parameters.printConfigurationTemplate;
            standaloneConfiguration = parameters.standalone;

            var confFilePath = (parameters.configPath == null)
                ? System.getenv("RESTHEART_CONF_FILE")
                : parameters.configPath;
            CONFIGURATION_FILE_PATH = FileUtils.getFileAbsolutePath(confFilePath);

            FileUtils.getFileAbsolutePath(parameters.configPath);

            IS_FORKED = parameters.isForked;

            var confOverridesFilePath = parameters.rho == null
                ? System.getenv("RHO_FILE")
                : parameters.rho;

            CONF_OVERRIDES_FILE_PATH = FileUtils.getFileAbsolutePath(confOverridesFilePath);
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
            cmd.usage(System.out);
            System.exit(1);
        }
    }

    public static void main(final String[] args) throws ConfigurationException, IOException {
        doNotWarnTruffleInterpreterOnly();
        parseCommandLineParameters(args);
        setJsonpathDefaults();
        try {
            configuration = Configuration.Builder.build(CONFIGURATION_FILE_PATH, CONF_OVERRIDES_FILE_PATH, standaloneConfiguration, true);
        } catch(ConfigurationException ce) {
            var confJustForError = Configuration.Builder.build(true, true);
            initLogging(confJustForError, null, IS_FORKED);
            logErrorAndExit(ce.getMessage(), ce, false, true, -1);
        }

        run();
    }

    private static void run() {
        if (!configuration.logging().ansiConsole()) {
            AnsiConsole.systemInstall();
        }

        if (!IS_FORKED) {
            initLogging(configuration, null, IS_FORKED);
            startServer(false);
        } else {
            if (OSChecker.isWindows()) {
                LOGGER.error("Fork is not supported on Windows");
                LOGGER.info(ansi().fg(GREEN).bold().a("RESTHeart stopped").reset().toString());
                System.exit(-1);
            }

            // RHSecDaemon only works on POSIX OSes
            final boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

            if (!isPosix) {
                logErrorAndExit("Unable to fork process, this is only supported on POSIX compliant OSes", null, false, -1);
            }

            var d = new RESTHeartDaemon();
            if (d.isDaemonized()) {
                try {
                    d.init();
                    initLogging(configuration, d, IS_FORKED);
                } catch (Exception t) {
                    logErrorAndExit("Error staring forked process", t, false, false, -1);
                }
                startServer(true);
            } else {
                initLogging(configuration, d, IS_FORKED);
                try {
                    logStartMessages(configuration);
                    logLoggingConfiguration(configuration, false);
                    d.daemonize();
                } catch (Throwable t) {
                    logErrorAndExit("Error forking", t, false, false, -1);
                }
            }
        }
    }

    /**
     * Shutdown the server
     *
     * @param args command line arguments
     */
    public static void shutdown(final String[] args) {
        stopServer(false);
    }

    /**
     * startServer
     *
     * @param fork
     */
    private static void startServer(boolean fork) {
        logStartMessages(configuration);

        var pidFilePath = pidFile(CONFIGURATION_FILE_PATH, CONF_OVERRIDES_FILE_PATH);
        var pidFileAlreadyExists = false;

        if (!OSChecker.isWindows() && pidFilePath != null) {
            pidFileAlreadyExists = checkPidFile(CONFIGURATION_FILE_PATH, CONF_OVERRIDES_FILE_PATH);
        }

        logLoggingConfiguration(configuration, fork);

        // re-read configuration file, to log errors now that logger is initialized
        try {
            Configuration.Builder.build(CONFIGURATION_FILE_PATH, CONF_OVERRIDES_FILE_PATH, standaloneConfiguration, false);
        } catch (ConfigurationException ex) {
            logErrorAndExit(ex.getMessage() + EXITING, ex, false, -1);
        }

        // if -c, just print the effective configuration to sterr and exit
        if (printConfiguration) {
            LOGGER.info("Printing configuration and exiting");
            System.err.println(configuration.toString());
            System.exit(0);
        }

        // if -t, just print the configuration to sterr and exit
        if (printConfigurationTemplate) {
            var confFilePath = standaloneConfiguration ? "/restheart-default-config-no-mongodb.yml" : "/restheart-default-config.yml";

            try (var confFileStream = Configuration.class.getResourceAsStream(confFilePath)) {
                var content = new BufferedReader(new InputStreamReader(confFileStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
                LOGGER.info("Printing configuration template and exiting");
                System.err.println(content);
                System.exit(0);
            } catch(IOException ioe) {
                logErrorAndExit(ioe.getMessage() + EXITING, ioe, false, -1);
            }
        }

        // force instantiation of all plugins singletons
        try {
            PluginsRegistryImpl.getInstance().instantiateAll();
        } catch (IllegalArgumentException iae) {
            // this occurs instatiating plugin missing external dependencies
            // unfortunatly Classgraph wraps it to IllegalArgumentException
            if (iae.getMessage() != null && iae.getMessage().contains("NoClassDefFoundError")) {
                logErrorAndExit("Error instantiating plugins: an external dependency is missing. Copy the missing dependency jar to the plugins directory to add it to the classpath", iae, false, -112);
            } else {
                logErrorAndExit("Error instantiating plugins", iae, false, -110);
            }
        } catch (NoClassDefFoundError ncdfe) {
            // this occurs instatiating plugin missing external dependencies
            // unfortunatly Classgraph wraps it to IllegalArgumentException

            logErrorAndExit("Error instantiating plugins: an external dependency is missing. Copy the missing dependency jar to the plugins directory to add it to the classpath", ncdfe, false, -112);
        } catch (LinkageError le) {
            // this occurs executing plugin code compiled
            // with wrong version of restheart-commons

            var version = Version.getInstance().getVersion() == null ? "of correct version" : "v" + Version.getInstance().getVersion();

            logErrorAndExit("Linkage error instantiating plugins. Check that all plugins were compiled against restheart-commons " + version, le, false, -111);
        } catch (Throwable t) {
            logErrorAndExit("Error instantiating plugins", t, false, -110);
        }

        // run pre startup initializers
        PluginsRegistryImpl.getInstance()
            .getInitializers()
            .stream()
            .filter(i -> initPoint(i.getInstance()) == BEFORE_STARTUP)
            .forEach(i -> {
                try {
                    i.getInstance().init();
                } catch (NoClassDefFoundError iae) {
                    // this occurs executing interceptors missing external dependencies
                    LOGGER.error("Error executing initializer {}. An external dependency is missing. Copy the missing dependency jar to the plugins directory to add it to the classpath", i.getName(), iae);
                } catch (LinkageError le) {
                    // this might occur executing plugin code compiled
                    // with wrong version of restheart-commons
                    var version = Version.getInstance().getVersion() == null
                        ? "of correct version"
                        : "v" + Version.getInstance().getVersion();

                    LOGGER.error("Linkage error executing initializer {}. Check that it was compiled against restheart-commons {}", i.getName(), version, le);
                } catch (Throwable t) {
                    LOGGER.error("Error executing initializer {}", i.getName());
                }
            });
        try {
            startCoreSystem();
        } catch (Throwable t) {
            logErrorAndExit("Error starting RESTHeart. Exiting...", t, false, !pidFileAlreadyExists, -2);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopServer(false)));

        // create pid file on supported OSes
        if (!OSChecker.isWindows() && pidFilePath != null) {
            FileUtils.createPidFile(pidFilePath);
            LOGGER.info("Pid file {}", pidFilePath);
        }

        // run initializers
        PluginsRegistryImpl.getInstance()
            .getInitializers()
            .stream()
            .filter(i -> initPoint(i.getInstance()) == AFTER_STARTUP)
            .forEach(i -> {
                try {
                    i.getInstance().init();
                } catch (NoClassDefFoundError iae) {
                    // this occurs executing interceptors missing external dependencies

                    LOGGER.error("Error executing initializer {}. An external dependency is missing. Copy the missing dependency jar to the plugins directory to add it to the classpath", i.getName(), iae);
                } catch (LinkageError le) {
                    // this might occur executing plugin code compiled
                    // with wrong version of restheart-commons

                    var version = Version.getInstance().getVersion() == null
                            ? "of correct version"
                            : "v" + Version.getInstance().getVersion();

                    LOGGER.error("Linkage error executing initializer {}. Check that it was compiled against restheart-commons {}", i.getName(), version, le);
                } catch (Throwable t) {
                    LOGGER.error("Error executing initializer {}", i.getName(), t);
                }
            });

        LOGGER.info(ansi().fg(GREEN).bold().a("RESTHeart started").reset().toString());
    }

    /**
     * stopServer
     *
     * @param silent
     */
    private static void stopServer(boolean silent) {
        stopServer(silent, true);
    }

    /**
     * stopServer
     *
     * @param silent
     * @param removePid
     */
    private static void stopServer(boolean silent, boolean removePid) {
        if (!silent) {
            LOGGER.info("Stopping RESTHeart...");
        }

        if (HANDLERS != null) {
            if (!silent) {
                LOGGER.info("Waiting for pending request to complete (up to 1 minute)...");
            }
            try {
                HANDLERS.shutdown();
                HANDLERS.awaitShutdown(60 * 1000); // up to 1 minute
            } catch (InterruptedException ie) {
                LOGGER.error("Error while waiting for pending request to complete", ie);
                Thread.currentThread().interrupt();
            }
        }

        var pidFilePath = FileUtils.getPidFilePath(FileUtils.getFileAbsolutePathHash(CONFIGURATION_FILE_PATH, CONF_OVERRIDES_FILE_PATH));

        if (removePid && pidFilePath != null) {
            if (!silent) {
                LOGGER.info("Removing the pid file {}", pidFilePath.toString());
            }
            try {
                Files.deleteIfExists(pidFilePath);
            } catch (IOException ex) {
                LOGGER.error("Can't delete pid file {}", pidFilePath.toString(), ex);
            }
        }

        if (!silent) {
            LOGGER.info("Cleaning up temporary directories...");
        }
        TMP_EXTRACTED_FILES.keySet().forEach(k -> {
            try {
                ResourcesExtractor.deleteTempDir(Bootstrapper.class, k, TMP_EXTRACTED_FILES.get(k));
            } catch (URISyntaxException | IOException ex) {
                LOGGER.error("Error cleaning up temporary directory {}", TMP_EXTRACTED_FILES.get(k).toString(), ex);
            }
        });

        if (undertowServer != null) {
            undertowServer.stop();
        }

        if (!silent) {
            LOGGER.info(ansi().fg(GREEN).bold().a("RESTHeart stopped").reset().toString());
        }

        LoggingInitializer.stopLogging();
    }

    /**
     * startCoreSystem
     */
    private static void startCoreSystem() {
        if (configuration == null) {
            logErrorAndExit("No configuration found. exiting..", null, false, -1);
        }


        if (!configuration.httpsListener().enabled() && !configuration.httpListener().enabled() && !configuration.ajpListener().enabled()) {
            logErrorAndExit("No listener specified. exiting..", null, false, -1);
        }

        final var tokenManager = PluginsRegistryImpl.getInstance().getTokenManager();

        final var authMechanisms = PluginsRegistryImpl.getInstance().getAuthMechanisms();

        if (authMechanisms == null || authMechanisms.isEmpty()) {
            LOGGER.warn(ansi().fg(RED).bold().a("No Authentication Mechanisms defined").reset().toString());
        }

        final var authorizers = PluginsRegistryImpl.getInstance().getAuthorizers();

        final var allowers = authorizers == null
            ? null
            : authorizers.stream()
                .filter(a -> a.isEnabled())
                .filter(a -> a.getInstance() != null)
                .map(a -> a.getInstance())
                .filter(a -> PluginUtils.authorizerType(a) == TYPE.ALLOWER)
                .collect(Collectors.toList());

        if (allowers == null || allowers.isEmpty()) {
            LOGGER.warn(ansi().fg(RED).bold().a("No Authorizer of type ALLOWER defined, all requests to secured services will be forbidden; fullAuthorizer can be enabled to allow any request.").reset().toString());
        }

        var builder = Undertow.builder();

        // set the bytee buffer pool
        // since the undertow default byte buffer is not good for virtual threads
        builder.setByteBufferPool(new ThreadAwareByteBufferPool(
            configuration.coreModule().directBuffers(),
            configuration.coreModule().bufferSize(),
            configuration.coreModule().buffersPooling()));

        var httpsListener = configuration.httpsListener();
        if (httpsListener.enabled()) {
            builder.addHttpsListener(httpsListener.port(), httpsListener.host(), initSSLContext());
            if (httpsListener.host().equals("127.0.0.1") || httpsListener.host().equalsIgnoreCase("localhost")) {
                LOGGER.warn("HTTPS listener bound to localhost:{}. Remote systems will be unable to connect to this server.", httpsListener.port());
            } else {
                LOGGER.info("HTTPS listener bound at {}:{}", httpsListener.host(), httpsListener.port());
            }
        }

        var httpListener = configuration.httpListener();
        if (httpListener.enabled()) {
            builder.addHttpListener(httpListener.port(), httpListener.host());

            if (httpListener.host().equals("127.0.0.1") || httpListener.host().equalsIgnoreCase("localhost")) {
                LOGGER.warn("HTTP listener bound to localhost:{}. Remote systems will be unable to connect to this server.", httpListener.port());
            } else {
                LOGGER.info("HTTP listener bound at {}:{}", httpListener.host(), httpListener.port());
            }
        }

        var ajpListener = configuration.ajpListener();
        if (ajpListener.enabled()) {
            builder.addAjpListener(ajpListener.port(), ajpListener.host());

            if (ajpListener.host().equals("127.0.0.1") || ajpListener.host().equalsIgnoreCase("localhost")) {
                LOGGER.warn("AJP listener bound to localhost:{}. Remote systems will be unable to connect to this server.", ajpListener.port());
            } else {
                LOGGER.info("AJP listener bound at {}:{}", ajpListener.host(), ajpListener.port());
            }
        }

        HANDLERS = getPipeline(authMechanisms, authorizers, tokenManager);

        // update buffer size
        Exchange.updateBufferSize(configuration.coreModule().bufferSize());

        // io threads
        // use value in configuration, or auto detect values if io-threads <= 0
        var autoConfigIoThreads = configuration.coreModule().ioThreads() <= 0;
        var ioThreads = autoConfigIoThreads ? Runtime.getRuntime().availableProcessors() : configuration.coreModule().ioThreads();

        // virtual threads carriers
        // use value in configuration, or auto detect values if virtual-threads-carriers <= 0
        var autoConfigWorkersSchedulerParallelism = configuration.coreModule().workersSchedulerParallelism() <= 0;
        var workersSchedulerParallelism = autoConfigWorkersSchedulerParallelism ? Math.round(Runtime.getRuntime().availableProcessors()*1.5) : configuration.coreModule().workersSchedulerParallelism();

        // apply workersSchedulerParallelism and workersSchedulerMaxPoolSize
        System.setProperty("jdk.virtualThreadScheduler.parallelism", String.valueOf(workersSchedulerParallelism));
        System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", String.valueOf(configuration.coreModule().workersSchedulerMaxPoolSize()));

        LOGGER.info("Available processors: {}, IO threads{}: {}, worker scheduler parallelism{}: {}, worker scheduler max pool size: {}",
            Runtime.getRuntime().availableProcessors(), autoConfigIoThreads ? " (auto detected)" : "", ioThreads,
            autoConfigWorkersSchedulerParallelism ? " (auto detected)" : "", workersSchedulerParallelism,
            configuration.coreModule().workersSchedulerMaxPoolSize());

        builder = builder
            .setIoThreads(ioThreads)
            .setWorkerThreads(0) // starting v8, restheart uses virtual threads
            .setDirectBuffers(configuration.coreModule().directBuffers())
            .setBufferSize(configuration.coreModule().bufferSize())
            .setHandler(HANDLERS);

        // starting from undertow 1.4.23 URL checks become much stricter
        // (undertow commit 09d40a13089dbff37f8c76d20a41bf0d0e600d9d)
        // allow unescaped chars in URL (otherwise not allowed by default)
        builder.setServerOption(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, configuration.coreModule().allowUnescapedCharsInUrl());

        LOGGER.debug("Allow unescaped characters in URL: {}", configuration.coreModule().allowUnescapedCharsInUrl());

        Utils.setConnectionOptions(builder, configuration);

        undertowServer = builder.build();
        undertowServer.start();
    }

    private static SSLContext initSSLContext() {
        try {
            var sslContext = SSLContext.getInstance("TLSv1.2");

            var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

            var ks = KeyStore.getInstance(KeyStore.getDefaultType());

            var httpsListener = configuration.httpsListener();
            if (httpsListener.keystorePath() != null && httpsListener.keystorePwd() != null && httpsListener.certificatePwd() != null) {
                try (var fis = new FileInputStream(new File(httpsListener.keystorePath()))) {
                    ks.load(fis, httpsListener.keystorePwd().toCharArray());
                    kmf.init(ks, httpsListener.certificatePwd().toCharArray());
                }
            } else {
                logErrorAndExit("Cannot enable the HTTPS listener: the keystore is not configured. Generate a keystore and set the configuration options keystore-path, keystore-password and certificate-password. More information at https://restheart.org/docs/security/tls/", null, false, -1);
            }

            tmf.init(ks);

            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            return sslContext;
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | CertificateException | UnrecoverableKeyException ex) {
            logErrorAndExit("Couldn't start RESTHeart, error with specified keystore. Check the keystore-path, keystore-password and certificate-password options. Exiting..", ex, false, -1);
        } catch (FileNotFoundException ex) {
            logErrorAndExit("Couldn't start RESTHeart, keystore file not found. Check the keystore-path, keystore-password and certificate-password options. Exiting..", ex, false, -1);
        } catch (IOException ex) {
            logErrorAndExit("Couldn't start RESTHeart, error reading the keystore file. Check the keystore-path, keystore-password and certificate-password options. Exiting..", ex, false, -1);
        }

        return null;
    }

    /**
     * logErrorAndExit
     *
     * @param message
     * @param t
     * @param silent
     * @param status
     */
    private static void logErrorAndExit(String message, Throwable t, boolean silent, int status) {
        logErrorAndExit(message, t, silent, true, status);
    }

    /**
     * getHandlersPipe
     *
     * @param identityManager
     * @param authorizers
     * @param tokenManager
     * @return a GracefulShutdownHandler
     */
    private static GracefulShutdownHandler getPipeline(final Set<PluginRecord<AuthMechanism>> authMechanisms, final Set<PluginRecord<Authorizer>> authorizers, final PluginRecord<TokenManager> tokenManager) {
        PluginsRegistryImpl
            .getInstance()
            .getRootPathHandler()
            .addPrefixPath("/", new RequestNotManagedHandler());

        LOGGER.debug("Content buffers maximun size is {} bytes", MAX_CONTENT_SIZE);

        plugServices();

        plugProxies(configuration, authMechanisms, authorizers, tokenManager);

        plugStaticResourcesHandlers(configuration);

        return getBasePipeline();
    }

    /**
     *
     * @return the base handler pipeline
     */
    private static GracefulShutdownHandler getBasePipeline() {
        return new GracefulShutdownHandler(
            new AllowedMethodsHandler(
                new ErrorHandler(PipelinedWrappingHandler.wrap(new HttpContinueAcceptingHandler(PluginsRegistryImpl.getInstance().getRootPathHandler()))),
                // allowed methods
                HttpString.tryFromString(ExchangeKeys.METHOD.GET.name()),
                HttpString.tryFromString(ExchangeKeys.METHOD.POST.name()),
                HttpString.tryFromString(ExchangeKeys.METHOD.PUT.name()),
                HttpString.tryFromString(ExchangeKeys.METHOD.DELETE.name()),
                HttpString.tryFromString(ExchangeKeys.METHOD.PATCH.name()),
                HttpString.tryFromString(ExchangeKeys.METHOD.OPTIONS.name())));
    }

    /**
     * plug services
     */
    private static void plugServices() {
        PluginsRegistryImpl.getInstance().getServices().stream()
        // if a service has been added programmatically (for instance, by an initializer)
        // filter out it (assuming it isn't annotated with @RegisterPlugin)
        .filter(srv -> srv.getInstance().getClass().getDeclaredAnnotation(RegisterPlugin.class) != null)
        .forEach(srv -> {
            var srvConfArgs = srv.getConfArgs();

            String uri;
            var mp = uriMatchPolicy(srv.getInstance());

            if (srvConfArgs == null || !srvConfArgs.containsKey("uri") || srvConfArgs.get("uri") == null) {
                uri = defaultURI(srv.getInstance());
            } else {
                if (!(srvConfArgs.get("uri") instanceof String)) {
                    LOGGER.error("Cannot start service {}: the configuration property 'uri' must be a string", srv.getName());

                    return;
                } else {
                    uri = (String) srvConfArgs.get("uri");
                }
            }

            if (uri == null) {
                LOGGER.error("Cannot start service {}: the configuration property 'uri' is not defined and the service does not have a default value", srv.getName());
                return;
            }

            if (!uri.startsWith("/")) {
                LOGGER.error("Cannot start service {}: the configuration property 'uri' must start with /", srv.getName(), uri);

                return;
            }

            var secured = srv.isSecure();

            PluginsRegistryImpl.getInstance().plugService(srv, uri, mp, secured);

            LOGGER.info(ansi().fg(GREEN).a("URI {} bound to service {}, secured: {}, uri match {}").reset().toString(), uri, srv.getName(), secured, mp);
        });
    }

    /**
     * plugProxies
     *
     * @param conf
     * @param paths
     * @param authMechanisms
     * @param identityManager
     * @param authorizers
     */
    private static void plugProxies(final Configuration conf, final Set<PluginRecord<AuthMechanism>> authMechanisms, final Set<PluginRecord<Authorizer>> authorizers, final PluginRecord<TokenManager> tokenManager) {
        if (conf.getProxies() == null || conf.getProxies().isEmpty()) {
            LOGGER.debug("No proxy specified");
            return;
        }

        conf.getProxies().stream().forEachOrdered((ProxiedResource proxy) -> {
            if (proxy.location() == null || proxy.proxyPass() == null || proxy.proxyPass().isEmpty()) {
                LOGGER.warn("Invalid proxies entry: {}", proxy);
                return;
            }

            var proxyClient = new LoadBalancingProxyClient()
                .setConnectionsPerThread(proxy.connectionPerThread())
                .setSoftMaxConnectionsPerThread(proxy.softMaxConnectionsPerThread())
                .setMaxQueueSize(proxy.maxQueueSize())
                .setProblemServerRetry(proxy.problemServerRetry())
                .setTtl(proxy.connectionsTTL());

            proxy.proxyPass().stream().forEach(pp -> {
                try {
                    var byteBufferPool = new ThreadAwareByteBufferPool(
                        configuration.coreModule().directBuffers(),
                        configuration.coreModule().bufferSize(),
                        configuration.coreModule().buffersPooling());

                    var xnioSsl = new UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY, byteBufferPool);
                    var uri = new URI(pp);
                    proxyClient.addHost(uri, xnioSsl);
                } catch(URISyntaxException t) {
                    LOGGER.warn("Invalid location URI {}, resource {} not bound ", proxy.location(), pp);
                } catch (GeneralSecurityException ex) {
                    logErrorAndExit("error configuring ssl", ex, false, -13);
                }
            });

            var proxyHandler = ProxyHandler.builder()
                .setRewriteHostHeader(proxy.rewriteHostHeader())
                .setProxyClient(proxyClient)
                .build();

            var rhProxy = pipe(
                new WorkingThreadsPoolDispatcher(),
                new PipelineInfoInjector(),
                new TracingInstrumentationHandler(),
                new RequestLogger(),
                new ProxyExchangeBuffersCloser(),
                new BeforeExchangeInitInterceptorsExecutor(),
                new RequestContentInjector(ON_REQUIRES_CONTENT_BEFORE_AUTH),
                new RequestInterceptorsExecutor(REQUEST_BEFORE_AUTH),
                new QueryStringRebuilder(),
                new SecurityHandler(authMechanisms, authorizers, tokenManager),
                new AuthHeadersRemover(),
                new XForwardedHeadersInjector(),
                new RequestContentInjector(ON_REQUIRES_CONTENT_AFTER_AUTH),
                new RequestInterceptorsExecutor(REQUEST_AFTER_AUTH),
                new QueryStringRebuilder(),
                new ConduitInjector(),
                PipelinedWrappingHandler.wrap(new ConfigurableEncodingHandler(proxyHandler))); // Must be after ConduitInjector

            PluginsRegistryImpl.getInstance().plugPipeline(proxy.location(), rhProxy, new PipelineInfo(PROXY, proxy.location(), proxy.name()));

            LOGGER.info(ansi().fg(GREEN).a("URI {} bound to proxy resource {}").reset().toString(), proxy.location(), proxy.proxyPass());
        });
    }

    /**
     * plugStaticResourcesHandlers
     *
     * plug the static resources specified in the configuration file
     *
     * @param conf
     * @param pathHandler
     * @param authenticationMechanism
     * @param identityManager
     * @param accessManager
     */
    private static void plugStaticResourcesHandlers(final Configuration conf) {
        if (conf.getStaticResources() == null || conf.getStaticResources().isEmpty()) {
            LOGGER.debug("No static resource specified");
            return;
        }

        conf.getStaticResources().stream().forEach(sr -> {
            try {
                if (sr.where() == null || !sr.where().startsWith("/")) {
                    LOGGER.error("Cannot bind static resources {}. parameter 'where' must start with /", sr);
                    return;
                }

                if (sr.what() == null) {
                    LOGGER.error("Cannot bind static resources to {}. missing parameter 'what'", sr);
                    return;
                }

                File file;

                if (sr.embedded()) {
                    if (sr.what() == null) {
                        LOGGER.error("Cannot bind embedded static resources {}. parameter 'what' is missing", sr);
                        return;
                    }

                    try {
                        file = ResourcesExtractor.extract(Bootstrapper.class, sr.what());

                        if (ResourcesExtractor.isResourceInJar(Bootstrapper.class, sr.what())) {
                            TMP_EXTRACTED_FILES.put(sr.what(), file);
                            LOGGER.info("Embedded static resources {} extracted in {}", sr.what(), file.toString());
                        }
                    } catch (URISyntaxException | IOException | IllegalStateException ex) {
                        LOGGER.error("Error extracting embedded static resource {}", sr.what(), ex);
                        return;
                    }
                } else if (!sr.what().startsWith("/")) {
                    // this is to allow specifying the configuration file path relative
                    // to the jar (also working when running from classes)
                    var location = Bootstrapper.class.getProtectionDomain().getCodeSource().getLocation();

                    var locationFile = new File(location.getPath());

                    var _path = Paths.get(locationFile.getParent().concat(File.separator).concat(sr.what()));

                    // normalize addresses https://issues.jboss.org/browse/UNDERTOW-742
                    file = _path.normalize().toFile();
                } else {
                    file = new File(sr.what());
                }

                if (file.exists()) {
                    var handler = resource(new FileResourceManager(file, 3)).addWelcomeFiles(sr.welcomeFile()).setDirectoryListingEnabled(false);

                    var ph = PipelinedHandler.pipe(new PipelineInfoInjector(), new RequestLogger(), new WorkingThreadsPoolDispatcher(PipelinedWrappingHandler.wrap(handler)));

                    PluginsRegistryImpl.getInstance().plugPipeline(sr.where(), ph, new PipelineInfo(STATIC_RESOURCE, sr.where(), sr.what()));

                    LOGGER.info(ansi().fg(GREEN).a("URI {} bound to static resource {}").reset().toString(), sr.where(), file.getAbsolutePath());
                } else {
                    LOGGER.error("Failed to bind URL {} to static resources {}. Directory does not exist.", sr.where(), sr.what());
                }

            } catch (Throwable t) {
                LOGGER.error("Cannot bind static resource", sr, t);
            }
        });
    }

    /**
     * logErrorAndExit
     *
     * @param message
     * @param t
     * @param silent
     * @param removePid
     * @param status
     */
    private static void logErrorAndExit(String message, Throwable t, boolean silent, boolean removePid, int status) {
        if (t == null) {
            LOGGER.error(message);
        } else if (t instanceof ConfigurationException ce) {
            if (ce.shoudlPrintStackTrace()) {
                LOGGER.error(message, t);
            } else {
                LOGGER.error(message);
            }
        } else {
            LOGGER.error(message, t);
        }

        stopServer(silent, removePid);
        System.exit(status);
    }

    /**
     * Disables JIT compilation for Truffle in environments using Virtual Threads, as of version 24.
     * Since JIT compilation conflicts with Virtual Threads, the 'truffle-runtime' is excluded from 'restheart.jar'.
     * The dependency for 'truffle-runtime' is marked as 'provided' in the project's pom.xml, facilitating its exclusion.
     *
     * This method addresses and suppresses the following warning:
     * WARNING: The polyglot engine uses a fallback runtime that does not support runtime compilation to machine code.
     * Execution without runtime compilation will negatively impact the performance of the guest application.
     */
    private static  void doNotWarnTruffleInterpreterOnly() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    @Command(name="java -jar restheart.jar")
    private static class Args {
        @Parameters(index = "0", arity = "0..1", paramLabel = "CONF_FILE", description = "Main configuration file")
        private String configPath = null;

        @Option(names = "--fork", description = "Fork the process in background")
        private boolean isForked = false;

        @Option(names = {"-o", "--rho" }, paramLabel = "RHO_FILE", description = "Configuration overrides file")
        private String rho = null;

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "This help message")
        private boolean help = false;

        @Option(names = {"-c", "--printConfiguration"}, description = "Print the effective configuration to the standard error and exit")
        private boolean printConfiguration = false;

        @Option(names = {"-t", "--printConfigurationTemplate"}, description = "Print the configuration template to the standard error and exit")
        private boolean printConfigurationTemplate = false;

        @Option(names = { "-v", "--version" }, versionHelp = true, description = "Print product version to the output stream and exit")
        boolean versionRequested;

        @Option(names = { "-s", "--standalone" }, description = "Use an alternate configuration that disables all plugins depending from MongoDB")
        boolean standalone;
    }
}
