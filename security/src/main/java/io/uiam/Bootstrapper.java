/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam;

import static com.sun.akuma.CLibrary.LIBC;
import static io.uiam.Configuration.UIAM_VERSION;
import io.uiam.handlers.RequestNotManagedHandler;
import static io.undertow.Handlers.path;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.MAGENTA;
import static org.fusesource.jansi.Ansi.Color.RED;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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

import io.uiam.handlers.ErrorHandler;
import io.uiam.handlers.Request.METHOD;
import io.uiam.handlers.GzipEncodingHandler;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.PipedWrappingHandler;
import io.uiam.handlers.RequestContentInjector;
import io.uiam.handlers.RequestLoggerHandler;
import io.uiam.handlers.RequestInterceptorsHandler;
import io.uiam.handlers.ResponseSenderHandler;
import io.uiam.handlers.ResponseInterceptorsHandler;
import io.uiam.handlers.injectors.AccountHeadersInjector;
import io.uiam.handlers.injectors.AuthHeadersRemover;
import io.uiam.handlers.injectors.XPoweredByInjector;
import io.uiam.handlers.security.CORSHandler;
import io.uiam.handlers.security.SecurityHandler;
import io.uiam.plugins.PluginConfigurationException;
import io.uiam.plugins.PluginsFactory;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.uiam.plugins.authentication.PluggableTokenManager;
import io.uiam.plugins.authorization.PluggableAccessManager;
import io.uiam.plugins.authorization.impl.FullAccessManager;
import io.uiam.plugins.init.PluggableInitializer;
import io.uiam.plugins.service.PluggableService;
import io.uiam.utils.FileUtils;
import io.uiam.utils.LoggingInitializer;
import io.uiam.utils.OSChecker;
import io.uiam.utils.ResourcesExtractor;
import io.uiam.utils.uIAMDaemon;
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

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Bootstrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrapper.class);
    private static final Map<String, File> TMP_EXTRACTED_FILES = new HashMap<>();

    private static Path CONF_FILE_PATH;

    private static GracefulShutdownHandler HANDLERS = null;
    private static Configuration configuration;
    private static Undertow undertowServer;

    private static final String EXITING = ", exiting...";
    private static final String INSTANCE = " instance ";
    private static final String STARTING = "Starting ";
    private static final String UNDEFINED = "undefined";
    private static final String UIAM = "uIAM";
    private static final String VERSION = "version {}";

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
            LOGGER.info("ANSI colored console: "
                    + ansi().fg(RED).bold().a(configuration.isAnsiConsole())
                            .reset().toString());
        }
        catch (ConfigurationException ex) {
            LOGGER.info(STARTING + ansi().fg(RED).bold().a(UIAM).reset().toString()
                    + INSTANCE
                    + ansi().fg(RED).bold().a(UNDEFINED).reset().toString());

            if (UIAM_VERSION != null) {
                LOGGER.info(VERSION,
                        ansi().fg(MAGENTA).bold().a(UIAM_VERSION)
                                .reset().toString());
            }

            logErrorAndExit(ex.getMessage() + EXITING, ex, false, -1);
        }

        if (!hasForkOption(args)) {
            initLogging(args, null);
            startServer(false);
        } else {
            if (OSChecker.isWindows()) {
                logWindowsStart();

                LOGGER.error("Fork is not supported on Windows");

                LOGGER.info(ansi().fg(GREEN).bold().a("uIAM stopped")
                        .reset().toString());

                System.exit(-1);
            }

            // uIAMDaemon only works on POSIX OSes
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

            uIAMDaemon d = new uIAMDaemon();

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
                            + ansi().fg(RED).bold().a(UIAM).reset().toString()
                            + INSTANCE
                            + ansi().fg(RED).bold().a(instanceName).reset()
                                    .toString());

                    if (UIAM_VERSION != null) {
                        LOGGER.info(VERSION, UIAM_VERSION);
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

        LOGGER.info(STARTING + ansi().fg(RED).bold().a(UIAM).reset().toString()
                + INSTANCE
                + ansi().fg(RED).bold().a(instanceName).reset().toString());

        if (UIAM_VERSION != null) {
            LOGGER.info(VERSION,
                    ansi().fg(MAGENTA).bold().a(UIAM_VERSION).reset().toString());
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
     * Startup the uIAM server
     *
     * @param confFilePath the path of the configuration file
     */
    public static void startup(final String confFilePath) {
        startup(FileUtils.getFileAbsoultePath(confFilePath));
    }

    /**
     * Startup the uIAM server
     *
     * @param confFilePath the path of the configuration file
     */
    public static void startup(final Path confFilePath) {
        try {
            configuration = FileUtils.getConfiguration(confFilePath, false);
        }
        catch (ConfigurationException ex) {
            if (UIAM_VERSION != null) {
                LOGGER.info(ansi().fg(RED).bold().a(UIAM).reset().toString()
                        + " version {}",
                        ansi().fg(MAGENTA).bold().a(UIAM_VERSION)
                                .reset().toString());
            }

            logErrorAndExit(ex.getMessage() + EXITING, ex, false, -1);
        }

        startServer(false);
    }

    /**
     * Shutdown the uIAM server
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
    private static void initLogging(final String[] args, final uIAMDaemon d) {
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

        try {
            startCoreSystem();
        }
        catch (Throwable t) {
            logErrorAndExit("Error starting uIAM. Exiting...",
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

        // run initializer if defined
        if (configuration.getInitializerClass() != null) {
            try {
                Object o = Class.forName(configuration.getInitializerClass())
                        .getDeclaredConstructor().newInstance();

                if (o instanceof PluggableInitializer) {
                    try {
                        ((PluggableInitializer) o).init();
                        LOGGER.info("initializer {} executed",
                                configuration.getInitializerClass());
                    }
                    catch (Throwable t) {
                        LOGGER.error("Error executing intializer {}",
                                configuration.getInitializerClass(), t);
                    }
                }
            }
            catch (ClassNotFoundException
                    | NoSuchMethodException
                    | InvocationTargetException
                    | InstantiationException
                    | IllegalAccessException t) {
                LOGGER.error(ansi().fg(RED).bold()
                        .a("Wrong configuration for intializer {}")
                        .reset().toString(),
                        configuration.getInitializerClass(), t);
            }
        }

        LOGGER.info(ansi().fg(GREEN).bold().a("uIAM started").reset().toString());
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
            LOGGER.info("Stopping uIAM...");
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
            LOGGER.info(ansi().fg(GREEN).bold().a("uIAM stopped")
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
                && !configuration.isHttpListener()
                && !configuration.isAjpListener()) {
            logErrorAndExit("No listener specified. exiting..", null, false, -1);
        }

        final PluggableTokenManager tokenManager = loadTokenManager();

        final List<PluggableAuthenticationMechanism> authMechanisms = authMechanisms();

        final PluggableAccessManager accessManager = accessManager();

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
                try (FileInputStream fis = new FileInputStream(
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
                    "Couldn't start uIAM, error with specified keystore. "
                    + "Check the keystore-file, "
                    + "keystore-password and certpassword options. Exiting..",
                    ex, false, -1);
        }
        catch (FileNotFoundException ex) {
            logErrorAndExit(
                    "Couldn't start uIAM, keystore file not found. "
                    + "Check the keystore-file, "
                    + "keystore-password and certpassword options. Exiting..",
                    ex, false, -1);
        }
        catch (IOException ex) {
            logErrorAndExit(
                    "Couldn't start uIAM, error reading the keystore file. "
                    + "Check the keystore-file, "
                    + "keystore-password and certpassword options. Exiting..",
                    ex, false, -1);
        }

        Builder builder = Undertow.builder();

        if (configuration.isHttpsListener()) {
            builder.addHttpsListener(configuration.getHttpsPort(),
                    configuration.getHttpHost(),
                    sslContext);
            LOGGER.info("HTTPS listener bound at {}:{}",
                    configuration.getHttpsHost(),
                    configuration.getHttpsPort());
        }

        if (configuration.isHttpListener()) {
            builder.addHttpListener(configuration.getHttpPort(),
                    configuration.getHttpsHost());
            LOGGER.info("HTTP listener bound at {}:{}",
                    configuration.getHttpHost(),
                    configuration.getHttpPort());
        }

        if (configuration.isAjpListener()) {
            builder.addAjpListener(configuration.getAjpPort(),
                    configuration.getAjpHost());
            LOGGER.info("Ajp listener bound at {}:{}",
                    configuration.getAjpHost(),
                    configuration.getAjpPort());
        }

        HANDLERS = getHandlersPipe(authMechanisms,
                accessManager,
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
    private static List<PluggableAuthenticationMechanism> authMechanisms() {
        var authMechanisms = new ArrayList<PluggableAuthenticationMechanism>();

        if (configuration.getAuthMechanisms() != null
                && !configuration.getAuthMechanisms().isEmpty()) {
            configuration.getAuthMechanisms().stream().forEachOrdered(am -> {

                try {
                    authMechanisms.add(PluginsFactory
                            .getAutenticationMechanism(am));

                    LOGGER.info("Authentication Mechanism {} enabled",
                            am.get(Configuration.NAME_KEY));
                }
                catch (PluginConfigurationException pcex) {
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
     * @return the PluggableAccessManager
     */
    private static PluggableAccessManager accessManager() {
        if (configuration.getAccessManager() == null) {
            LOGGER.warn("***** No Access Manager specified. "
                    + "All requests are allowed.");
            if (configuration.getAuthMechanisms() != null
                    && configuration.getAuthMechanisms().size() > 1) {
                return new FullAccessManager(true);
            } else {
                return new FullAccessManager(false);
            }
        } else {
            try {
                return PluginsFactory.getAccessManager(configuration
                        .getAccessManager());
            }
            catch (PluginConfigurationException ex) {
                logErrorAndExit("Error configuring Access Manager implementation "
                        + configuration.getAccessManager(),
                        ex, false, -3);
                return null;
            }
        }
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
     * @param accessManager
     * @param tokenManager
     * @return a GracefulShutdownHandler
     */
    private static GracefulShutdownHandler getHandlersPipe(
            final List<PluggableAuthenticationMechanism> authMechanisms,
            final PluggableAccessManager accessManager,
            final PluggableTokenManager tokenManager
    ) {
        PathHandler paths = path();

        paths.addPrefixPath("/", new RequestNotManagedHandler());

        plugServices(configuration, paths,
                authMechanisms, accessManager, tokenManager);

        proxyResources(configuration, paths,
                authMechanisms, accessManager, tokenManager);

        return buildGracefulShutdownHandler(paths);
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
                                new GzipEncodingHandler(new ErrorHandler(
                                        new HttpContinueAcceptingHandler(paths)),
                                        configuration.isForceGzipEncoding())),
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
     * @param accessManager
     */
    private static void plugServices(final Configuration conf,
            final PathHandler paths,
            final List<PluggableAuthenticationMechanism> authMechanisms,
            final PluggableAccessManager accessManager,
            final PluggableTokenManager tokenManager) {
        if (!conf.getServices().isEmpty()) {
            conf.getServices().stream()
                    .forEach((Map<String, Object> serviceConf) -> {
                        try {
                            PluggableService _srv = PluginsFactory
                                    .getService(serviceConf);

                            var srv = new PipedWrappingHandler(
                                    new ResponseSenderHandler(),
                                    new PipedWrappingHandler(
                                            new ResponseInterceptorsHandler(),
                                            _srv));

                            if (_srv.getSecured()) {
                                paths.addPrefixPath(_srv.getUri(), new RequestLoggerHandler(
                                        new CORSHandler(
                                                new XPoweredByInjector(
                                                        new RequestInterceptorsHandler(
                                                                new SecurityHandler(srv,
                                                                        authMechanisms,
                                                                        accessManager,
                                                                        tokenManager))))));
                            } else {
                                paths.addPrefixPath(_srv.getUri(), new RequestLoggerHandler(
                                        new CORSHandler(
                                                new XPoweredByInjector(
                                                        new RequestInterceptorsHandler(
                                                                new SecurityHandler(srv,
                                                                        authMechanisms,
                                                                        new FullAccessManager(false),
                                                                        tokenManager))))));
                            }

                            LOGGER.info("URI {} bound to service {}, secured: {}",
                                    _srv.getUri(),
                                    _srv.getName(),
                                    _srv.getSecured());

                        }
                        catch (PluginConfigurationException pce) {
                            LOGGER.error("Error plugging in the service", pce);
                        }
                    });
        }
    }

    /**
     * loadTokenManager
     *
     * @return the PluggableTokenManager, or null if it is not configured
     */
    private static PluggableTokenManager loadTokenManager() {
        final Map<String, Object> tokenManager = configuration.getTokenManager();

        if (tokenManager == null || tokenManager.isEmpty()) {
            return null;
        }

        try {
            return PluginsFactory.getTokenManager(tokenManager);
        }
        catch (PluginConfigurationException pce) {
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
     * @param accessManager
     */
    private static void proxyResources(final Configuration conf,
            final PathHandler paths,
            final List<PluggableAuthenticationMechanism> authMechanisms,
            final PluggableAccessManager accessManager,
            final PluggableTokenManager tokenManager) {
        if (conf.getProxies() == null || conf.getProxies().isEmpty()) {
            LOGGER.info("No {} specified", Configuration.PROXY_KEY);
            return;
        }

        conf.getProxies().stream().forEachOrdered(m -> {
            String uri = Configuration.getOrDefault(m,
                    Configuration.PROXY_URI_KEY, null, true);

            Object _resourceURL = Configuration.getOrDefault(m,
                    Configuration.PROXY_URL_KEY, null, true);

            // The number of connections to create per thread
            Integer connectionsPerThread = Configuration.getOrDefault(m,
                    Configuration.PROXY_CONNECTIONS_PER_THREAD, 10,
                    true);

            Integer maxQueueSize = Configuration.getOrDefault(m,
                    Configuration.PROXY_MAX_QUEUE_SIZE, 0, true);

            Integer softMaxConnectionsPerThread = Configuration.getOrDefault(m,
                    Configuration.PROXY_SOFT_MAX_CONNECTIONS_PER_THREAD, 5, true);

            Integer ttl = Configuration.getOrDefault(m,
                    Configuration.PROXY_TTL, -1, true);

            // Time in seconds between retries for problem server
            Integer problemServerRetry = Configuration.getOrDefault(m,
                    Configuration.PROXY_PROBLEM_SERVER_RETRY, 10,
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

                if (_resourceURL instanceof String) {
                    proxyClient = proxyClient.addHost(
                            new URI((String) _resourceURL), sslProvider);
                } else if (_resourceURL instanceof List) {
                    for (Object resourceURL : ((List<?>) _resourceURL)) {
                        if (resourceURL instanceof String) {
                            proxyClient = proxyClient.addHost(
                                    new URI((String) resourceURL), sslProvider);
                        } else {
                            LOGGER.warn("Invalid URI {}, resource {} not proxied ",
                                    uri, resourceURL);
                        }
                    }
                } else {
                    LOGGER.warn("Invalid URI {}, resource {} not proxied ",
                            uri, _resourceURL);
                }

                ProxyHandler proxyHandler = ProxyHandler.builder()
                        .setRewriteHostHeader(true)
                        .setProxyClient(proxyClient)
                        .setNext(new AccountHeadersInjector(
                                new ResponseInterceptorsHandler()))
                        .build();

                PipedHttpHandler wrappedProxyHandler
                        = new AccountHeadersInjector(
                                new PipedWrappingHandler(
                                        new XPoweredByInjector(
                                                new RequestInterceptorsHandler()),
                                        proxyHandler));

                paths.addPrefixPath(uri,
                        new RequestLoggerHandler(
                                new SecurityHandler(
                                        new AuthHeadersRemover(
                                                wrappedProxyHandler),
                                        authMechanisms,
                                        accessManager,
                                        tokenManager)));

                LOGGER.info("URI {} bound to {}", uri, _resourceURL);
            }
            catch (URISyntaxException ex) {
                LOGGER.warn("Invalid URI {}, resources {} not proxied ",
                        uri,
                        _resourceURL);
            }
        });
    }

    /**
     * getConfiguration
     *
     * @return the global configuration
     */
    public static Configuration getConfiguration() {
        return configuration;
    }

    private Bootstrapper() {
    }
}
