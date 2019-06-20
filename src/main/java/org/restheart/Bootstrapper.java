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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.MustacheNotFoundException;
import com.mongodb.MongoClient;
import static com.sun.akuma.CLibrary.LIBC;
import static io.undertow.Handlers.path;
import static io.undertow.Handlers.pathTemplate;
import static io.undertow.Handlers.resource;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.UndertowOptions;
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
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
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
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import static org.fusesource.jansi.Ansi.Color.*;
import static org.fusesource.jansi.Ansi.ansi;
import org.fusesource.jansi.AnsiConsole;
import static org.restheart.ConfigurationKeys.MONGO_MOUNT_WHAT_KEY;
import static org.restheart.ConfigurationKeys.MONGO_MOUNT_WHERE_KEY;
import static org.restheart.ConfigurationKeys.STATIC_RESOURCES_MOUNT_EMBEDDED_KEY;
import static org.restheart.ConfigurationKeys.STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY;
import static org.restheart.ConfigurationKeys.STATIC_RESOURCES_MOUNT_WHAT_KEY;
import static org.restheart.ConfigurationKeys.STATIC_RESOURCES_MOUNT_WHERE_KEY;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.handlers.ErrorHandler;
import org.restheart.handlers.GzipEncodingHandler;
import org.restheart.handlers.metrics.MetricsInstrumentationHandler;
import org.restheart.handlers.OptionsHandler;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestDispatcherHandler;
import org.restheart.handlers.RequestLoggerHandler;
import org.restheart.handlers.metrics.TracingInstrumentationHandler;
import org.restheart.handlers.injectors.AccountInjectorHandler;
import org.restheart.handlers.injectors.BodyInjectorHandler;
import org.restheart.handlers.injectors.ClientSessionInjectorHandler;
import org.restheart.handlers.injectors.CollectionPropsInjectorHandler;
import org.restheart.handlers.injectors.DbPropsInjectorHandler;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import org.restheart.handlers.injectors.RequestContextInjectorHandler;
import org.restheart.handlers.CORSHandler;
import org.restheart.utils.FileUtils;
import org.restheart.utils.LoggingInitializer;
import org.restheart.utils.OSChecker;
import org.restheart.utils.RHDaemon;
import org.restheart.utils.ResourcesExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Bootstrapper {

    private static boolean IS_FORKED;
    private static String ENVIRONMENT_FILE;

    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrapper.class);
    private static final Map<String, File> TMP_EXTRACTED_FILES = new HashMap<>();

    private static Path CONF_FILE_PATH;

    private static GracefulShutdownHandler shutdownHandler = null;
    private static PathHandler rootPathHandler = path();

    private static Configuration configuration;
    private static Undertow undertowServer;

    private static final String EXITING = ", exiting...";
    private static final String RESTHEART = "RESTHeart";

    /**
     * parameters method
     *
     * @param args command line arguments
     * @throws org.restheart.ConfigurationException
     * @throws java.io.IOException
     */
    public static void main(final String[] args) throws ConfigurationException, IOException {
        parseCommandLineParameters(args);
        configuration = loadConfiguration();
        run();
    }

    private Bootstrapper() {
    }

    /**
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

    private static void parseCommandLineParameters(final String[] args) {
        Args parameters = new Args();
        JCommander cmd = JCommander.newBuilder().addObject(parameters).build();
        cmd.setProgramName("java -Dfile.encoding=UTF-8 -jar -server restheart.jar");
        try {
            cmd.parse(args);
            if (parameters.help) {
                cmd.usage();
                System.exit(0);
            }

            String confFilePath = (parameters.configPath == null)
                    ? System.getenv("RESTHEART_CONFFILE")
                    : parameters.configPath;
            CONF_FILE_PATH = FileUtils.getFileAbsolutePath(confFilePath);

            FileUtils.getFileAbsolutePath(parameters.configPath);

            IS_FORKED = parameters.isForked;
            ENVIRONMENT_FILE = (parameters.envFile == null)
                    ? System.getenv("RESTHEART_ENVFILE")
                    : parameters.envFile;
        } catch (com.beust.jcommander.ParameterException ex) {
            LOGGER.error(ex.getMessage());
            cmd.usage();
            System.exit(1);
        }
    }

    private static Configuration loadConfiguration() throws FileNotFoundException, UnsupportedEncodingException, IOException {
        if (CONF_FILE_PATH == null) {
            LOGGER.warn("No configuration file provided, starting with default values!");
            return new Configuration();
        } else if (ENVIRONMENT_FILE == null) {
            try {
                if (Configuration.isParametric(CONF_FILE_PATH)) {
                    logErrorAndExit("Configuration is parametric but no properties file has been specified. You can use -e option to specify the properties file. For more information check https://restheart.org/learn/configuration", null, false, -1);
                }
            } catch (IOException ioe) {
                logErrorAndExit("Configuration file not found " + CONF_FILE_PATH, null, false, -1);
            }

            return new Configuration(CONF_FILE_PATH, false);
        } else {
            Properties p = new Properties();
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(ENVIRONMENT_FILE), "UTF-8")) {
                p.load(reader);
            } catch (FileNotFoundException fnfe) {
                logErrorAndExit("Properties file not found " + ENVIRONMENT_FILE, null, false, -1);
            }

            MustacheFactory mf = new DefaultMustacheFactory();

            Mustache m;

            try {
                m = mf.compile(CONF_FILE_PATH.toString());
            } catch (MustacheNotFoundException ex) {
                logErrorAndExit("Configuration file not found " + CONF_FILE_PATH, null, false, -1);
                m = null;
            }

            StringWriter writer = new StringWriter();
            m.execute(writer, p);
            writer.flush();
            Yaml yaml = new Yaml();
            Map<String, Object> obj = yaml.load(writer.toString());
            return new Configuration(obj, false);
        }
    }

    private static void run() {
        LOGGER.debug(configuration.toString());
        if (!configuration.isAnsiConsole()) {
            AnsiConsole.systemInstall();
        }

        if (!hasForkOption()) {
            initLogging(null);
            startServer(false);
        } else {
            if (OSChecker.isWindows()) {
                logWindowsStart();
                LOGGER.error("Fork is not supported on Windows");
                LOGGER.info(ansi().fg(GREEN).a("RESTHeart stopped").reset().toString());
                System.exit(-1);
            }

            // RHDaemon only works on POSIX OSes
            final boolean isPosix = FileSystems.getDefault()
                    .supportedFileAttributeViews().contains("posix");

            if (!isPosix) {
                logErrorAndExit("Unable to fork process, this is only supported on POSIX compliant OSes",
                        null, false, -1);
            }

            RHDaemon d = new RHDaemon();

            if (d.isDaemonized()) {
                try {
                    d.init();
                    LOGGER.info("Forked process: {}", LIBC.getpid());
                    initLogging(d);
                } catch (Exception t) {
                    logErrorAndExit("Error staring forked process", t, false, false, -1);
                }
                startServer(true);
            } else {
                initLogging(d);
                try {
                    logWindowsStart();
                    logLoggingConfiguration(true);
                    logManifestInfo();
                    d.daemonize();
                } catch (Throwable t) {
                    logErrorAndExit("Error forking", t, false, false, -1);
                }
            }
        }
    }

    private static void logWindowsStart() {
        String version = Version.getInstance().getVersion() == null
                ? "Unknown, not packaged"
                : Version.getInstance().getVersion();

        String info = String.format("  {%n"
                + "    \"Version\": \"%s\",%n"
                + "    \"Instance-Name\": \"%s\",%n"
                + "    \"Configuration\": \"%s\",%n"
                + "    \"Environment\": \"%s\",%n"
                + "    \"Build-Time\": \"%s\"%n"
                + "  }",
                ansi().fg(MAGENTA).a(version).reset().toString(),
                ansi().fg(MAGENTA).a(getInstanceName()).reset().toString(),
                ansi().fg(MAGENTA).a(CONF_FILE_PATH).reset().toString(),
                ansi().fg(MAGENTA).a(ENVIRONMENT_FILE).reset().toString(),
                ansi().fg(MAGENTA).a(Version.getInstance().getBuildTime()).reset().toString());

        LOGGER.info("Starting {}\n{}", ansi().fg(RED).a(RESTHEART).reset().toString(), info);
    }

    private static void logManifestInfo() {
        if (LOGGER.isDebugEnabled()) {
            final Set<Entry<Object, Object>> MANIFEST_ENTRIES = FileUtils.findManifestInfo();

            if (MANIFEST_ENTRIES != null) {
                LOGGER.debug("Build Information: {}", MANIFEST_ENTRIES.toString());
            } else {
                LOGGER.debug("Build Information: {}", "Unknown, not packaged");
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
        Path pidFilePath = FileUtils
                .getPidFilePath(FileUtils.getFileAbsolutePathHash(confFilePath));
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
        startup(FileUtils.getFileAbsolutePath(confFilePath));
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
            logWindowsStart();
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
    private static void initLogging(final RHDaemon d) {
        LoggingInitializer.setLogLevel(configuration.getLogLevel());

        if (d != null && d.isDaemonized()) {
            LoggingInitializer.stopConsoleLogging();
            LoggingInitializer.startFileLogging(configuration.getLogFilePath());
        } else if (!hasForkOption()) {
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
            LOGGER.info("Logging to file {} with level {}",
                    configuration.getLogFilePath(), configuration.getLogLevel());
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
     * @return true if has isForked option
     */
    private static boolean hasForkOption() {
        return IS_FORKED;
    }

    /**
     * startServer
     *
     * @param fork
     */
    private static void startServer(boolean fork) {
        logWindowsStart();

        Path pidFilePath = FileUtils.getPidFilePath(
                FileUtils.getFileAbsolutePathHash(CONF_FILE_PATH));

        boolean pidFileAlreadyExists = false;

        if (!OSChecker.isWindows() && pidFilePath != null) {
            pidFileAlreadyExists = checkPidFile(CONF_FILE_PATH);
        }

        logLoggingConfiguration(fork);
        logManifestInfo();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Initializing MongoDB connection pool to {} with options {}",
                    configuration.getMongoUri().getHosts(), configuration.getMongoUri().getOptions());
        } else {
            LOGGER.info("Initializing MongoDB connection pool to {}",
                    configuration.getMongoUri().getHosts());
        }

        try {
            MongoDBClientSingleton.init(configuration.getMongoUri());
            //force setup
            MongoDBClientSingleton.getInstance();

            LOGGER.info("MongoDB connection pool initialized");
            LOGGER.info("MongoDB version {}",
                    ansi().fg(MAGENTA).a(MongoDBClientSingleton.getServerVersion()).reset().toString());

            if (MongoDBClientSingleton.isReplicaSet()) {
                LOGGER.info("MongoDB is a replica set");
            } else {
                LOGGER.warn("MongoDB is a standalone instance, use a replica set in production");
            }

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

        // run initializer extensions
        runInitializers();

        LOGGER.info(ansi().fg(GREEN).a("RESTHeart started").reset().toString());
    }

    /**
     * runs the initializers defined with @Initializer annotation
     */
    private static void runInitializers() {
        PluginsRegistry.
                getInstance()
                .getInitializers()
                .stream()
                .forEachOrdered(record -> {
                    try {
                        record.getInstance().init(record.getConfArgs());
                    } catch (Throwable t) {
                        LOGGER.error("Error executing initializer {}",
                                record.getName(),
                                t);
                    }
                });
    }

    private static String getInstanceName() {
        return configuration == null
                ? "Undefined"
                : configuration.getInstanceName() == null
                ? "Undefined"
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
                FileUtils.getFileAbsolutePathHash(CONF_FILE_PATH));

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

        if (!silent) {
            LOGGER.info(ansi().fg(GREEN).a("RESTHeart stopped").reset().toString());
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
            builder.addHttpsListener(configuration.getHttpsPort(),
                    configuration.getHttpsHost(), sslContext);

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

        if (configuration.isAjpListener()) {
            builder.addAjpListener(configuration.getAjpPort(),
                    configuration.getAjpHost());

            if (configuration.getAjpHost().equals("127.0.0.1")
                    || configuration.getAjpHost().equalsIgnoreCase("localhost")) {
                LOGGER.warn("AJP listener bound to localhost:{}. "
                        + "Remote systems will be unable to connect to this server.",
                        configuration.getAjpPort());
            } else {
                LOGGER.info("AJP listener bound at {}:{}",
                        configuration.getAjpHost(), configuration.getAjpPort());
            }
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
            LOGGER.info("Local cache for schema stores enabled with TTL {} msecs",
                    configuration.getSchemaCacheTtl() < 0 ? "∞"
                    : configuration.getSchemaCacheTtl());
        } else {
            LOGGER.info("Local cache for schema stores not enabled");
        }

        shutdownHandler = getHandlersPipe();

        builder = builder
                .setIoThreads(configuration.getIoThreads())
                .setWorkerThreads(configuration.getWorkerThreads())
                .setDirectBuffers(configuration.isDirectBuffers())
                .setBufferSize(configuration.getBufferSize())
                .setHandler(shutdownHandler);

        // starting undertow 1.4.23 URL become much stricter
        // (undertow commit 09d40a13089dbff37f8c76d20a41bf0d0e600d9d)
        // allow unescaped chars in URL (otherwise not allowed by default)
        builder.setServerOption(
                UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL,
                configuration.isAllowUnescapedCharactersInUrl());
        LOGGER.info("Allow unescaped characters in URL: {}",
                configuration.isAllowUnescapedCharactersInUrl());

        ConfigurationHelper.setConnectionOptions(builder, configuration);

        undertowServer = builder.build();
        undertowServer.start();
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
        return (url == null)
                ? false
                : url.contains("{") && url.contains("}");
    }

    /**
     * getHandlersPipe
     *
     * @return a GracefulShutdownHandler
     */
    private static GracefulShutdownHandler getHandlersPipe() {
        ClientSessionInjectorHandler.build(new DbPropsInjectorHandler(
                new CollectionPropsInjectorHandler(
                        RequestDispatcherHandler.getInstance())));

        PipedHttpHandler coreHandlerChain
                = new AccountInjectorHandler(
                        ClientSessionInjectorHandler.getInstance());

        PathTemplateHandler pathsTemplates = pathTemplate(false);

        // check that all mounts are either all paths or all path templates
        boolean allPathTemplates = configuration.getMongoMounts()
                .stream()
                .map(m -> (String) m.get(MONGO_MOUNT_WHERE_KEY))
                .allMatch(url -> isPathTemplate(url));

        boolean allPaths = configuration.getMongoMounts()
                .stream()
                .map(m -> (String) m.get(MONGO_MOUNT_WHERE_KEY))
                .allMatch(url -> !isPathTemplate(url));

        final PipedHttpHandler baseChain = new MetricsInstrumentationHandler(
                new TracingInstrumentationHandler(
                        new RequestLoggerHandler(
                                new CORSHandler(
                                        new OptionsHandler(
                                                new BodyInjectorHandler(
                                                        coreHandlerChain))))));

        if (!allPathTemplates && !allPaths) {
            LOGGER.error("No mongo resource mounted! Check your mongo-mounts."
                    + " where url must be either all absolute paths"
                    + " or all path templates");
        } else {
            configuration.getMongoMounts().stream().forEach(m -> {
                String url = (String) m.get(MONGO_MOUNT_WHERE_KEY);
                String db = (String) m.get(MONGO_MOUNT_WHAT_KEY);

                PipedHttpHandler pipe = new RequestContextInjectorHandler(
                        url,
                        db,
                        configuration.getAggregationCheckOperators(),
                        baseChain);

                if (allPathTemplates) {
                    pathsTemplates.add(url, pipe);
                } else {
                    getRootPathHandler().addPrefixPath(url, pipe);
                }

                LOGGER.info(ansi().fg(GREEN).a("URI {} bound to MongoDB resource {}").reset().toString(), url, db);
            });

            if (allPathTemplates) {
                getRootPathHandler().addPrefixPath("/", pathsTemplates);
            }
        }

        pipeStaticResourcesHandlers(configuration, getRootPathHandler());
        pipeServices(configuration, getRootPathHandler());

        return buildGracefulShutdownHandler(getRootPathHandler());
    }

    /**
     * buildGracefulShutdownHandler
     *
     * @param pathHandler
     * @return
     */
    private static GracefulShutdownHandler buildGracefulShutdownHandler(PathHandler pathHandler) {
        return new GracefulShutdownHandler(
                new RequestLimitingHandler(new RequestLimit(configuration.getRequestLimit()),
                        new AllowedMethodsHandler(
                                new BlockingHandler(
                                        new GzipEncodingHandler(
                                                new ErrorHandler(
                                                        new HttpContinueAcceptingHandler(pathHandler)
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
     * @param pathHandler
     * @param authenticationMechanism
     * @param identityManager
     * @param accessManager
     */
    private static void pipeStaticResourcesHandlers(
            final Configuration conf,
            final PathHandler pathHandler) {
        if (!conf.getStaticResourcesMounts().isEmpty()) {
            conf.getStaticResourcesMounts().stream().forEach(sr -> {
                try {
                    String path = (String) sr.get(STATIC_RESOURCES_MOUNT_WHAT_KEY);
                    String where = (String) sr.get(STATIC_RESOURCES_MOUNT_WHERE_KEY);
                    String welcomeFile = (String) sr.get(STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY);

                    Boolean embedded = (Boolean) sr.get(STATIC_RESOURCES_MOUNT_EMBEDDED_KEY);
                    if (embedded == null) {
                        embedded = false;
                    }

                    if (where == null || !where.startsWith("/")) {
                        LOGGER.error("Cannot bind static resources to {}. "
                                + "parameter 'where' must start with /", where);
                        return;
                    }

                    if (welcomeFile == null) {
                        welcomeFile = "index.html";
                    }

                    File file;

                    if (embedded) {
                        if (path.startsWith("/")) {
                            LOGGER.error("Cannot bind embedded static resources to {}. parameter 'where'"
                                    + "cannot start with /. the path is relative to the jar root dir"
                                    + " or classpath directory", where);
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

                        PipedHttpHandler ph = new RequestLoggerHandler(handler);

                        pathHandler.addPrefixPath(where, ph);

                        LOGGER.info("URI {} bound to static resources {}.",
                                where, file.getAbsolutePath());
                    } else {
                        LOGGER.error("Failed to bind URL {} to static resources {}."
                                + " Directory does not exist.", where, path);
                    }

                } catch (Throwable t) {
                    LOGGER.error("Cannot bind static resources to {}",
                            sr.get(STATIC_RESOURCES_MOUNT_WHERE_KEY), t);
                }
            });
        }
    }

    /**
     * pipe services
     *
     * @param conf
     * @param pathHandler
     */
    private static void pipeServices(
            final Configuration conf,
            final PathHandler pathHandler) {
        PluginsRegistry.getInstance().getServices().stream().forEach(srv -> {
            var srvConfArgs = srv.getConfArgs();

            String uri;

            if (srvConfArgs == null
                    || !srvConfArgs.containsKey("uri")
                    || srvConfArgs.get("uri") == null) {
                uri = srv.getInstance().defaultUri();
            } else {
                if (!(srvConfArgs.get("uri") instanceof String)) {
                    LOGGER.error("Cannot start service {}:"
                            + " the configuration property 'uri' must be a string",
                            srv.getName());

                    return;
                } else {
                    uri = (String) srvConfArgs.get("uri");
                }
            }

            if (uri == null) {
                LOGGER.error("Cannot start service {}:"
                        + " the configuration property 'uri' is not defined"
                        + " and the service does not have a default value",
                        srv.getName());
                return;
            }

            if (!uri.startsWith("/")) {
                LOGGER.error("Cannot start service {}:"
                        + " the configuration property 'uri' must start with /",
                        srv.getName(),
                        uri);

                return;
            }

            PipedHttpHandler handler
                    = new RequestContextInjectorHandler(
                            "/",
                            "*",
                            conf.getAggregationCheckOperators(),
                            new BodyInjectorHandler(srv.getInstance()));

            pathHandler.addPrefixPath(uri,
                    new TracingInstrumentationHandler(
                            new RequestLoggerHandler(
                                    new CORSHandler(handler))));

            LOGGER.info("Service {} bound to {}",
                    srv.getName(), uri);
        });
    }

    @Parameters
    private static class Args {

        @Parameter(description = "<Configuration file>")
        private String configPath = null;

        @Parameter(names = "--fork", description = "Fork the process")
        private boolean isForked = false;

        @Parameter(names = {"--envFile", "--envfile", "-e"}, description = "Environment file name")
        private String envFile = null;

        @Parameter(names = {"--help", "-?"}, help = true, description = "This help message")
        private boolean help;
    }
}
