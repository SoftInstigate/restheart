package org.restheart.security;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheNotFoundException;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import static com.sun.akuma.CLibrary.LIBC;
import static io.undertow.Handlers.path;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.ansi;
import org.restheart.security.handlers.CORSHandler;
import org.restheart.security.handlers.ConfigurableEncodingHandler;
import org.restheart.security.handlers.ErrorHandler;
import static org.restheart.security.handlers.PipedHttpHandler.pipe;
import org.restheart.security.handlers.PipedWrappingHandler;
import org.restheart.security.handlers.QueryStringRebuiler;
import org.restheart.security.handlers.RequestInterceptorsExecutor;
import org.restheart.security.handlers.RequestLogger;
import org.restheart.security.handlers.RequestNotManagedHandler;
import org.restheart.security.handlers.ResponseSender;
import org.restheart.security.handlers.SecurityHandler;
import static org.restheart.security.handlers.exchange.AbstractExchange.MAX_CONTENT_SIZE;
import org.restheart.security.handlers.exchange.AbstractExchange.METHOD;
import org.restheart.security.handlers.injectors.AuthHeadersRemover;
import org.restheart.security.handlers.injectors.ConduitInjector;
import org.restheart.security.handlers.injectors.RequestContentInjector;
import static org.restheart.security.handlers.injectors.RequestContentInjector.Policy.ALWAYS;
import static org.restheart.security.handlers.injectors.RequestContentInjector.Policy.ON_REQUIRES_CONTENT_AFTER_AUTH;
import static org.restheart.security.handlers.injectors.RequestContentInjector.Policy.ON_REQUIRES_CONTENT_BEFORE_AUTH;
import org.restheart.security.handlers.injectors.XForwardedHeadersInjector;
import org.restheart.security.handlers.injectors.XPoweredByInjector;
import org.restheart.security.plugins.AuthMechanism;
import org.restheart.security.plugins.Authorizer;
import org.restheart.security.plugins.PluginsRegistry;
import static org.restheart.security.plugins.RequestInterceptor.IPOINT.AFTER_AUTH;
import static org.restheart.security.plugins.RequestInterceptor.IPOINT.BEFORE_AUTH;
import org.restheart.security.plugins.Service;
import org.restheart.security.plugins.TokenManager;
import org.restheart.security.plugins.authorizers.FullAuthorizer;
import org.restheart.security.utils.FileUtils;
import org.restheart.security.utils.LoggingInitializer;
import org.restheart.security.utils.OSChecker;
import org.restheart.security.utils.RESTHeartSecurityDaemon;
import org.restheart.security.utils.ResourcesExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;
import org.xnio.Xnio;
import org.xnio.ssl.XnioSsl;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Bootstrapper {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(Bootstrapper.class);

    private static boolean IS_FORKED;
    
    private static final Map<String, File> TMP_EXTRACTED_FILES = new HashMap<>();

    private static Path CONFIGURATION_FILE;
    private static Path PROPERTIES_FILE;
    private static final PathHandler ROOT_PATH_HANDLER = path();
    private static GracefulShutdownHandler HANDLERS = null;
    private static Configuration configuration;
    private static Undertow undertowServer;

    private static final String EXITING = ", exiting...";
    private static final String INSTANCE = " instance ";
    private static final String STARTING = "Starting ";
    private static final String UNDEFINED = "undefined";
    private static final String RESTHEART_SECURITY = "RESTHeart Security";
    private static final String VERSION = "Version {}";

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
        return ROOT_PATH_HANDLER;
    }

    private static void parseCommandLineParameters(final String[] args) {
        Args parameters = new Args();
        JCommander cmd = JCommander.newBuilder().addObject(parameters).build();
        cmd.setProgramName("java -Dfile.encoding=UTF-8 -jar -server restheart-security.jar");
        try {
            cmd.parse(args);
            if (parameters.help) {
                cmd.usage();
                System.exit(0);
            }

            String confFilePath = (parameters.configPath == null)
                    ? System.getenv("RESTHEART_SECURITY_CONFFILE")
                    : parameters.configPath;
            CONFIGURATION_FILE = FileUtils.getFileAbsolutePath(confFilePath);

            FileUtils.getFileAbsolutePath(parameters.configPath);

            IS_FORKED = parameters.isForked;
            String propFilePath = (parameters.envFile == null)
                    ? System.getenv("RESTHEART_SECURITY_ENVFILE")
                    : parameters.envFile;
            
            PROPERTIES_FILE = FileUtils.getFileAbsolutePath(propFilePath);
        } catch (com.beust.jcommander.ParameterException ex) {
            LOGGER.error(ex.getMessage());
            cmd.usage();
            System.exit(1);
        }
    }

    public static void main(final String[] args) throws ConfigurationException, IOException {
        parseCommandLineParameters(args);
        setJsonpathDefaults();
        configuration = loadConfiguration();
        run();
    }

    /**
     * Configuration from JsonPath
     */
    private static void setJsonpathDefaults() {
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
    }

    private static void run() {
        if (!hasForkOption()) {
            initLogging(null);
            startServer(false);
        } else {
            if (OSChecker.isWindows()) {
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
                        null, false, -1);
            }

            RESTHeartSecurityDaemon d = new RESTHeartSecurityDaemon();
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
                    String instanceName = getInstanceName();

                    LOGGER.info(STARTING
                            + ansi().fg(RED).bold().a(RESTHEART_SECURITY).reset().toString()
                            + INSTANCE
                            + ansi().fg(RED).bold().a(instanceName).reset()
                                    .toString());

                    if (Configuration.VERSION != null) {
                        LOGGER.info(VERSION, Configuration.VERSION);
                    }

                    logLoggingConfiguration(true);

                    d.daemonize();
                } catch (Throwable t) {
                    logErrorAndExit("Error forking", t, false, false, -1);
                }
            }
        }
    }

    private static Configuration loadConfiguration() throws ConfigurationException, UnsupportedEncodingException {
        if (CONFIGURATION_FILE == null) {
            LOGGER.warn("No configuration file provided, starting with default values!");
            return new Configuration();
        } else if (PROPERTIES_FILE == null) {
            try {
                if (Configuration.isParametric(CONFIGURATION_FILE)) {
                    logErrorAndExit("Configuration is parametric but no properties file has been specified."
                            + " You can use -e option to specify the properties file. "
                            + "For more information check https://restheart.org/learn/configuration",
                            null, false, -1);
                }
            } catch (IOException ioe) {
                logErrorAndExit("Configuration file not found " + CONFIGURATION_FILE, null, false, -1);
            }

            return new Configuration(CONFIGURATION_FILE, false);
        } else {
            final Properties p = new Properties();
            try ( InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(PROPERTIES_FILE.toFile()), "UTF-8")) {
                p.load(reader);
            } catch (FileNotFoundException fnfe) {
                logErrorAndExit("Properties file not found " + PROPERTIES_FILE, null, false, -1);
            } catch(IOException ieo) {
                logErrorAndExit("Error reading properties file " + PROPERTIES_FILE, null, false, -1);
            }

            final StringWriter writer = new StringWriter();
            try (BufferedReader reader = new BufferedReader(new FileReader(CONFIGURATION_FILE.toFile()))) {
                Mustache m = new DefaultMustacheFactory().compile(reader, "configuration-file");
                m.execute(writer, p);
                writer.flush();
            } catch (MustacheNotFoundException ex) {
                logErrorAndExit("Configuration file not found: " + CONFIGURATION_FILE, ex, false, -1);
            } catch (FileNotFoundException fnfe) {
                logErrorAndExit("Configuration file not found " + CONFIGURATION_FILE, null, false, -1);
            } catch(IOException ieo) {
                logErrorAndExit("Error reading configuration file " + CONFIGURATION_FILE, null, false, -1);
            }

            Map<String, Object> obj = new Yaml().load(writer.toString());
            return new Configuration(obj, false);
        }
    }

    private static void logStartMessages() {
        String instanceName = getInstanceName();
        LOGGER.info(STARTING + ansi().fg(RED).bold().a(RESTHEART_SECURITY).reset().toString()
                + INSTANCE
                + ansi().fg(RED).bold().a(instanceName).reset().toString());
        LOGGER.info(VERSION, Configuration.VERSION);
        LOGGER.info("Configuration = " + configuration.toString());
    }

    private static void logManifestInfo() {
        if (LOGGER.isDebugEnabled()) {
            final Set<Map.Entry<Object, Object>> MANIFEST_ENTRIES = FileUtils.findManifestInfo();
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
     * @param propFilePath
     * @return true if pid file exists
     */
    private static boolean checkPidFile(Path confFilePath, Path propFilePath) {
        if (OSChecker.isWindows()) {
            return false;
        }
        
        // pid file name include the hash of the configuration file so that
        // for each configuration we can have just one instance running
        Path pidFilePath = FileUtils
                .getPidFilePath(FileUtils.getFileAbsolutePathHash(confFilePath, propFilePath));
        if (Files.exists(pidFilePath)) {
            LOGGER.warn("Found pid file! If this instance is already "
                    + "running, startup will fail with a BindException");
            return true;
        }
        return false;
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
    private static void initLogging(final RESTHeartSecurityDaemon d) {
        LoggingInitializer.setLogLevel(configuration.getLogLevel());
        if (d != null && d.isDaemonized()) {
            LoggingInitializer.stopConsoleLogging();
            LoggingInitializer.startFileLogging(configuration
                    .getLogFilePath());
        } else if (!hasForkOption()) {
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
    private static boolean hasForkOption() {
        return IS_FORKED;
    }

    /**
     * startServer
     *
     * @param fork
     */
    private static void startServer(boolean fork) {
        logStartMessages();

        Path pidFilePath = FileUtils.getPidFilePath(
                FileUtils.getFileAbsolutePathHash(CONFIGURATION_FILE, PROPERTIES_FILE));
        boolean pidFileAlreadyExists = false;

        if (!OSChecker.isWindows() && pidFilePath != null) {
            pidFileAlreadyExists = checkPidFile(CONFIGURATION_FILE, PROPERTIES_FILE);
        }

        logLoggingConfiguration(fork);
        logManifestInfo();

        // re-read configuration file, to log errors new that logger is initialized
        try {
            loadConfiguration();
        } catch (ConfigurationException | IOException ex) {
            logErrorAndExit(ex.getMessage() + EXITING, ex, false, -1);
        }

        // run pre startup initializers
        PluginsRegistry.getInstance()
                .getPreStartupInitializers()
                .stream()
                .forEach(i -> {
                    try {
                        i.getInstance().init();
                    } catch (Throwable t) {
                        LOGGER.error("Error executing initializer {}", i.getName());
                    }
                });

        try {
            startCoreSystem();
        } catch (Throwable t) {
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
                    } catch (Throwable t) {
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
            } catch (InterruptedException ie) {
                LOGGER.error("Error while waiting for pending request "
                        + "to complete", ie);
                Thread.currentThread().interrupt();
            }
        }

        Path pidFilePath = FileUtils.getPidFilePath(FileUtils
                .getFileAbsolutePathHash(CONFIGURATION_FILE, PROPERTIES_FILE));

        if (removePid && pidFilePath != null) {
            if (!silent) {
                LOGGER.info("Removing the pid file {}",
                        pidFilePath.toString());
            }
            try {
                Files.deleteIfExists(pidFilePath);
            } catch (IOException ex) {
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
            } catch (URISyntaxException | IOException ex) {
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
        } catch (KeyManagementException
                | NoSuchAlgorithmException
                | KeyStoreException
                | CertificateException
                | UnrecoverableKeyException ex) {
            logErrorAndExit(
                    "Couldn't start RESTHeart Security, error with specified keystore. "
                    + "Check the keystore-file, "
                    + "keystore-password and certpassword options. Exiting..",
                    ex, false, -1);
        } catch (FileNotFoundException ex) {
            logErrorAndExit(
                    "Couldn't start RESTHeart Security, keystore file not found. "
                    + "Check the keystore-file, "
                    + "keystore-password and certpassword options. Exiting..",
                    ex, false, -1);
        } catch (IOException ex) {
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

        var amsConf = getConfiguration().getAuthMechanisms();

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

                            if (LOGGER.isInfoEnabled()) {
                                var amConf = amsConf.stream().filter(am -> name
                                        .equals(am.get("name")))
                                        .findFirst();

                                if (amConf != null
                                        && amConf.isPresent()
                                        && amConf.get().get("args") != null
                                        && amConf.get().get("args") instanceof Map
                                        && ((Map) amConf.get().get("args"))
                                                .containsKey("authenticator")) {
                                    LOGGER.info("Authentication Mechanism {} "
                                            + "enabled with Authenticator {}",
                                            name,
                                            ((Map) amConf.get().get("args"))
                                                    .get("authenticator"));
                                } else {
                                    LOGGER.info("Authentication Mechanism {} "
                                            + "enabled",
                                            name);
                                }
                            }
                        } catch (ConfigurationException pcex) {
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
                        } catch (ConfigurationException pcex) {
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
        return new GracefulShutdownHandler(
                new RequestLimitingHandler(
                        new RequestLimit(configuration.getRequestsLimit()),
                        new AllowedMethodsHandler(
                                new BlockingHandler(
                                        new ErrorHandler(
                                                new HttpContinueAcceptingHandler(paths))),
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

                            LOGGER.debug("{} secured {}", name, _srv.getSecured());

                            SecurityHandler securityHandler;

                            if (_srv.getSecured()) {
                                securityHandler = new SecurityHandler(
                                        authMechanisms,
                                        authorizers,
                                        tokenManager);
                            } else {
                                var _fauthorizers = new LinkedHashSet<Authorizer>();
                                _fauthorizers.add(new FullAuthorizer(false));

                                securityHandler = new SecurityHandler(
                                        authMechanisms,
                                        _fauthorizers,
                                        tokenManager);
                            }

                            var srv = pipe(
                                    new RequestLogger(),
                                    new CORSHandler(),
                                    new XPoweredByInjector(),
                                    new RequestContentInjector(ON_REQUIRES_CONTENT_BEFORE_AUTH),
                                    new RequestInterceptorsExecutor(BEFORE_AUTH),
                                    new QueryStringRebuiler(),
                                    securityHandler,
                                    new RequestContentInjector(ON_REQUIRES_CONTENT_AFTER_AUTH),
                                    new RequestInterceptorsExecutor(AFTER_AUTH),
                                    new QueryStringRebuiler(),
                                    new ConduitInjector(),
                                    PipedWrappingHandler.wrap(
                                            new ConfigurableEncodingHandler(_srv,
                                                    configuration.isForceGzipEncoding())),
                                    new ResponseSender()
                            );

                            paths.addPrefixPath(_srv.getUri(), srv);

                            LOGGER.info("URI {} bound to service {}, secured: {}",
                                    _srv.getUri(),
                                    _srv.getName(),
                                    _srv.getSecured());

                        } catch (ConfigurationException pce) {
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
        } catch (ConfigurationException pce) {
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
            } catch (GeneralSecurityException ex) {
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
                    for (Object proxyPassURL : ((Iterable<? extends Object>) _proxyPass)) {
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

                var proxy = pipe(
                        new RequestLogger(),
                        new XPoweredByInjector(),
                        new RequestContentInjector(ALWAYS),
                        new RequestInterceptorsExecutor(BEFORE_AUTH),
                        new QueryStringRebuiler(),
                        new SecurityHandler(
                                authMechanisms,
                                authorizers,
                                tokenManager),
                        new AuthHeadersRemover(),
                        new XForwardedHeadersInjector(),
                        new RequestInterceptorsExecutor(AFTER_AUTH),
                        new QueryStringRebuiler(),
                        new ConduitInjector(),
                        PipedWrappingHandler.wrap(
                                new ConfigurableEncodingHandler( // Must be after ConduitInjector
                                        proxyHandler,
                                        configuration.isForceGzipEncoding())));

                paths.addPrefixPath(location, proxy);

                LOGGER.info("URI {} bound to resource {}", location, _proxyPass);
            } catch (URISyntaxException ex) {
                LOGGER.warn("Invalid location URI {}, resource {} not bound ",
                        location,
                        _proxyPass);
            }
        });
    }

    private Bootstrapper() {
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
