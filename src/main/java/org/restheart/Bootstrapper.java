/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import com.mongodb.MongoClient;
import static org.restheart.Configuration.RESTHEART_VERSION;
import org.restheart.db.PropsFixer;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.handlers.ErrorHandler;
import org.restheart.handlers.GzipEncodingHandler;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestDispacherHandler;
import org.restheart.handlers.injectors.RequestContextInjectorHandler;
import org.restheart.handlers.injectors.CollectionPropsInjectorHandler;
import org.restheart.handlers.injectors.DbPropsInjectorHandler;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import org.restheart.security.AccessManager;
import org.restheart.utils.ResourcesExtractor;
import org.restheart.utils.LoggingInitializer;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.applicationlogic.ApplicationLogicHandler;
import org.restheart.handlers.OptionsHandler;
import org.restheart.handlers.PipedWrappingHandler;
import org.restheart.handlers.injectors.BodyInjectorHandler;
import org.restheart.handlers.metadata.RequestTransformerMetadataHandler;
import org.restheart.security.handlers.SecurityHandlerDispacher;
import org.restheart.security.handlers.CORSHandler;
import org.restheart.utils.FileUtils;
import org.restheart.utils.OSChecker;
import com.sun.akuma.Daemon;
import static io.undertow.Handlers.path;
import io.undertow.Undertow;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.handlers.HttpContinueAcceptingHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import static io.undertow.Handlers.resource;
import io.undertow.Undertow.Builder;
import io.undertow.server.handlers.AllowedMethodsHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.HttpString;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.restheart.security.FullAccessManager;
import org.restheart.security.handlers.AuthTokenHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public final class Bootstrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrapper.class);
    private static final Map<String, File> TMP_EXTRACTED_FILES = new HashMap<>();

    private static Undertow server;
    private static GracefulShutdownHandler shutdownHandler = null;
    private static Configuration configuration;
    private static Path pidFilePath;

    /**
     * main method
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {

        try {
            // read configuration silently, to avoid logging before initializing the logger
            configuration = FileUtils.getConfiguration(args, true);
        } catch (ConfigurationException ex) {
            LOGGER.error(ex.getMessage() + ", exiting...", ex);
            stopServer(false);
            System.exit(-1);
        }

        Daemon d = null;

        if (!OSChecker.isWindows()) {
            d = new Daemon.WithoutChdir();

            // pid file name include the hash of the configuration file so that for each configuration we can have just one instance running
            // If we proceed we'd get a BindException for same port being already used by the running instance
            pidFilePath = FileUtils.getPidFilePath(FileUtils.getFileAbsoultePathHash(FileUtils.getConfigurationFilePath(args)));

            if (Files.exists(pidFilePath)) {
                LOGGER.info("Starting RESTHeart ********************************************");
                LOGGER.error("This instance is already running, exiting. {}", FileUtils.getConfigurationFilePath(args) == null ? "No configuration file specified" : "Configuration file is " + FileUtils.getConfigurationFilePath(args));
                LOGGER.error("Running instance pid is {}", FileUtils.getPidFromFile(pidFilePath));
                LOGGER.error("If it is not actually running, manually remove the pid file {} and retry", pidFilePath);
                LOGGER.info("Exiting *********************************************");

                // do not stopServer() here, since this might delete other running instace pid file and tmp resources
                System.exit(-1);
            }
        }

        initLogging(args, d);

        if (!OSChecker.isWindows()) {
            d = new Daemon.WithoutChdir();
        } else {
            LOGGER.info("Starting RESTHeart ********************************************");

            try {
                configuration = FileUtils.getConfiguration(args);
            } catch (ConfigurationException ex) {
                LOGGER.error(ex.getMessage() + ", exiting...", ex);
                stopServer(false);
                System.exit(-1);
            }

            if (shouldDemonize(args) && OSChecker.isWindows()) {
                LOGGER.warn("Fork is not supported on Windows");
            }

            logLoggingConfiguration(args, d);
        }

        // we are not on Windows and this process is not daemonized
        if (d != null && !d.isDaemonized()) {
            LOGGER.info("Starting RESTHeart ********************************************");

            try {
                configuration = FileUtils.getConfiguration(args);
            } catch (ConfigurationException ex) {
                LOGGER.error(ex.getMessage() + ", exiting...", ex);
                stopServer(false);
                System.exit(-1);
            }

            // we have to fork, this is done later by demonizeInCase(args, d), now just log some message
            if (shouldDemonize(args)) {
                LOGGER.info("Stopping logging to console");
                LOGGER.info("Logging to {} with level {}", configuration.getLogFilePath(), configuration.getLogLevel());
                LOGGER.info("RESTHeart forked **********************************************");
            } // we don't have to fork, let's create the pid file (otherwise done by Daemon.init() call in demonizeInCase())
            else {
                LOGGER.info("Creating pid file {}", pidFilePath);
                FileUtils.createPidFile(pidFilePath);
            }

            logLoggingConfiguration(args, d);
        }

        // we are not on windows and this process is daemonized
        if (d != null && d.isDaemonized()) {
            pidFilePath = FileUtils.getPidFilePath(FileUtils.getFileAbsoultePathHash(FileUtils.getConfigurationFilePath(args)));

            LOGGER.info("Forking RESTHeart ********************************************");

            logLoggingConfiguration(args, d);

            // re-read configuration, to have warnings and errors logged to file
            try {
                configuration = FileUtils.getConfiguration(args);
            } catch (ConfigurationException ex) {
                LOGGER.error(ex.getMessage() + ", exiting...", ex);
                stopServer(false);
                System.exit(-1);
            }

            try {
                LOGGER.info("pid file {}", pidFilePath);
                d.init(pidFilePath.toString());
            } catch (Exception ex) {
                LOGGER.error("Error writing pid file to {}", pidFilePath, ex);
            }
        }

        demonizeInCase(args, d);
        startServer();
    }

    /**
     * Startups the RESTHeart server
     *
     * @param confFilePath the path of the configuration file
     */
    public static void startup(final String confFilePath) {
        try {
            configuration = FileUtils.getConfiguration(new String[]{confFilePath});
        } catch (ConfigurationException ex) {
            LOGGER.error(ex.getMessage() + ", exiting...", ex);
            stopServer(false);
            System.exit(-1);
        }

        startServer();
    }

    /**
     * Shutdown the RESTHeart server
     */
    public static void shutdown() {
        stopServer(false);
    }

    private static void initLogging(final String[] args, final Daemon d) {
        LoggingInitializer.setLogLevel(configuration.getLogLevel());

        if (d != null && d.isDaemonized()) {
            LoggingInitializer.stopConsoleLogging();
            LoggingInitializer.startFileLogging(configuration.getLogFilePath());
        } else if (!shouldDemonize(args)) {
            if (!configuration.isLogToConsole()) {
                LoggingInitializer.stopConsoleLogging();
            }
            if (configuration.isLogToFile()) {
                LoggingInitializer.startFileLogging(configuration.getLogFilePath());
            }
        }
    }

    private static void logLoggingConfiguration(final String[] args, final Daemon d) {
        if (d == null || !d.isDaemonized()) {
            return;
        }

        if (!shouldDemonize(args)) {
            if (!configuration.isLogToConsole()) {
                LOGGER.info("Stop logging to console ");
                LOGGER.info("***************************************************************");
            } else {
                LOGGER.info("Logging to console with level {}", configuration.getLogLevel());
            }

            if (configuration.isLogToFile()) {
                LOGGER.info("Logging to {} with level {}", configuration.getLogFilePath(), configuration.getLogLevel());
            }
        }
    }

    private static boolean shouldDemonize(final String[] args) {
        for (String arg : args) {
            if (arg.equals("--fork")) {
                return true;
            }
        }

        return false;
    }

    private static void demonizeInCase(final String[] args, Daemon d) {
        if (d == null || d.isDaemonized() || args == null || args.length < 1) {
            return;
        }

        if (shouldDemonize(args)) {
            // Daemon only works on POSIX OSes
            final boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
            if (isPosix) {
                try {
                    d.daemonize();
                    stopServer(true);
                    System.exit(0);
                } catch (Exception ex) {
                    LOGGER.warn("Unable to fork process. Note that forking is only supported on Linux (x86, amd64), Solaris (x86, amd64, sparc, sparcv9) and Mac OS X", ex);
                }
            } else {
                LOGGER.info("Unable to fork process, this is only supported on POSIX compliant OSes");
            }
        }
    }

    private static void startServer() {
        if (RESTHEART_VERSION != null) {
            LOGGER.info("RESTHeart version {}", RESTHEART_VERSION);
        }

        String mongoHosts = configuration.getMongoServers().stream()
                .map(s -> s.get(Configuration.MONGO_HOST_KEY) + ":" + s.get(Configuration.MONGO_PORT_KEY) + " ")
                .reduce("", String::concat);

        LOGGER.info("Initializing MongoDB connection pool to {}", mongoHosts);

        try {
            MongoDBClientSingleton.init(configuration);
            LOGGER.info("MongoDB connection pool initialized");
            new PropsFixer().fixAllMissingProps();
        } catch (Throwable t) {
            LOGGER.error("Error connecting to MongoDB. exiting..", t);
            stopServer(false);
            System.exit(-1);
        }

        try {
            startCoreSystem();
        } catch (Throwable t) {
            LOGGER.error("Error starting RESTHeart. Exiting...", t);
            stopServer(false);
            System.exit(-2);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                stopServer(false);
            }
        });

        LOGGER.info("RESTHeart started **********************************************");
    }

    private static void stopServer(boolean silent) {
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
            }
        }

        if (server != null) {
            if (!silent) {
                LOGGER.info("Stopping the Undertow server...");
            }
            try {
                server.stop();
            } catch (Throwable t) {
                LOGGER.error("Error stopping the Undertow server", t);
            }
        }

        try {
            if (!silent) {
                LOGGER.info("Flushing and closing the MongoDB client...");
            }
            if (MongoDBClientSingleton.isInitialized()) {
                MongoClient client = MongoDBClientSingleton.getInstance().getClient();
                client.fsync(false);
                client.close();
            }
        } catch (Throwable t) {
            LOGGER.error("Error flushing and closing the MongoDB client", t);
        }

        if (pidFilePath != null) {
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
            LOGGER.info("RESTHeart stopped *********************************************");
        }
    }

    private static void startCoreSystem() {
        if (configuration == null) {
            LOGGER.error("No configuration found. exiting..");
            stopServer(false);
            System.exit(-1);
        }

        if (!configuration.isHttpsListener() && !configuration.isHttpListener() && !configuration.isAjpListener()) {
            LOGGER.error("No listener specified. exiting..");
            stopServer(false);
            System.exit(-1);
        }

        IdentityManager identityManager = null;

        if (configuration.getIdmImpl() == null) {
            LOGGER.warn("***** No identity manager specified. authentication disabled.");
            identityManager = null;

        } else {
            try {
                Object idm = Class.forName(configuration.getIdmImpl())
                        .getConstructor(Map.class)
                        .newInstance(configuration.getIdmArgs());
                identityManager = (IdentityManager) idm;
            } catch (ClassCastException | NoSuchMethodException | SecurityException | ClassNotFoundException | IllegalArgumentException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                LOGGER.error("Error configuring idm implementation {}", configuration.getIdmImpl(), ex);
                stopServer(false);
                System.exit(-3);
            }
        }

        AccessManager accessManager = null;

        if (configuration.getAmImpl() == null && configuration.getIdmImpl() != null) {
            LOGGER.warn("***** no access manager specified. authenticated users can do anything.");
            accessManager = new FullAccessManager();
        } else if (configuration.getAmImpl() == null && configuration.getIdmImpl() == null) {
            LOGGER.warn("***** No access manager specified. users can do anything.");
            accessManager = new FullAccessManager();

        } else {
            try {
                Object am = Class.forName(configuration.getAmImpl())
                        .getConstructor(Map.class)
                        .newInstance(configuration.getAmArgs());
                accessManager = (AccessManager) am;
            } catch (ClassCastException | NoSuchMethodException | SecurityException | ClassNotFoundException | IllegalArgumentException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                LOGGER.error("Error configuring acess manager implementation {}", configuration.getAmImpl(), ex);
                stopServer(false);
                System.exit(-3);
            }
        }

        if (configuration.isAuthTokenEnabled()) {
            LOGGER.info("Token based authentication enabled with token TTL {} minutes", configuration.getAuthTokenTtl());
        }

        SSLContext sslContext = null;

        try {
            KeyManagerFactory kmf;
            KeyStore ks;

            if (getConfiguration().isUseEmbeddedKeystore()) {
                char[] storepass = "restheart".toCharArray();
                char[] keypass = "restheart".toCharArray();

                String storename = "rakeystore.jks";

                sslContext = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                ks = KeyStore.getInstance("JKS");
                ks.load(Bootstrapper.class.getClassLoader().getResourceAsStream(storename), storepass);

                kmf.init(ks, keypass);

                sslContext.init(kmf.getKeyManagers(), null, null);
            } else {
                sslContext = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                ks = KeyStore.getInstance("JKS");

                try (FileInputStream fis = new FileInputStream(new File(configuration.getKeystoreFile()))) {
                    ks.load(fis, configuration.getKeystorePassword().toCharArray());

                    kmf.init(ks, configuration.getCertPassword().toCharArray());
                    sslContext.init(kmf.getKeyManagers(), null, null);
                }
            }
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | CertificateException | UnrecoverableKeyException ex) {
            LOGGER.error("Couldn't start RESTHeart, error with specified keystore. exiting..", ex);
            stopServer(false);
            System.exit(-1);
        } catch (FileNotFoundException ex) {
            LOGGER.error("Couldn't start RESTHeart, keystore file not found. exiting..", ex);
            stopServer(false);
            System.exit(-1);
        } catch (IOException ex) {
            LOGGER.error("Couldn't start RESTHeart, error reading the keystore file. exiting..", ex);
            stopServer(false);
            System.exit(-1);
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
            LOGGER.info("Local cache for db and collection properties enabled");
        } else {
            LOGGER.info("Local cache for db and collection properties not enabled");
        }

        shutdownHandler = getHandlersPipe(identityManager, accessManager);

        builder
                .setIoThreads(configuration.getIoThreads())
                .setWorkerThreads(configuration.getWorkerThreads())
                .setDirectBuffers(configuration.isDirectBuffers())
                .setBufferSize(configuration.getBufferSize())
                .setBuffersPerRegion(configuration.getBuffersPerRegion())
                .setHandler(shutdownHandler);

        builder.build().start();
    }

    private static GracefulShutdownHandler getHandlersPipe(final IdentityManager identityManager, final AccessManager accessManager) {
        PipedHttpHandler coreHandlerChain
                = new DbPropsInjectorHandler(
                        new CollectionPropsInjectorHandler(
                                new RequestDispacherHandler()
                        ));

        PathHandler paths = path();

        configuration.getMongoMounts().stream().forEach(m -> {
            String url = (String) m.get(Configuration.MONGO_MOUNT_WHERE_KEY);
            String db = (String) m.get(Configuration.MONGO_MOUNT_WHAT_KEY);

            paths.addPrefixPath(url,
                    new CORSHandler(
                            new RequestContextInjectorHandler(url, db,
                                    new OptionsHandler(
                                            new BodyInjectorHandler(
                                                    new SecurityHandlerDispacher(coreHandlerChain, identityManager, accessManager))))
                    ));

            LOGGER.info("URL {} bound to MongoDB resource {}", url, db);
        });

        pipeStaticResourcesHandlers(configuration, paths, identityManager, accessManager);

        pipeApplicationLogicHandlers(configuration, paths, identityManager, accessManager);

        // pipe the auth tokens invalidation handler
        paths.addPrefixPath("/_authtokens", new CORSHandler(new SecurityHandlerDispacher(new AuthTokenHandler(), identityManager, new FullAccessManager())));

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

    private static void pipeStaticResourcesHandlers(
            final Configuration conf,
            final PathHandler paths,
            final IdentityManager identityManager,
            final AccessManager accessManager) {
        // pipe the static resources specified in the configuration file
        if (conf.getStaticResourcesMounts() != null) {
            conf.getStaticResourcesMounts().stream().forEach(sr -> {
                try {
                    String path = (String) sr.get(Configuration.STATIC_RESOURCES_MOUNT_WHAT_KEY);
                    String where = (String) sr.get(Configuration.STATIC_RESOURCES_MOUNT_WHERE_KEY);
                    String welcomeFile = (String) sr.get(Configuration.STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY);
                    boolean embedded = (Boolean) sr.get(Configuration.STATIC_RESOURCES_MOUNT_EMBEDDED_KEY);
                    boolean secured = (Boolean) sr.get(Configuration.STATIC_RESOURCES_MOUNT_SECURED_KEY);

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
                                LOGGER.error("**** Did you downloaded the browser submodule before building?");
                                LOGGER.error("**** to fix, run this command: $ git submodule update --init --recursive");
                            }
                            return;

                        }
                    } else {
                        if (!path.startsWith("/")) {
                            // this is to allow specifying the configuration file path relative to the jar (also working when running from classes)
                            URL location = Bootstrapper.class
                                    .getProtectionDomain().getCodeSource().getLocation();
                            File locationFile = new File(location.getPath());
                            file = new File(locationFile.getParent() + File.separator + path);
                        } else {
                            file = new File(path);
                        }
                    }

                    ResourceHandler handler = resource(new FileResourceManager(file, 3))
                            .addWelcomeFiles(welcomeFile)
                            .setDirectoryListingEnabled(false);

                    if (secured) {
                        paths.addPrefixPath(where,
                                new SecurityHandlerDispacher(
                                        new PipedWrappingHandler(null, handler), identityManager, accessManager));
                    } else {
                        paths.addPrefixPath(where, handler);
                    }

                    LOGGER.info("URL {} bound to static resources {}. access manager: {}", where, path, secured);

                } catch (Throwable t) {
                    LOGGER.error("Cannot bind static resources to {}", sr.get(Configuration.STATIC_RESOURCES_MOUNT_WHERE_KEY), t);
                }
            });
        }
    }

    private static void pipeApplicationLogicHandlers(
            final Configuration conf,
            final PathHandler paths,
            final IdentityManager identityManager,
            final AccessManager accessManager) {
        if (conf.getApplicationLogicMounts() != null) {
            conf.getApplicationLogicMounts().stream().forEach(al -> {
                try {
                    String alClazz = (String) al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHAT_KEY);
                    String alWhere = (String) al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHERE_KEY);
                    boolean alSecured = (Boolean) al.get(Configuration.APPLICATION_LOGIC_MOUNT_SECURED_KEY);
                    Object alArgs = al.get(Configuration.APPLICATION_LOGIC_MOUNT_ARGS_KEY);

                    if (alWhere == null || !alWhere.startsWith("/")) {
                        LOGGER.error("Cannot pipe application logic handler {}. parameter 'where' must start with /", alWhere);
                        return;
                    }

                    if (alArgs != null && !(alArgs instanceof Map)) {
                        LOGGER.error("Cannot pipe application logic handler {}."
                                + "args are not defined as a map. it is a ", alWhere, alWhere.getClass());
                        return;

                    }

                    Object o = Class.forName(alClazz)
                            .getConstructor(PipedHttpHandler.class, Map.class)
                            .newInstance(null, (Map) alArgs);

                    if (o instanceof ApplicationLogicHandler) {
                        ApplicationLogicHandler alHandler = (ApplicationLogicHandler) o;

                        PipedHttpHandler handler = new RequestContextInjectorHandler("/_logic", "*", alHandler);

                        if (alSecured) {
                            paths.addPrefixPath("/_logic" + alWhere, new CORSHandler(new SecurityHandlerDispacher(handler, identityManager, accessManager)));
                        } else {
                            paths.addPrefixPath("/_logic" + alWhere, new CORSHandler(new SecurityHandlerDispacher(handler, identityManager, new FullAccessManager())));
                        }

                        LOGGER.info("URL {} bound to application logic handler {}."
                                + " access manager: {}", "/_logic" + alWhere, alClazz, alSecured);
                    } else {
                        LOGGER.error("Cannot pipe application logic handler {}."
                                + " class {} does not extend ApplicationLogicHandler", alWhere, alClazz);
                    }

                } catch (Throwable t) {
                    LOGGER.error("Cannot pipe application logic handler {}",
                            al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHERE_KEY), t);
                }
            }
            );
        }
    }

    /**
     * @return the conf
     */
    public static Configuration getConfiguration() {
        return configuration;
    }
}
