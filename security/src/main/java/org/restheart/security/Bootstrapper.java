/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security;

import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import static com.sun.akuma.CLibrary.LIBC;
import org.restheart.security.handlers.ConfigurableEncodingHandler;
import static org.restheart.security.handlers.exchange.AbstractExchange.MAX_CONTENT_SIZE;
import org.restheart.security.handlers.exchange.AbstractExchange.METHOD;
import org.restheart.security.handlers.RequestNotManagedHandler;
import static io.undertow.Handlers.path;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.MAGENTA;
import static org.fusesource.jansi.Ansi.Color.RED;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.fusesource.jansi.AnsiConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;
import org.xnio.Xnio;
import org.xnio.ssl.XnioSsl;

import org.restheart.security.handlers.ErrorHandler;
import org.restheart.security.handlers.PipedHttpHandler;
import org.restheart.security.handlers.PipedWrappingHandler;
import org.restheart.security.handlers.injectors.RequestContentInjector;
import org.restheart.security.handlers.RequestLogger;
import org.restheart.security.handlers.RequestInterceptorsExecutor;
import org.restheart.security.handlers.ResponseSender;
import org.restheart.security.handlers.injectors.XForwardedHeadersInjector;
import org.restheart.security.handlers.injectors.AuthHeadersRemover;
import org.restheart.security.handlers.injectors.ConduitInjector;
import org.restheart.security.handlers.injectors.XPoweredByInjector;
import org.restheart.security.handlers.CORSHandler;
import org.restheart.security.handlers.SecurityHandler;
import org.restheart.security.plugins.PluginsRegistry;
import org.restheart.security.plugins.authorizers.FullAuthorizer;
import org.restheart.security.plugins.Service;
import org.restheart.security.utils.FileUtils;
import org.restheart.security.utils.LoggingInitializer;
import org.restheart.security.utils.OSChecker;
import org.restheart.security.utils.ResourcesExtractor;
import org.restheart.security.utils.RESTHeartSecurityDaemon;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.UndertowOptions;
import io.undertow.server.handlers.AllowedMethodsHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.HttpContinueAcceptingHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.HttpString;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.restheart.security.plugins.TokenManager;
import org.restheart.security.plugins.Authorizer;
import org.restheart.security.plugins.AuthMechanism;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Bootstrapper {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(Bootstrapper.class);

    private static final Map<String, File> TMP_EXTRACTED_FILES = new HashMap<>();

    private static Path CONF_FILE_PATH;

    private static PathHandler rootPathHandler = path();

    private static GracefulShutdownHandler HANDLERS = null;
    private static Configuration configuration;
    private static Undertow undertowServer;

    private static final String EXITING = ", exiting...";
    private static final String INSTANCE = " instance ";
    private static final String STARTING = "Starting ";
    private static final String UNDEFINED = "undefined";
    private static final String RESTHeartSecurity = "RESTHeart Security";
    private static final String VERSION = "Version {}";

    private Bootstrapper() {
    }

    /**
     * getConfiguration
     *
     * @return the global configuration
     */
    public static Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Allows to programmatically add handlers to the root path handler
     *
     * @see Path.addPrefixPath()
     *
     * @return the restheart root path handler
     */
    public static PathHandler getRootPathHandler() {
        return rootPathHandler;
    }

    /**
     * main method
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        CONF_FILE_PATH = FileUtils.getConfigurationFilePath(args);

        try {
            // read configuration silently, to avoid logging before initializing the logger
            configuration = FileUtils.getConfiguration(args, true);
            LOGGER.debug(configuration.toString());

            if (!configuration.isAnsiConsole()) {
                AnsiConsole.systemInstall();
            }
            LOGGER.debug("ANSI colored console: "
                    + ansi().fg(RED).bold().a(configuration.isAnsiConsole())
                            .reset().toString());
        }
        catch (ConfigurationException ex) {
            LOGGER.info(STARTING + ansi().fg(RED).bold().a(RESTHeartSecurity).reset().toString()
                    + INSTANCE
                    + ansi().fg(RED).bold().a(UNDEFINED).reset().toString());

            if (VERSION != null) {
                LOGGER.info(VERSION,
                        ansi().fg(MAGENTA).bold()
                                .a(Configuration.VERSION)
                                .reset().toString());
            }

            logErrorAndExit(ex.getMessage() + EXITING, ex, false, -1);
        }

        // configuration from JsonPath
        com.jayway.jsonpath.Configuration.setDefaults(
                new com.jayway.jsonpath.Configuration.Defaults() {
            private final JsonProvider jsonProvider = new GsonJsonProvider();
            private final MappingProvider mappingProvider = new GsonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<com.jayway.jsonpath.Option> options() {
                return EnumSet.noneOf(com.jayway.jsonpath.Option.class);
            }
        });

        if (!hasForkOption(args)) {
            initLogging(args, null);
            startServer(false);
        } else {
            if (OSChecker.isWindows()) {
                logWindowsStart();

                LOGGER.error("Fork is not supported on Windows");

                LOGGER.info(ansi().fg(GREEN).bold().a("RESTHeart Security stopped")
                        .reset().toString());

                System.exit(-1);
            }

            // RHSecDaemon only works on POSIX OSes
            final boolean isPosix = FileSystems.getDefault()
                    .supportedFileAttributeViews()
                    .contains("posix");

            if (!isPosix) {
                logErrorAndExit("Unable to fork process, "
                        + "this is only supported on POSIX compliant OSes",
                        null,
                        false,
                        -1);
            }

            RESTHeartSecurityDaemon d = new RESTHeartSecurityDaemon();

            if (d.isDaemonized()) {
                try {
                    d.init();
                    LOGGER.info("Forked process: {}", LIBC.getpid());
                    initLogging(args, d);
                }
                catch (Exception t) {
                    logErrorAndExit("Error staring forked process", t,
                            false,
                            false,
                            -1);
                }

                startServer(true);
            } else {
                initLogging(args, d);

                try {
                    String instanceName = getInstanceName();

                    LOGGER.info(STARTING
                            + ansi().fg(RED).bold().a(RESTHeartSecurity).reset().toString()
                            + INSTANCE
                            + ansi().fg(RED).bold().a(instanceName).reset()
                                    .toString());

                    if (Configuration.VERSION != null) {
                        LOGGER.info(VERSION, Configuration.VERSION);
                    }

                    logLoggingConfiguration(true);

                    d.daemonize();
                }
                catch (Throwable t) {
                    logErrorAndExit("Error forking", t, false, false, -1);
                }
            }
        }
    }

    private static void logWindowsStart() {
        String instanceName = getInstanceName();

        LOGGER.info(STARTING + ansi().fg(RED).bold().a(RESTHeartSecurity).reset().toString()
                + INSTANCE
                + ansi().fg(RED).bold().a(instanceName).reset().toString());

        if (Configuration.VERSION != null) {
            LOGGER.info(VERSION, Configuration.VERSION);
        }
    }

    /**
     * logs warning message if pid file exists
     *
     * @param confFilePath
     * @return true if pid file exists
     */
    private static boolean checkPidFile(Path confFilePath) {
        if (OSChecker.isWindows()) {
            return false;
        }

        // pid file name include the hash of the configuration file so that
        // for each configuration we can have just one instance running
        Path pidFilePath = FileUtils.getPidFilePath(FileUtils
                .getFileAbsoultePathHash(confFilePath));

        if (Files.exists(pidFilePath)) {
            LOGGER.warn(
                    "Found pid file! If this instance is already "
                    + "running, startup will fail with a BindException");

            return true;
        }

        return false;
    }

    /**
     * Startup the server
     *
     * @param confFilePath the path of the configuration file
     */
    public static void startup(final String confFilePath) {
        startup(FileUtils.getFileAbsoultePath(confFilePath));
    }

    /**
     * Startup the server
     *
     * @param confFilePath the path of the configuration file
     */
    public static void startup(final Path confFilePath) {
        try {
            configuration = FileUtils.getConfiguration(confFilePath, false);
        }
        catch (ConfigurationException ex) {
            if (VERSION != null) {
                LOGGER.info(ansi().fg(RED).bold().a(RESTHeartSecurity).reset().toString()
                        + " version {}",
                        ansi().fg(MAGENTA).bold().a(Configuration.VERSION)
                                .reset().toString());
            }

            logErrorAndExit(ex.getMessage() + EXITING, ex, false, -1);
        }

        startServer(false);
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
     * initLogging
     *
     * @param args
     * @param d
     */
    private static void initLogging(final String[] args, final RESTHeartSecurityDaemon d) {
        LoggingInitializer.setLogLevel(configuration
                .getLogLevel());

        if (d != null && d.isDaemonized()) {
            LoggingInitializer.stopConsoleLogging();
            LoggingInitializer.startFileLogging(configuration
                    .getLogFilePath());
        } else if (!hasForkOption(args)) {
            if (!configuration.isLogToConsole()) {
                LoggingInitializer.stopConsoleLogging();
            }
            if (configuration.isLogToFile()) {
                LoggingInitializer.startFileLogging(configuration
                        .getLogFilePath());
            }
        }
    }

    /**
     * logLoggingConfiguration
     *
     * @param fork
     */
    private static void logLoggingConfiguration(boolean fork) {
        String logbackConfigurationFile = System
                .getProperty("logback.configurationFile");

        boolean usesLogback = logbackConfigurationFile != null
                && !logbackConfigurationFile.isEmpty();

        if (usesLogback) {
            return;
        }

        if (configuration.isLogToFile()) {
            LOGGER.info("Logging to file {} with level {}",
                    configuration.getLogFilePath(),
                    configuration.getLogLevel());
        }

        if (!fork) {
            if (!configuration.isLogToConsole()) {
                LOGGER.info("Stop logging to console ");
            } else {
                LOGGER.info("Logging to console with level {}",
                        configuration.getLogLevel());
            }
        }
    }

    /**
     * hasForkOption
     *
     * @param args
     * @return true if has fork option
     */
    private static boolean hasForkOption(final String[] args) {
        if (args == null || args.length < 1) {
            return false;
        }

        for (String arg : args) {
            if (arg.equals("--fork")) {
                return true;
            }
        }

        return false;
    }

    /**
     * startServer
     *
     * @param fork
     */
    private static void startServer(boolean fork) {
        logWindowsStart();

        Path pidFilePath = FileUtils.getPidFilePath(FileUtils
                .getFileAbsoultePathHash(CONF_FILE_PATH));

        boolean pidFileAlreadyExists = false;

        if (!OSChecker.isWindows() && pidFilePath != null) {
            pidFileAlreadyExists = checkPidFile(CONF_FILE_PATH);
        }

        logLoggingConfiguration(fork);

        // re-read configuration file, to log errors new that logger is initialized 
        try {
            FileUtils.getConfiguration(CONF_FILE_PATH, false);
        }
        catch (ConfigurationException ex) {
            logErrorAndExit(ex.getMessage() + EXITING, ex, false, -1);
        }

        // run pre startup initializers
        PluginsRegistry.getInstance()
                .getPreStartupInitializers()
                .stream()
                .forEach(i -> {
                    try {
                        i.getInstance().init();
                    }
                    catch (Throwable t) {
                        LOGGER.error("Error executing initializer {}", i.getName());
                    }
                });

        try {
            startCoreSystem();
        }
        catch (Throwable t) {
            logErrorAndExit("Error starting RESTHeart Security. Exiting...",
                    t,
                    false,
                    !pidFileAlreadyExists, -2);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                stopServer(false);
            }
        });

        // create pid file on supported OSes
        if (!OSChecker.isWindows() && pidFilePath != null) {
            FileUtils.createPidFile(pidFilePath);
        }

        // log pid file path on supported OSes
        if (!OSChecker.isWindows() && pidFilePath != null) {
            LOGGER.info("Pid file {}", pidFilePath);
        }

        // run initializers
        PluginsRegistry.getInstance()
                .getInitializers()
                .stream()
                .forEach(i -> {
                    try {
                        i.getInstance().init();
                    }
                    catch (Throwable t) {
                        LOGGER.error("Error executing initializer {}", i.getName());
                    }
                });

        LOGGER.info(ansi().fg(GREEN).bold().a("RESTHeart Security started").reset().toString());
    }

    private static String getInstanceName() {
        return configuration == null ? UNDEFINED
                : configuration.getInstanceName() == null
                ? UNDEFINED
                : configuration.getInstanceName();
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
            LOGGER.info("Stopping RESTHeart Security...");
        }

        if (HANDLERS != null) {
            if (!silent) {
                LOGGER.info("Waiting for pending request "
                        + "to complete (up to 1 minute)...");
            }
            try {
                HANDLERS.shutdown();
                HANDLERS.awaitShutdown(60 * 1000); // up to 1 minute
            }
            catch (InterruptedException ie) {
                LOGGER.error("Error while waiting for pending request "
                        + "to complete", ie);
                Thread.currentThread().interrupt();
            }
        }

        Path pidFilePath = FileUtils.getPidFilePath(
                FileUtils.getFileAbsoultePathHash(CONF_FILE_PATH));

        if (removePid && pidFilePath != null) {
            if (!silent) {
                LOGGER.info("Removing the pid file {}",
                        pidFilePath.toString());
            }
            try {
                Files.deleteIfExists(pidFilePath);
            }
            catch (IOException ex) {
                LOGGER.error("Can't delete pid file {}",
                        pidFilePath.toString(), ex);
            }
        }

        if (!silent) {
            LOGGER.info("Cleaning up temporary directories...");
        }
        TMP_EXTRACTED_FILES.keySet().forEach(k -> {
            try {
                ResourcesExtractor.deleteTempDir(k, TMP_EXTRACTED_FILES.get(k));
            }
            catch (URISyntaxException | IOException ex) {
                LOGGER.error("Error cleaning up temporary directory {}",
                        TMP_EXTRACTED_FILES.get(k).toString(), ex);
            }
        });

        if (undertowServer != null) {
            undertowServer.stop();
        }

        if (!silent) {
            LOGGER.info(ansi().fg(GREEN).bold().a("RESTHeart Security stopped")
                    .reset().toString());
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

        if (!configuration.isHttpsListener()
                && !configuration.isHttpListener()) {
            logErrorAndExit("No listener specified. exiting..", null, false, -1);
        }

        final TokenManager tokenManager = loadTokenManager();

        final List<AuthMechanism> authMechanisms = authMechanisms();

        final LinkedHashSet<Authorizer> authorizers = authorizers();

        SSLContext sslContext = null;

        try {
            sslContext = SSLContext.getInstance("TLS");

            KeyManagerFactory kmf = KeyManagerFactory
                    .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

            if (getConfiguration().isUseEmbeddedKeystore()) {
                char[] storepass = "uiamuiam".toCharArray();
                char[] keypass = "uiamuiam".toCharArray();

                String storename = "sskeystore.jks";

                ks.load(Bootstrapper.class.getClassLoader()
                        .getResourceAsStream(storename), storepass);
                kmf.init(ks, keypass);
            } else if (configuration.getKeystoreFile() != null
                    && configuration.getKeystorePassword() != null
                    && configuration.getCertPassword() != null) {
                try ( FileInputStream fis = new FileInputStream(
                        new File(configuration.getKeystoreFile()))) {
                    ks.load(fis, configuration.getKeystorePassword().toCharArray());
                    kmf.init(ks, configuration.getCertPassword().toCharArray());
                }
            } else {
                LOGGER.error(
                        "The keystore is not configured. "
                        + "Check the keystore-file, "
                        + "keystore-password and certpassword options.");
            }

            tmf.init(ks);

            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        }
        catch (KeyManagementException
                | NoSuchAlgorithmException
                | KeyStoreException
                | CertificateException
                | UnrecoverableKeyException ex) {
            logErrorAndExit(
                    "Couldn't start RESTHeart Security, error with specified keystore. "
                    + "Check the keystore-file, "
                    + "keystore-password and certpassword options. Exiting..",
                    ex, false, -1);
        }
        catch (FileNotFoundException ex) {
            logErrorAndExit(
                    "Couldn't start RESTHeart Security, keystore file not found. "
                    + "Check the keystore-file, "
                    + "keystore-password and certpassword options. Exiting..",
                    ex, false, -1);
        }
        catch (IOException ex) {
            logErrorAndExit(
                    "Couldn't start RESTHeart Security, error reading the keystore file. "
                    + "Check the keystore-file, "
                    + "keystore-password and certpassword options. Exiting..",
                    ex, false, -1);
        }

        Builder builder = Undertow.builder();

        if (configuration.isHttpsListener()) {
            builder.addHttpsListener(configuration.getHttpsPort(),
                    configuration.getHttpsHost(),
                    sslContext);

            if (configuration.getHttpsHost().equals("127.0.0.1")
                    || configuration.getHttpsHost().equalsIgnoreCase("localhost")) {
                LOGGER.warn("HTTPS listener bound to localhost:{}. "
                        + "Remote systems will be unable to connect to this server.",
                        configuration.getHttpsPort());
            } else {
                LOGGER.info("HTTPS listener bound at {}:{}",
                        configuration.getHttpsHost(), configuration.getHttpsPort());
            }
        }

        if (configuration.isHttpListener()) {
            builder.addHttpListener(configuration.getHttpPort(),
                    configuration.getHttpHost());

            if (configuration.getHttpHost().equals("127.0.0.1")
                    || configuration.getHttpHost().equalsIgnoreCase("localhost")) {
                LOGGER.warn("HTTP listener bound to localhost:{}. "
                        + "Remote systems will be unable to connect to this server.",
                        configuration.getHttpPort());
            } else {
                LOGGER.info("HTTP listener bound at {}:{}",
                        configuration.getHttpHost(), configuration.getHttpPort());
            }
        }

        HANDLERS = getHandlersPipe(authMechanisms,
                authorizers,
                tokenManager);

        builder = builder
                .setIoThreads(configuration.getIoThreads())
                .setWorkerThreads(configuration.getWorkerThreads())
                .setDirectBuffers(configuration.isDirectBuffers())
                .setBufferSize(configuration.getBufferSize())
                .setHandler(HANDLERS);

        // starting from undertow 1.4.23 URL checks become much stricter
        // (undertow commit 09d40a13089dbff37f8c76d20a41bf0d0e600d9d)
        // allow unescaped chars in URL (otherwise not allowed by default)
        builder.setServerOption(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL,
                configuration.isAllowUnescapedCharactersInUrl());

        LOGGER.info("Allow unescaped characters in URL: {}",
                configuration.isAllowUnescapedCharactersInUrl());

        ConfigurationHelper.setConnectionOptions(builder, configuration);

        undertowServer = builder.build();
        undertowServer.start();
    }

    /**
     *
     * @return the AuthenticationMechanisms
     */
    private static List<AuthMechanism> authMechanisms() {
        var authMechanisms = new ArrayList<AuthMechanism>();

        if (configuration.getAuthMechanisms() != null
                && !configuration.getAuthMechanisms().isEmpty()) {
            configuration.getAuthMechanisms().stream()
                    .map(am -> am.get(ConfigurationKeys.NAME_KEY))
                    .filter(name -> name instanceof String)
                    .map(name -> (String) name)
                    .forEachOrdered(name -> {

                        try {
                            authMechanisms.add(PluginsRegistry.getInstance()
                                    .getAuthenticationMechanism(name));

                            LOGGER.info("Authentication Mechanism {} enabled",
                                    name);
                        }
                        catch (ConfigurationException pcex) {
                            logErrorAndExit(pcex.getMessage(), pcex, false, -3);
                        }
                    });
        } else {
            LOGGER.warn("***** No Authentication Mechanism specified. "
                    + "Authentication disabled.");
        }

        return authMechanisms;
    }

    /**
     *
     * @return the AuthenticationMechanisms
     */
    private static LinkedHashSet<Authorizer> authorizers() {
        var authorizers = new LinkedHashSet<Authorizer>();

        if (configuration.getAuthorizers() != null
                && !configuration.getAuthorizers().isEmpty()) {
            configuration.getAuthorizers().stream()
                    .map(am -> am.get(ConfigurationKeys.NAME_KEY))
                    .filter(name -> name instanceof String)
                    .map(name -> (String) name)
                    .forEachOrdered(name -> {

                        try {
                            authorizers.add(PluginsRegistry.getInstance()
                                    .getAuthorizer(name));

                            LOGGER.info("Authorizer {} enabled",
                                    name);
                        }
                        catch (ConfigurationException pcex) {
                            logErrorAndExit(pcex.getMessage(), pcex, false, -3);
                        }
                    });
        } else {
            LOGGER.warn("***** No Authorizer specified. "
                    + "All requests are allowed.");
        }

        return authorizers;
    }

    /**
     * logErrorAndExit
     *
     * @param message
     * @param t
     * @param silent
     * @param status
     */
    private static void logErrorAndExit(String message,
            Throwable t,
            boolean silent,
            int status) {
        logErrorAndExit(message, t, silent, true, status);
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
    private static void logErrorAndExit(String message,
            Throwable t,
            boolean silent,
            boolean removePid,
            int status) {
        if (t == null) {
            LOGGER.error(message);
        } else {
            LOGGER.error(message, t);
        }
        stopServer(silent, removePid);
        System.exit(status);
    }

    /**
     * getHandlersPipe
     *
     * @param identityManager
     * @param authorizers
     * @param tokenManager
     * @return a GracefulShutdownHandler
     */
    private static GracefulShutdownHandler getHandlersPipe(
            final List<AuthMechanism> authMechanisms,
            final LinkedHashSet<Authorizer> authorizers,
            final TokenManager tokenManager
    ) {
        getRootPathHandler().addPrefixPath("/", new RequestNotManagedHandler());

        LOGGER.info("Content buffers maximun size "
                + "is {} bytes",
                MAX_CONTENT_SIZE);

        plugServices(configuration, getRootPathHandler(),
                authMechanisms, authorizers, tokenManager);

        proxyResources(configuration, getRootPathHandler(),
                authMechanisms, authorizers, tokenManager);

        return buildGracefulShutdownHandler(getRootPathHandler());
    }

    /**
     * buildGracefulShutdownHandler
     *
     * @param paths
     * @return
     */
    private static GracefulShutdownHandler buildGracefulShutdownHandler(
            PathHandler paths) {
        return new GracefulShutdownHandler(new RequestLimitingHandler(
                new RequestLimit(configuration.getRequestsLimit()),
                new AllowedMethodsHandler(
                        new RequestContentInjector(
                                new BlockingHandler(
                                        new ConduitInjector(
                                                new PipedWrappingHandler(null,
                                                        new ConfigurableEncodingHandler(
                                                                new ErrorHandler(
                                                                        new HttpContinueAcceptingHandler(paths)),
                                                                configuration.isForceGzipEncoding())
                                                )))),
                        // allowed methods
                        HttpString.tryFromString(METHOD.GET.name()),
                        HttpString.tryFromString(METHOD.POST.name()),
                        HttpString.tryFromString(METHOD.PUT.name()),
                        HttpString.tryFromString(METHOD.DELETE.name()),
                        HttpString.tryFromString(METHOD.PATCH.name()),
                        HttpString.tryFromString(METHOD.OPTIONS.name()))));
    }

    /**
     * plugServices
     *
     * @param conf
     * @param paths
     * @param authMechanisms
     * @param identityManager
     * @param authorizers
     */
    private static void plugServices(final Configuration conf,
            final PathHandler paths,
            final List<AuthMechanism> authMechanisms,
            final LinkedHashSet<Authorizer> authorizers,
            final TokenManager tokenManager) {
        if (!conf.getServices().isEmpty()) {
            conf.getServices().stream()
                    .map(am -> am.get(ConfigurationKeys.NAME_KEY))
                    .filter(name -> name instanceof String)
                    .map(name -> (String) name)
                    .forEachOrdered(name -> {
                        try {
                            Service _srv = PluginsRegistry.getInstance()
                                    .getService(name);

                            var srv = new PipedWrappingHandler(
                                    new ResponseSender(),
                                    _srv);

                            if (_srv.getSecured()) {
                                paths.addPrefixPath(_srv.getUri(), new RequestLogger(
                                        new CORSHandler(
                                                new XPoweredByInjector(
                                                        new RequestInterceptorsExecutor(
                                                                new SecurityHandler(srv,
                                                                        authMechanisms,
                                                                        authorizers,
                                                                        tokenManager))))));
                            } else {
                                var _fauthorizers = new LinkedHashSet<Authorizer>();
                                _fauthorizers.add(new FullAuthorizer(false));

                                paths.addPrefixPath(_srv.getUri(), new RequestLogger(
                                        new CORSHandler(
                                                new XPoweredByInjector(
                                                        new RequestInterceptorsExecutor(
                                                                new SecurityHandler(srv,
                                                                        authMechanisms,
                                                                        _fauthorizers,
                                                                        tokenManager))))));
                            }

                            LOGGER.info("URI {} bound to service {}, secured: {}",
                                    _srv.getUri(),
                                    _srv.getName(),
                                    _srv.getSecured());

                        }
                        catch (ConfigurationException pce) {
                            LOGGER.error("Error plugging in the service", pce);
                        }
                    });
        }
    }

    /**
     * loadTokenManager
     *
     * @return the TokenManager, or null if it is not configured
     */
    private static TokenManager loadTokenManager() {
        final Map<String, Object> tokenManager = configuration.getTokenManager();

        if (tokenManager == null || tokenManager.isEmpty()) {
            return null;
        }

        try {
            return PluginsRegistry.getInstance().getTokenManager();
        }
        catch (ConfigurationException pce) {
            LOGGER.error("Error configuring token manager", pce);
            return null;
        }
    }

    /**
     * proxyResources
     *
     * @param conf
     * @param paths
     * @param authMechanisms
     * @param identityManager
     * @param authorizers
     */
    private static void proxyResources(final Configuration conf,
            final PathHandler paths,
            final List<AuthMechanism> authMechanisms,
            final LinkedHashSet<Authorizer> authorizers,
            final TokenManager tokenManager) {
        if (conf.getProxies() == null || conf.getProxies().isEmpty()) {
            LOGGER.info("No {} specified", ConfigurationKeys.PROXY_KEY);
            return;
        }

        conf.getProxies().stream().forEachOrdered(m -> {
            String location = Configuration.getOrDefault(m,
                    ConfigurationKeys.PROXY_LOCATION_KEY, null, true);

            Object _proxyPass = Configuration.getOrDefault(m,
                    ConfigurationKeys.PROXY_PASS_KEY, null, true);

            if (location == null && _proxyPass != null) {
                LOGGER.warn("Location URI not specified for resource {} ",
                        _proxyPass);
                return;
            }

            if (location == null && _proxyPass == null) {
                LOGGER.warn("Invalid proxies entry detected");
                return;
            }

            // The number of connections to create per thread
            Integer connectionsPerThread = Configuration.getOrDefault(m,
                    ConfigurationKeys.PROXY_CONNECTIONS_PER_THREAD, 10,
                    true);

            Integer maxQueueSize = Configuration.getOrDefault(m,
                    ConfigurationKeys.PROXY_MAX_QUEUE_SIZE, 0, true);

            Integer softMaxConnectionsPerThread = Configuration.getOrDefault(m,
                    ConfigurationKeys.PROXY_SOFT_MAX_CONNECTIONS_PER_THREAD, 5, true);

            Integer ttl = Configuration.getOrDefault(m,
                    ConfigurationKeys.PROXY_TTL, -1, true);

            boolean rewriteHostHeader = Configuration.getOrDefault(m,
                    ConfigurationKeys.PROXY_REWRITE_HOST_HEADER, true, true);

            // Time in seconds between retries for problem server
            Integer problemServerRetry = Configuration.getOrDefault(m,
                    ConfigurationKeys.PROXY_PROBLEM_SERVER_RETRY, 10,
                    true);

            final Xnio xnio = Xnio.getInstance();

            final OptionMap optionMap = OptionMap.create(
                    Options.SSL_CLIENT_AUTH_MODE,
                    SslClientAuthMode.REQUIRED,
                    Options.SSL_STARTTLS,
                    true);

            XnioSsl sslProvider = null;

            try {
                sslProvider = xnio.getSslProvider(optionMap);
            }
            catch (GeneralSecurityException ex) {
                logErrorAndExit("error configuring ssl", ex, false, -13);
            }

            try {
                LoadBalancingProxyClient proxyClient
                        = new LoadBalancingProxyClient()
                                .setConnectionsPerThread(connectionsPerThread)
                                .setSoftMaxConnectionsPerThread(softMaxConnectionsPerThread)
                                .setMaxQueueSize(maxQueueSize)
                                .setProblemServerRetry(problemServerRetry)
                                .setTtl(ttl);

                if (_proxyPass instanceof String) {
                    proxyClient = proxyClient.addHost(
                            new URI((String) _proxyPass), sslProvider);
                } else if (_proxyPass instanceof List) {
                    for (Object proxyPassURL : ((List<?>) _proxyPass)) {
                        if (proxyPassURL instanceof String) {
                            proxyClient = proxyClient.addHost(
                                    new URI((String) proxyPassURL), sslProvider);
                        } else {
                            LOGGER.warn("Invalid proxy pass URL {}, location {} not bound ",
                                    proxyPassURL, location);
                        }
                    }
                } else {
                    LOGGER.warn("Invalid proxy pass URL {}, location {} not bound ",
                            _proxyPass);
                }

                ProxyHandler proxyHandler = ProxyHandler.builder()
                        .setRewriteHostHeader(rewriteHostHeader)
                        .setProxyClient(proxyClient)
                        .build();

                PipedHttpHandler wrappedProxyHandler
                        = new XForwardedHeadersInjector(
                                new XPoweredByInjector(
                                        new RequestInterceptorsExecutor(
                                                new PipedWrappingHandler(
                                                        null,
                                                        proxyHandler))));

                paths.addPrefixPath(location,
                        new RequestLogger(
                                new SecurityHandler(
                                        new AuthHeadersRemover(
                                                wrappedProxyHandler),
                                        authMechanisms,
                                        authorizers,
                                        tokenManager)));

                LOGGER.info("URI {} bound to resource {}", location, _proxyPass);
            }
            catch (URISyntaxException ex) {
                LOGGER.warn("Invalid location URI {}, resource {} not bound ",
                        location,
                        _proxyPass);
            }
        });
    }
}
