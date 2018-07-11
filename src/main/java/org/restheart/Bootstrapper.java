/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart;

import org.restheart.handlers.TracingInstrumentationHandler;
import org.restheart.init.Initializer;
import com.mongodb.MongoClient;
import static com.sun.akuma.CLibrary.LIBC;
import static io.undertow.Handlers.path;
import static io.undertow.Handlers.pathTemplate;
import static io.undertow.Handlers.resource;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.handlers.AllowedMethodsHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.HttpContinueAcceptingHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.PathTemplateHandler;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.HttpString;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.Color.MAGENTA;
import static org.fusesource.jansi.Ansi.ansi;
import org.fusesource.jansi.AnsiConsole;
import static org.restheart.Configuration.RESTHEART_VERSION;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.handlers.ErrorHandler;
import org.restheart.handlers.GzipEncodingHandler;
import org.restheart.handlers.MetricsInstrumentationHandler;
import org.restheart.handlers.OptionsHandler;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.PipedWrappingHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestDispatcherHandler;
import org.restheart.handlers.RequestLoggerHandler;
import org.restheart.handlers.applicationlogic.ApplicationLogicHandler;
import org.restheart.handlers.injectors.AccountInjectorHandler;
import org.restheart.handlers.injectors.BodyInjectorHandler;
import org.restheart.handlers.injectors.CollectionPropsInjectorHandler;
import org.restheart.handlers.injectors.DbPropsInjectorHandler;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import org.restheart.handlers.injectors.RequestContextInjectorHandler;
import org.restheart.security.AccessManager;
import org.restheart.security.AuthenticationMechanismFactory;
import org.restheart.security.FullAccessManager;
import org.restheart.security.handlers.AuthTokenHandler;
import org.restheart.security.handlers.CORSHandler;
import org.restheart.security.handlers.SecurityHandlerDispacher;
import org.restheart.utils.FileUtils;
import org.restheart.utils.LoggingInitializer;
import org.restheart.utils.OSChecker;
import org.restheart.utils.RHDaemon;
import org.restheart.utils.ResourcesExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Bootstrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrapper.class);
    private static final Map<String, File> TMP_EXTRACTED_FILES = new HashMap<>();

    private static Path CONF_FILE_PATH;

    private static GracefulShutdownHandler shutdownHandler = null;
    private static Configuration configuration;
    private static Undertow undertowServer;

    private static final String EXITING = ", exiting...";
    private static final String INSTANCE = " instance ";
    private static final String STARTING = "Starting ";
    private static final String UNDEFINED = "undefined";
    private static final String RESTHEART = "RESTHeart";
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
                    + ansi().fg(RED).bold().a(configuration.isAnsiConsole()).reset().toString());
        } catch (ConfigurationException ex) {
            LOGGER.info(STARTING
                    + ansi().fg(RED).bold().a(RESTHEART).reset().toString()
                    + INSTANCE
                    + ansi().fg(RED).bold().a(UNDEFINED).reset().toString());

            if (RESTHEART_VERSION != null) {
                LOGGER.info(VERSION, ansi().fg(MAGENTA).bold().a(RESTHEART_VERSION).reset().toString());
            }

            logErrorAndExit(ex.getMessage() + EXITING, ex, false, -1);
        }

        if (!hasForkOption(args)) {
            initLogging(args, null);
            startServer(false);
        } else {
            if (OSChecker.isWindows()) {
                String instanceName = getInstanceName();

                LOGGER.info(STARTING
                        + ansi().fg(RED).bold().a(RESTHEART).reset().toString()
                        + INSTANCE
                        + ansi().fg(RED).bold().a(instanceName).reset().toString());

                if (RESTHEART_VERSION != null) {
                    LOGGER.info(VERSION, ansi().fg(MAGENTA).bold().a(RESTHEART_VERSION).reset().toString());
                }

                LOGGER.error("Fork is not supported on Windows");

                LOGGER.info(ansi().fg(GREEN).bold().a("RESTHeart stopped").reset().toString());

                System.exit(-1);
            }

            // RHDaemon only works on POSIX OSes
            final boolean isPosix = FileSystems.getDefault()
                    .supportedFileAttributeViews().contains("posix");

            if (!isPosix) {
                logErrorAndExit("Unable to fork process, this is only supported on POSIX compliant OSes", null, false, -1);
            }

            RHDaemon d = new RHDaemon();

            if (d.isDaemonized()) {
                try {
                    d.init();
                    LOGGER.info("Forked process: {}", LIBC.getpid());
                    initLogging(args, d);
                } catch (Exception t) {
                    logErrorAndExit("Error staring forked process", t, false, false, -1);
                }

                startServer(true);
            } else {
                initLogging(args, d);

                try {
                    String instanceName = getInstanceName();

                    LOGGER.info(STARTING
                            + ansi().fg(RED).bold().a(RESTHEART).reset().toString()
                            + INSTANCE
                            + ansi().fg(RED).bold().a(instanceName).reset().toString());

                    if (RESTHEART_VERSION != null) {
                        LOGGER.info(VERSION, RESTHEART_VERSION);
                    }

                    logLoggingConfiguration(true);

                    d.daemonize();
                } catch (Throwable t) {
                    logErrorAndExit("Error forking", t, false, false, -1);
                }
            }
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
        Path pidFilePath = FileUtils.getPidFilePath(
                FileUtils.getFileAbsoultePathHash(confFilePath));

        if (Files.exists(pidFilePath)) {
            LOGGER.warn("Found pid file! If this instance is already "
                    + "running, startup will fail with a BindException");

            return true;
        }

        return false;
    }

    /**
     * Startup the RESTHeart server
     *
     * @param confFilePath the path of the configuration file
     */
    public static void startup(final String confFilePath) {
        startup(FileUtils.getFileAbsoultePath(confFilePath));
    }

    /**
     * Startup the RESTHeart server
     *
     * @param confFilePath the path of the configuration file
     */
    public static void startup(final Path confFilePath) {
        try {
            configuration = FileUtils.getConfiguration(confFilePath, false);
        } catch (ConfigurationException ex) {
            if (RESTHEART_VERSION != null) {
                LOGGER.info(ansi().fg(RED).bold().a(RESTHEART).reset().toString() + " version {}",
                        ansi().fg(MAGENTA).bold().a(RESTHEART_VERSION).reset().toString());
            }

            logErrorAndExit(ex.getMessage() + EXITING, ex, false, -1);
        }

        startServer(false);
    }

    /**
     * Shutdown the RESTHeart server
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
    private static void initLogging(final String[] args, final RHDaemon d) {
        LoggingInitializer.setLogLevel(configuration.getLogLevel());

        if (d != null && d.isDaemonized()) {
            LoggingInitializer.stopConsoleLogging();
            LoggingInitializer.startFileLogging(configuration.getLogFilePath());
        } else if (!hasForkOption(args)) {
            if (!configuration.isLogToConsole()) {
                LoggingInitializer.stopConsoleLogging();
            }
            if (configuration.isLogToFile()) {
                LoggingInitializer.startFileLogging(configuration.getLogFilePath());
            }
        }
    }

    /**
     * logLoggingConfiguration
     *
     * @param fork
     */
    private static void logLoggingConfiguration(boolean fork) {
        String logbackConfigurationFile = System.getProperty("logback.configurationFile");
        boolean usesLogback = logbackConfigurationFile != null && !logbackConfigurationFile.isEmpty();

        if (usesLogback) {
            return;
        }

        if (configuration.isLogToFile()) {
            LOGGER.info("Logging to file {} with level {}", configuration.getLogFilePath(), configuration.getLogLevel());
        }

        if (!fork) {
            if (!configuration.isLogToConsole()) {
                LOGGER.info("Stop logging to console ");
            } else {
                LOGGER.info("Logging to console with level {}", configuration.getLogLevel());
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
        String instanceName = getInstanceName();

        LOGGER.info(STARTING
                + ansi().fg(RED).bold().a(RESTHEART).reset().toString()
                + INSTANCE
                + ansi().fg(RED).bold().a(instanceName).reset().toString());

        if (RESTHEART_VERSION != null) {
            LOGGER.info(VERSION, ansi().fg(MAGENTA).bold().a(RESTHEART_VERSION).reset().toString());
        }

        Path pidFilePath = FileUtils.getPidFilePath(
                FileUtils.getFileAbsoultePathHash(CONF_FILE_PATH));

        boolean pidFileAlreadyExists = false;

        if (!OSChecker.isWindows() && pidFilePath != null) {
            pidFileAlreadyExists = checkPidFile(CONF_FILE_PATH);
        }

        logLoggingConfiguration(fork);

        LOGGER.debug("Initializing MongoDB connection pool to {} with options {}", configuration.getMongoUri().getHosts(), configuration.getMongoUri().getOptions());

        try {
            MongoDBClientSingleton.init(configuration);
            //force setup
            MongoDBClientSingleton.getInstance();
            LOGGER.info("MongoDB connection pool initialized");
            LOGGER.info("MongoDB version {}",
                    ansi().fg(MAGENTA).bold().a(MongoDBClientSingleton.getServerVersion()).reset().toString());
        } catch (Throwable t) {
            logErrorAndExit("Error connecting to MongoDB. exiting..", t, false, !pidFileAlreadyExists, -1);
        }

        try {
            startCoreSystem();
        } catch (Throwable t) {
            logErrorAndExit("Error starting RESTHeart. Exiting...", t, false, !pidFileAlreadyExists, -2);
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

        // run initialized if defined
        if (configuration.getInitializerClass() != null) {
            try {

                Object o = Class
                        .forName(configuration.getInitializerClass())
                        .newInstance();

                if (o instanceof Initializer) {
                    try {
                        ((Initializer) o).init();
                        LOGGER.info(
                                "initializer {} executed",
                                configuration.getInitializerClass());
                    } catch (Throwable t) {
                        LOGGER.error("Error executing intializer {}",
                                configuration.getInitializerClass(),
                                t);
                    }
                }
            } catch (Throwable t) {
                LOGGER.error(ansi().fg(RED).bold().a(
                        "Wrong configuration for intializer {}")
                        .reset().toString(),
                        configuration.getInitializerClass(),
                        t);
            }
        }

        LOGGER.info(ansi().fg(GREEN).bold().a("RESTHeart started").reset().toString());
    }

    private static String getInstanceName() {
        return configuration == null
                ? UNDEFINED
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
            LOGGER.info("Stopping RESTHeart...");
        }

        if (shutdownHandler != null) {
            if (!silent) {
                LOGGER.info("Waiting for pending request to complete (up to 1 minute)...");
            }
            try {
                shutdownHandler.shutdown();
                shutdownHandler.awaitShutdown(60 * 1000); // up to 1 minute
            } catch (InterruptedException ie) {
                LOGGER.error("Error while waiting for pending request to complete", ie);
                Thread.currentThread().interrupt();
            }
        }

        if (MongoDBClientSingleton.isInitialized()) {
            MongoClient client = MongoDBClientSingleton.getInstance().getClient();

            if (!silent) {
                LOGGER.info("Closing MongoDB client connections...");
            }

            try {
                client.close();
            } catch (Throwable t) {
                LOGGER.warn("Error closing the MongoDB client connection", t);
            }
        }

        Path pidFilePath = FileUtils.getPidFilePath(
                FileUtils.getFileAbsoultePathHash(CONF_FILE_PATH));

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
                ResourcesExtractor.deleteTempDir(k, TMP_EXTRACTED_FILES.get(k));
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

        if (!configuration.isHttpsListener() && !configuration.isHttpListener() && !configuration.isAjpListener()) {
            logErrorAndExit("No listener specified. exiting..", null, false, -1);
        }

        final IdentityManager identityManager = loadIdentityManager();

        final AccessManager accessManager = loadAccessManager();

        final AuthenticationMechanism authenticationMechanism = loadAuthenticationMechanism(identityManager);

        if (configuration.isAuthTokenEnabled()) {
            LOGGER.info("Token based authentication enabled with token TTL {} minutes", configuration.getAuthTokenTtl());
        }

        SSLContext sslContext = null;

        try {
            sslContext = SSLContext.getInstance("TLS");

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

            if (getConfiguration().isUseEmbeddedKeystore()) {
                char[] storepass = "restheart".toCharArray();
                char[] keypass = "restheart".toCharArray();

                String storename = "rakeystore.jks";

                ks.load(Bootstrapper.class.getClassLoader().getResourceAsStream(storename), storepass);
                kmf.init(ks, keypass);
            } else {
                try (FileInputStream fis = new FileInputStream(new File(configuration.getKeystoreFile()))) {
                    ks.load(fis, configuration.getKeystorePassword().toCharArray());
                    kmf.init(ks, configuration.getCertPassword().toCharArray());
                }
            }

            tmf.init(ks);

            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (KeyManagementException
                | NoSuchAlgorithmException
                | KeyStoreException
                | CertificateException
                | UnrecoverableKeyException ex) {
            logErrorAndExit("Couldn't start RESTHeart, error with specified keystore. exiting..", ex, false, -1);
        } catch (FileNotFoundException ex) {
            logErrorAndExit("Couldn't start RESTHeart, keystore file not found. exiting..", ex, false, -1);
        } catch (IOException ex) {
            logErrorAndExit("Couldn't start RESTHeart, error reading the keystore file. exiting..", ex, false, -1);
        }

        Builder builder = Undertow.builder();

        if (configuration.isHttpsListener()) {
            builder.addHttpsListener(configuration.getHttpsPort(), configuration.getHttpHost(), sslContext);
            LOGGER.info("HTTPS listener bound at {}:{}", configuration.getHttpsHost(), configuration.getHttpsPort());
        }

        if (configuration.isHttpListener()) {
            builder.addHttpListener(configuration.getHttpPort(), configuration.getHttpsHost());
            LOGGER.info("HTTP listener bound at {}:{}", configuration.getHttpHost(), configuration.getHttpPort());
        }

        if (configuration.isAjpListener()) {
            builder.addAjpListener(configuration.getAjpPort(), configuration.getAjpHost());
            LOGGER.info("Ajp listener bound at {}:{}", configuration.getAjpHost(), configuration.getAjpPort());
        }

        LocalCachesSingleton.init(configuration);

        if (configuration.isLocalCacheEnabled()) {
            LOGGER.info("Local cache for db and collection properties enabled with TTL {} msecs",
                    configuration.getLocalCacheTtl() < 0 ? "∞"
                    : configuration.getLocalCacheTtl());
        } else {
            LOGGER.info("Local cache for db and collection properties not enabled");
        }

        if (configuration.isSchemaCacheEnabled()) {
            LOGGER.info("Local cache for schema stores enabled  with TTL {} msecs",
                    configuration.getSchemaCacheTtl() < 0 ? "∞"
                    : configuration.getSchemaCacheTtl());
        } else {
            LOGGER.info("Local cache for schema stores not enabled");
        }

        shutdownHandler = getHandlersPipe(authenticationMechanism, identityManager, accessManager);

        builder = builder
                .setIoThreads(configuration.getIoThreads())
                .setWorkerThreads(configuration.getWorkerThreads())
                .setDirectBuffers(configuration.isDirectBuffers())
                .setBufferSize(configuration.getBufferSize())
                .setBuffersPerRegion(configuration.getBuffersPerRegion())
                .setHandler(shutdownHandler);

        ConfigurationHelper.setConnectionOptions(builder, configuration);

        undertowServer = builder.build();
        undertowServer.start();
    }

    /**
     * loadAuthenticationMechanism
     *
     * @return the AuthenticationMechanism
     */
    private static AuthenticationMechanism loadAuthenticationMechanism(final IdentityManager identityManager) {
        AuthenticationMechanism authMechanism = null;
        if (configuration.getAuthMechanism() != null) {
            try {
                AuthenticationMechanismFactory am = (AuthenticationMechanismFactory) Class.forName(configuration.getAuthMechanism())
                        .getConstructor()
                        .newInstance();

                authMechanism = am.build(configuration.getAuthMechanismArgs(), identityManager);
                LOGGER.info("Authentication Mechanism {} enabled", configuration.getAuthMechanism());
            } catch (ClassNotFoundException
                    | IllegalAccessException
                    | IllegalArgumentException
                    | InstantiationException
                    | NoSuchMethodException
                    | SecurityException
                    | InvocationTargetException ex) {
                logErrorAndExit("Error configuring Authentication Mechanism implementation " + configuration.getAuthMechanism(), ex, false, -3);
            }
        } else {
            LOGGER.info("Authentication Mechanism io.undertow.security.impl.BasicAuthenticationMechanism enabled");
        }
        return authMechanism;
    }

    /**
     * loadIdentityManager
     *
     * @return the IdentityManager
     */
    private static IdentityManager loadIdentityManager() {
        IdentityManager identityManager = null;
        if (configuration.getIdmImpl() == null) {
            LOGGER.warn("***** No Identity Manager specified. Authentication disabled.");
        } else {
            try {
                Object idm = Class.forName(configuration.getIdmImpl())
                        .getConstructor(Map.class)
                        .newInstance(configuration.getIdmArgs());
                identityManager = (IdentityManager) idm;

                LOGGER.info("Identity Manager {} enabled", configuration.getIdmImpl());
            } catch (ClassNotFoundException
                    | IllegalAccessException
                    | IllegalArgumentException
                    | InstantiationException
                    | NoSuchMethodException
                    | SecurityException
                    | InvocationTargetException ex) {
                logErrorAndExit("Error configuring Identity Manager implementation " + configuration.getIdmImpl(), ex, false, -3);
            }
        }
        return identityManager;
    }

    /**
     * loadAccessManager
     *
     * @return the AccessManager
     */
    private static AccessManager loadAccessManager() {
        AccessManager accessManager = new FullAccessManager();
        if (configuration.getAmImpl() == null && configuration.getIdmImpl() != null) {
            LOGGER.warn("***** no access manager specified. authenticated users can do anything.");
        } else if (configuration.getAmImpl() == null && configuration.getIdmImpl() == null) {
            LOGGER.warn("***** No access manager specified. users can do anything.");
        } else {
            try {
                Object am = Class.forName(configuration.getAmImpl())
                        .getConstructor(Map.class)
                        .newInstance(configuration.getAmArgs());

                LOGGER.info("Access Manager {} enabled", configuration.getAmImpl());
                accessManager = (AccessManager) am;
            } catch (ClassNotFoundException
                    | IllegalAccessException
                    | IllegalArgumentException
                    | InstantiationException
                    | NoSuchMethodException
                    | SecurityException
                    | InvocationTargetException ex) {
                logErrorAndExit("Error configuring Access Manager implementation " + configuration.getAmImpl(), ex, false, -3);
            }
        }
        return accessManager;
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
        } else {
            LOGGER.error(message, t);
        }
        stopServer(silent, removePid);
        System.exit(status);
    }

    private static boolean isPathTemplate(final String url) {
        if (url == null) {
            return false;
        } else {
            return url.contains("{") && url.contains("}");
        }
    }

    /**
     * getHandlersPipe
     *
     * @param identityManager
     * @param accessManager
     * @return a GracefulShutdownHandler
     */
    private static GracefulShutdownHandler getHandlersPipe(final AuthenticationMechanism authenticationMechanism, final IdentityManager identityManager, final AccessManager accessManager) {
        PipedHttpHandler coreHandlerChain
                = new AccountInjectorHandler(
                        new DbPropsInjectorHandler(
                                new CollectionPropsInjectorHandler(
                                        new RequestDispatcherHandler()
                                )));

        PathHandler paths = path();
        PathTemplateHandler pathsTemplates = pathTemplate(false);

        // check that all mounts are either all paths or all path templates
        boolean allPathTemplates = configuration.getMongoMounts()
                .stream()
                .map(m -> (String) m.get(Configuration.MONGO_MOUNT_WHERE_KEY))
                .allMatch(url -> isPathTemplate(url));

        boolean allPaths = configuration.getMongoMounts()
                .stream()
                .map(m -> (String) m.get(Configuration.MONGO_MOUNT_WHERE_KEY))
                .allMatch(url -> !isPathTemplate(url));

        final PipedHttpHandler baseChain = new MetricsInstrumentationHandler(
            new TracingInstrumentationHandler(
                new RequestLoggerHandler(
                        new CORSHandler(
                                new OptionsHandler(
                                        new BodyInjectorHandler(
                                                new SecurityHandlerDispacher(
                                                        coreHandlerChain,
                                                        authenticationMechanism,
                                                        identityManager,
                                                        accessManager)))))));

        if (!allPathTemplates && !allPaths) {
            LOGGER.error("No mongo resource mounted! Check your mongo-mounts. where url must be either all absolute paths or all path templates");
        } else {
            configuration.getMongoMounts().stream().forEach(m -> {
                String url = (String) m.get(Configuration.MONGO_MOUNT_WHERE_KEY);
                String db = (String) m.get(Configuration.MONGO_MOUNT_WHAT_KEY);

                PipedHttpHandler pipe = new RequestContextInjectorHandler(
                        url,
                        db,
                        configuration.getAggregationCheckOperators(),
                        baseChain);

                if (allPathTemplates) {
                    pathsTemplates.add(url, pipe);
                } else {
                    paths.addPrefixPath(url, pipe);
                }

                LOGGER.info("URL {} bound to MongoDB resource {}", url, db);
            });

            if (allPathTemplates) {
                paths.addPrefixPath("/", pathsTemplates);
            }
        }

        pipeStaticResourcesHandlers(configuration, paths, authenticationMechanism, identityManager, accessManager);

        pipeApplicationLogicHandlers(configuration, paths, authenticationMechanism, identityManager, accessManager);

        // pipe the auth tokens invalidation handler
        paths.addPrefixPath("/_authtokens",
                new RequestLoggerHandler(
                        new CORSHandler(
                                new SecurityHandlerDispacher(
                                        new AuthTokenHandler(),
                                        authenticationMechanism,
                                        identityManager,
                                        new FullAccessManager()))));

        return buildGracefulShutdownHandler(paths);
    }

    /**
     * buildGracefulShutdownHandler
     *
     * @param paths
     * @return
     */
    private static GracefulShutdownHandler buildGracefulShutdownHandler(PathHandler paths) {
        return new GracefulShutdownHandler(
                new RequestLimitingHandler(new RequestLimit(configuration.getRequestLimit()),
                        new AllowedMethodsHandler(
                                new BlockingHandler(
                                        new GzipEncodingHandler(
                                                new ErrorHandler(
                                                        new HttpContinueAcceptingHandler(paths)
                                                ), configuration.isForceGzipEncoding()
                                        )
                                ), // allowed methods
                                HttpString.tryFromString(RequestContext.METHOD.GET.name()),
                                HttpString.tryFromString(RequestContext.METHOD.POST.name()),
                                HttpString.tryFromString(RequestContext.METHOD.PUT.name()),
                                HttpString.tryFromString(RequestContext.METHOD.DELETE.name()),
                                HttpString.tryFromString(RequestContext.METHOD.PATCH.name()),
                                HttpString.tryFromString(RequestContext.METHOD.OPTIONS.name())
                        )
                )
        );
    }

    /**
     * pipeStaticResourcesHandlers
     *
     * pipe the static resources specified in the configuration file
     *
     * @param conf
     * @param paths
     * @param authenticationMechanism
     * @param identityManager
     * @param accessManager
     */
    private static void pipeStaticResourcesHandlers(
            final Configuration conf,
            final PathHandler paths,
            AuthenticationMechanism authenticationMechanism, final IdentityManager identityManager,
            final AccessManager accessManager) {
        if (!conf.getStaticResourcesMounts().isEmpty()) {
            conf.getStaticResourcesMounts().stream().forEach(sr -> {
                try {
                    String path = (String) sr.get(Configuration.STATIC_RESOURCES_MOUNT_WHAT_KEY);
                    String where = (String) sr.get(Configuration.STATIC_RESOURCES_MOUNT_WHERE_KEY);
                    String welcomeFile = (String) sr.get(Configuration.STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY);

                    Boolean embedded = (Boolean) sr.get(Configuration.STATIC_RESOURCES_MOUNT_EMBEDDED_KEY);
                    if (embedded == null) {
                        embedded = false;
                    }

                    Boolean secured = (Boolean) sr.get(Configuration.STATIC_RESOURCES_MOUNT_SECURED_KEY);
                    if (secured == null) {
                        secured = false;
                    }

                    if (where == null || !where.startsWith("/")) {
                        LOGGER.error("Cannot bind static resources to {}. parameter 'where' must start with /", where);
                        return;
                    }

                    if (welcomeFile == null) {
                        welcomeFile = "index.html";
                    }

                    File file;

                    if (embedded) {
                        if (path.startsWith("/")) {
                            LOGGER.error("Cannot bind embedded static resources to {}. parameter 'where'"
                                    + "cannot start with /. the path is relative to the jar root dir or classpath directory", where);
                            return;
                        }

                        try {
                            file = ResourcesExtractor.extract(path);

                            if (ResourcesExtractor.isResourceInJar(path)) {
                                TMP_EXTRACTED_FILES.put(path, file);
                                LOGGER.info("Embedded static resources {} extracted in {}", path, file.toString());
                            }
                        } catch (URISyntaxException | IOException ex) {
                            LOGGER.error("Error extracting embedded static resource {}", path, ex);
                            return;
                        } catch (IllegalStateException ex) {
                            LOGGER.error("Error extracting embedded static resource {}", path, ex);

                            if ("browser".equals(path)) {
                                LOGGER.error("**** Have you downloaded the HAL Browser submodule before building?");
                                LOGGER.error("**** To fix this, run: $ git submodule update --init --recursive");
                            }
                            return;

                        }
                    } else if (!path.startsWith("/")) {
                        // this is to allow specifying the configuration file path relative
                        // to the jar (also working when running from classes)
                        URL location = Bootstrapper.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation();

                        File locationFile = new File(location.getPath());

                        Path _path = Paths.get(
                                locationFile.getParent()
                                        .concat(File.separator)
                                        .concat(path));

                        // normalize addresses https://issues.jboss.org/browse/UNDERTOW-742
                        file = _path.normalize().toFile();
                    } else {
                        file = new File(path);
                    }

                    if (file.exists()) {
                        ResourceHandler handler = resource(new FileResourceManager(file, 3))
                                .addWelcomeFiles(welcomeFile)
                                .setDirectoryListingEnabled(false);

                        PipedHttpHandler ph;

                        if (secured) {
                            ph = new RequestLoggerHandler(
                                    new SecurityHandlerDispacher(
                                            new PipedWrappingHandler(null, handler),
                                            authenticationMechanism,
                                            identityManager,
                                            accessManager));
                        } else {
                            ph = new RequestLoggerHandler(handler);
                        }

                        paths.addPrefixPath(where, ph);

                        LOGGER.info("URL {} bound to static resources {}. Access Manager: {}", where, file.getAbsolutePath(), secured);
                    } else {
                        LOGGER.error("Failed to bind URL {} to static resources {}. Directory does not exist.", where, path);
                    }

                } catch (Throwable t) {
                    LOGGER.error("Cannot bind static resources to {}", sr.get(Configuration.STATIC_RESOURCES_MOUNT_WHERE_KEY), t);
                }
            });
        }
    }

    /**
     * pipeApplicationLogicHandlers
     *
     * @param conf
     * @param paths
     * @param authenticationMechanism
     * @param identityManager
     * @param accessManager
     */
    private static void pipeApplicationLogicHandlers(
            final Configuration conf,
            final PathHandler paths,
            AuthenticationMechanism authenticationMechanism, final IdentityManager identityManager,
            final AccessManager accessManager) {
        if (!conf.getApplicationLogicMounts().isEmpty()) {
            conf.getApplicationLogicMounts().stream().forEach((Map<String, Object> al) -> {
                try {
                    String alClazz = (String) al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHAT_KEY);
                    String alWhere = (String) al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHERE_KEY);
                    boolean alSecured = (Boolean) al.get(Configuration.APPLICATION_LOGIC_MOUNT_SECURED_KEY);
                    Object alArgs = al.get(Configuration.APPLICATION_LOGIC_MOUNT_ARGS_KEY);

                    if (alWhere == null || !alWhere.startsWith("/")) {
                        LOGGER.error("Cannot pipe application logic handler {}. Parameter 'where' must start with /", alWhere);
                        return;
                    }

                    if (alArgs != null && !(alArgs instanceof Map)) {
                        LOGGER.error("Cannot pipe application logic handler {}."
                                + "Args are not defined as a map. It is a ", alWhere, alWhere.getClass());
                        return;

                    }

                    Object o = Class.forName(alClazz)
                            .getConstructor(PipedHttpHandler.class, Map.class)
                            .newInstance(null, (Map) alArgs);

                    if (o instanceof ApplicationLogicHandler) {
                        ApplicationLogicHandler alHandler = (ApplicationLogicHandler) o;

                        PipedHttpHandler handler
                                = new RequestContextInjectorHandler(
                                        "/_logic",
                                        "*",
                                        conf.getAggregationCheckOperators(),
                                        new BodyInjectorHandler(alHandler));

                        if (alSecured) {
                            paths.addPrefixPath("/_logic" + alWhere, new TracingInstrumentationHandler(
                                new RequestLoggerHandler(
                                        new CORSHandler(
                                                new SecurityHandlerDispacher(
                                                        handler,
                                                        authenticationMechanism,
                                                        identityManager,
                                                        accessManager)))));
                        } else {
                            paths.addPrefixPath("/_logic" + alWhere,
                                    new TracingInstrumentationHandler(
                                        new RequestLoggerHandler(
                                                new CORSHandler(
                                                        new SecurityHandlerDispacher(
                                                                handler,
                                                                authenticationMechanism,
                                                                identityManager,
                                                                new FullAccessManager())))));
                        }

                        LOGGER.info("URL {} bound to application logic handler {}."
                                + " Access manager: {}", "/_logic" + alWhere, alClazz, alSecured);
                    } else {
                        LOGGER.error("Cannot pipe application logic handler {}."
                                + " Class {} does not extend ApplicationLogicHandler", alWhere, alClazz);
                    }

                } catch (ClassNotFoundException
                        | IllegalAccessException
                        | IllegalArgumentException
                        | InstantiationException
                        | NoSuchMethodException
                        | SecurityException
                        | InvocationTargetException t) {
                    LOGGER.error("Cannot pipe application logic handler {}",
                            al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHERE_KEY), t);
                }
            }
            );
        }
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
