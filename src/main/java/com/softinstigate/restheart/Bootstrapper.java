/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
package com.softinstigate.restheart;

import com.mongodb.MongoClient;
import static com.softinstigate.restheart.Configuration.RESTHEART_VERSION;
import com.softinstigate.restheart.db.PropsFixer;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.handlers.ErrorHandler;
import com.softinstigate.restheart.handlers.GzipEncodingHandler;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestDispacherHandler;
import com.softinstigate.restheart.handlers.injectors.RequestContextInjectorHandler;
import com.softinstigate.restheart.handlers.root.GetRootHandler;
import com.softinstigate.restheart.handlers.collection.DeleteCollectionHandler;
import com.softinstigate.restheart.handlers.collection.GetCollectionHandler;
import com.softinstigate.restheart.handlers.injectors.CollectionPropsInjectorHandler;
import com.softinstigate.restheart.handlers.injectors.DbPropsInjectorHandler;
import com.softinstigate.restheart.handlers.injectors.LocalCachesSingleton;
import com.softinstigate.restheart.handlers.collection.PatchCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PostCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PutCollectionHandler;
import com.softinstigate.restheart.handlers.database.DeleteDBHandler;
import com.softinstigate.restheart.handlers.database.GetDBHandler;
import com.softinstigate.restheart.handlers.database.PatchDBHandler;
import com.softinstigate.restheart.handlers.database.PutDBHandler;
import com.softinstigate.restheart.handlers.document.DeleteDocumentHandler;
import com.softinstigate.restheart.handlers.document.GetDocumentHandler;
import com.softinstigate.restheart.handlers.document.PatchDocumentHandler;
import com.softinstigate.restheart.handlers.document.PutDocumentHandler;
import com.softinstigate.restheart.handlers.indexes.DeleteIndexHandler;
import com.softinstigate.restheart.handlers.indexes.GetIndexesHandler;
import com.softinstigate.restheart.handlers.indexes.PutIndexHandler;
import com.softinstigate.restheart.security.AccessManager;
import com.softinstigate.restheart.utils.ResourcesExtractor;
import com.softinstigate.restheart.utils.LoggingInitializer;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.handlers.applicationlogic.ApplicationLogicHandler;
import com.softinstigate.restheart.handlers.OptionsHandler;
import com.softinstigate.restheart.handlers.PipedWrappingHandler;
import com.softinstigate.restheart.handlers.injectors.BodyInjectorHandler;
import com.softinstigate.restheart.handlers.metadata.MetadataEnforcerHandler;
import com.softinstigate.restheart.security.handlers.SecurityHandler;
import com.softinstigate.restheart.security.handlers.CORSHandler;
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
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare
 */
public final class Bootstrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrapper.class);
    private static final Map<String, File> TMP_EXTRACTED_FILES = new HashMap<>();

    private static Undertow server;
    private static GracefulShutdownHandler hanldersPipe = null;
    private static Configuration configuration;

    private Bootstrapper() {
    }

    /**
     * main method
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        startServer(args);
    }

    /**
     * Startups the RESTHeart server
     *
     * @param confFilePath the path of the configuration file
     */
    public static void startup(final String confFilePath) {
        startServer(new String[]{confFilePath});
    }

    /**
     * Shutdown the RESTHeart server
     */
    public static void shutdown() {
        stopServer();
    }

    private static void startServer(final String[] args) {
        if (args == null || args.length < 1) {
            configuration = new Configuration();
        } else {
            configuration = new Configuration(args[0]);
        }
        LoggingInitializer.setLogLevel(configuration.getLogLevel());

        if (configuration.isLogToFile()) {
            LoggingInitializer.startFileLogging(configuration.getLogFilePath());
        }

        LOGGER.info("starting RESTHeart ********************************************");

        LOGGER.info("RESTHeart version {}", RESTHEART_VERSION);

        String mongoHosts = configuration.getMongoServers().stream()
                .map(s -> s.get(Configuration.MONGO_HOST_KEY) + ":" + s.get(Configuration.MONGO_PORT_KEY) + " ")
                .reduce("", String::concat);

        LOGGER.info("initializing mongodb connection pool to {}", mongoHosts);

        try {
            MongoDBClientSingleton.init(configuration);

            LOGGER.info("mongodb connection pool initialized");

            PropsFixer.fixAllMissingProps();
        } catch (Throwable t) {
            LOGGER.error("error connecting to mongodb. exiting..", t);
            System.exit(-1);
        }

        try {
            startCoreSystem();
        } catch (Throwable t) {
            LOGGER.error("error starting RESTHeart. exiting..", t);
            System.exit(-2);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                stopServer();
            }
        });

        if (configuration.isLogToFile()) {
            LOGGER.info("logging to {} with level {}", configuration.getLogFilePath(), configuration.getLogLevel());
        }

        if (!configuration.isLogToConsole()) {
            LOGGER.info("stopping logging to console ");
            LoggingInitializer.stopConsoleLogging();
        } else {
            LOGGER.info("logging to console with level {}", configuration.getLogLevel());
        }

        LOGGER.info("RESTHeart started **********************************************");
    }

    private static void stopServer() {
        LOGGER.info("stopping RESTHeart");
        LOGGER.info("waiting for pending request to complete (up to 1 minute)");

        try {
            hanldersPipe.shutdown();
            hanldersPipe.awaitShutdown(60 * 1000); // up to 1 minute
        } catch (InterruptedException ie) {
            LOGGER.error("error while waiting for pending request to complete", ie);
        }

        if (server != null) {
            try {
                server.stop();
            } catch (Throwable t) {
                LOGGER.error("error stopping undertow server", t);
            }
        }

        try {
            MongoClient client = MongoDBClientSingleton.getInstance().getClient();
            client.fsync(false);
            client.close();
        } catch (Throwable t) {
            LOGGER.error("error flushing and clonsing the mongo cliet", t);
        }

        TMP_EXTRACTED_FILES.keySet().forEach(k -> {
            try {
                ResourcesExtractor.deleteTempDir(k, TMP_EXTRACTED_FILES.get(k));
            } catch (URISyntaxException | IOException ex) {
                LOGGER.error("error cleaning up temporary directory {}", TMP_EXTRACTED_FILES.get(k).toString(), ex);
            }
        }
        );

        LOGGER.info("RESTHeart stopped");
    }

    private static void startCoreSystem() {
        if (configuration == null) {
            LOGGER.error("no configuration found. exiting..");
            System.exit(-1);
        }

        if (!configuration.isHttpsListener() && !configuration.isHttpListener() && !configuration.isAjpListener()) {
            LOGGER.error("no listener specified. exiting..");
            System.exit(-1);
        }

        IdentityManager identityManager = null;

        if (configuration.getIdmImpl() == null) {
            LOGGER.warn("***** no identity manager specified. authentication disabled.");
            identityManager = null;
        } else {
            try {
                Object idm = Class.forName(configuration.getIdmImpl()).getConstructor(Map.class).newInstance(configuration.getIdmArgs());
                identityManager = (IdentityManager) idm;
            } catch (ClassCastException
                    | NoSuchMethodException
                    | SecurityException
                    | ClassNotFoundException
                    | IllegalArgumentException
                    | InstantiationException
                    | IllegalAccessException
                    | InvocationTargetException ex) {
                LOGGER.error("error configuring idm implementation {}", configuration.getIdmImpl(), ex);
                System.exit(-3);
            }
        }

        AccessManager accessManager = null;

        if (configuration.getAmImpl() == null && configuration.getIdmImpl() != null) {
            LOGGER.warn("***** no access manager specified. authenticated users can do anything.");
            accessManager = null;
        } else if (configuration.getAmImpl() == null && configuration.getIdmImpl() == null) {
            LOGGER.warn("***** no access manager specified. users can do anything.");
            accessManager = null;
        } else {
            try {
                Object am = Class.forName(configuration.getAmImpl()).getConstructor(Map.class).newInstance(configuration.getAmArgs());
                accessManager = (AccessManager) am;
            } catch (ClassCastException
                    | NoSuchMethodException
                    | SecurityException
                    | ClassNotFoundException
                    | IllegalArgumentException
                    | InstantiationException
                    | IllegalAccessException
                    | InvocationTargetException ex) {
                LOGGER.error("error configuring acess manager implementation {}", configuration.getAmImpl(), ex);
                System.exit(-3);
            }
        }

        SSLContext sslContext = null;

        try {
            KeyManagerFactory kmf;
            KeyStore ks;

            if (configuration.isUseEmbeddedKeystore()) {
                char[] storepass = "restheart".toCharArray();
                char[] keypass = "restheart".toCharArray();

                String storename = "rakeystore.jks";

                sslContext = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");
                ks.load(Bootstrapper.class.getClassLoader().getResourceAsStream(storename), storepass);

                kmf.init(ks, keypass);
                sslContext.init(kmf.getKeyManagers(), null, null);
            } else {
                sslContext = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");

                try (FileInputStream fis = new FileInputStream(new File(configuration.getKeystoreFile()))) {
                    ks.load(fis, configuration.getKeystorePassword().toCharArray());

                    kmf.init(ks, configuration.getCertPassword().toCharArray());
                    sslContext.init(kmf.getKeyManagers(), null, null);
                }
            }
        } catch (KeyManagementException
                | NoSuchAlgorithmException
                | KeyStoreException
                | CertificateException
                | UnrecoverableKeyException ex) {
            LOGGER.error("couldn't start RESTHeart, error with specified keystore. exiting..", ex);
            System.exit(-1);
        } catch (FileNotFoundException ex) {
            LOGGER.error("couldn't start RESTHeart, keystore file not found. exiting..", ex);
            System.exit(-1);
        } catch (IOException ex) {
            LOGGER.error("couldn't start RESTHeart, error reading the keystore file. exiting..", ex);
            System.exit(-1);
        }

        Builder builder = Undertow.builder();

        if (configuration.isHttpsListener()) {
            builder.addHttpsListener(configuration.getHttpsPort(), configuration.getHttpHost(), sslContext);
            LOGGER.info("https listener bound at {}:{}", configuration.getHttpsHost(), configuration.getHttpsPort());
        }

        if (configuration.isHttpListener()) {
            builder.addHttpListener(configuration.getHttpPort(), configuration.getHttpsHost());
            LOGGER.info("http listener bound at {}:{}", configuration.getHttpHost(), configuration.getHttpPort());
        }

        if (configuration.isAjpListener()) {
            builder.addAjpListener(configuration.getAjpPort(), configuration.getAjpHost());
            LOGGER.info("ajp listener bound at {}:{}", configuration.getAjpHost(), configuration.getAjpPort());
        }

        LocalCachesSingleton.init(configuration);

        if (configuration.isLocalCacheEnabled()) {
            LOGGER.info("local cache enabled");
        } else {
            LOGGER.info("local cache not enabled");
        }

        hanldersPipe = getHandlersPipe(identityManager, accessManager);

        builder
                .setIoThreads(configuration.getIoThreads())
                .setWorkerThreads(configuration.getWorkerThreads())
                .setDirectBuffers(configuration.isDirectBuffers())
                .setBufferSize(configuration.getBufferSize())
                .setBuffersPerRegion(configuration.getBuffersPerRegion())
                .setHandler(hanldersPipe);

        builder.build().start();
    }

    private static GracefulShutdownHandler getHandlersPipe(final IdentityManager identityManager, final AccessManager accessManager) {
        PipedHttpHandler coreHanlderChain
                = new DbPropsInjectorHandler(
                        new CollectionPropsInjectorHandler(
                                new BodyInjectorHandler(
                                        new MetadataEnforcerHandler(
                                                new RequestDispacherHandler(
                                                        new GetRootHandler(),
                                                        new GetDBHandler(),
                                                        new PutDBHandler(),
                                                        new DeleteDBHandler(),
                                                        new PatchDBHandler(),
                                                        new GetCollectionHandler(),
                                                        new PostCollectionHandler(),
                                                        new PutCollectionHandler(),
                                                        new DeleteCollectionHandler(),
                                                        new PatchCollectionHandler(),
                                                        new GetDocumentHandler(),
                                                        new PutDocumentHandler(),
                                                        new DeleteDocumentHandler(),
                                                        new PatchDocumentHandler(),
                                                        new GetIndexesHandler(),
                                                        new PutIndexHandler(),
                                                        new DeleteIndexHandler()
                                                )
                                        )
                                )
                        )
                );

        PathHandler paths = path();

        configuration.getMongoMounts().stream().forEach(m -> {
            String url = (String) m.get(Configuration.MONGO_MOUNT_WHERE_KEY);
            String db = (String) m.get(Configuration.MONGO_MOUNT_WHAT_KEY);

            paths.addPrefixPath(url,
                    new CORSHandler(
                            new RequestContextInjectorHandler(url, db, 
                                    new OptionsHandler(
                                            new SecurityHandler(coreHanlderChain, identityManager, accessManager)))));

            LOGGER.info("url {} bound to mongodb resource {}", url, db);
        });

        pipeStaticResourcesHandlers(configuration, paths, identityManager, accessManager);

        pipeApplicationLogicHandlers(configuration, paths, identityManager, accessManager);

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
                        LOGGER.error("cannot bind static resources to {}. parameter 'where' must start with /", where);
                        return;
                    }

                    if (welcomeFile == null) {
                        welcomeFile = "index.html";
                    }

                    File file;

                    if (embedded) {
                        if (path.startsWith("/")) {
                            LOGGER.error("cannot bind embedded static resources to {}. parameter 'where'"
                                    + "cannot start with /. the path is relative to the jar root dir or classpath directory", where);
                            return;
                        }

                        try {
                            file = ResourcesExtractor.extract(path);

                            if (ResourcesExtractor.isResourceInJar(path)) {
                                TMP_EXTRACTED_FILES.put(path, file);
                                LOGGER.info("embedded static resources {} extracted in {}", path, file.toString());
                            }
                        } catch (URISyntaxException | IOException ex) {
                            LOGGER.error("error extracting embedded static resource {}", path, ex);
                            return;
                        } catch (IllegalStateException ex) {
                            LOGGER.error("error extracting embedded static resource {}", path, ex);

                            if ("browser".equals(path)) {
                                LOGGER.error("**** did you downloaded the browser submodule before building?");
                                LOGGER.error("**** to fix, run this command: $ git submodule update --init --recursive");
                            }
                            return;
                        }
                    } else {
                        if (!path.startsWith("/")) {
                            // this is to allow specifying the configuration file path relative to the jar (also working when running from classes)
                            URL location = Bootstrapper.class.getProtectionDomain().getCodeSource().getLocation();
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
                                new SecurityHandler(
                                        new PipedWrappingHandler(null, handler), identityManager, accessManager));
                    } else {
                        paths.addPrefixPath(where, handler);
                    }

                    LOGGER.info("url {} bound to static resources {}. access manager: {}", where, path, secured);

                } catch (Throwable t) {
                    LOGGER.error("cannot bind static resources to {}", sr.get(Configuration.STATIC_RESOURCES_MOUNT_WHERE_KEY), t);
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
                        LOGGER.error("cannot pipe application logic handler {}. parameter 'where' must start with /", alWhere);
                        return;
                    }

                    if (alArgs != null && !(alArgs instanceof Map)) {
                        LOGGER.error("cannot pipe application logic handler {}."
                                + "args are not defined as a map. it is a ", alWhere, alWhere.getClass());
                        return;

                    }

                    Object o = Class.forName(alClazz).getConstructor(PipedHttpHandler.class, Map.class
                    ).newInstance(null, (Map) alArgs);

                    if (o instanceof ApplicationLogicHandler) {
                        ApplicationLogicHandler alHandler = (ApplicationLogicHandler) o;

                        PipedHttpHandler handler = new CORSHandler(new RequestContextInjectorHandler("/_logic", "*", alHandler));

                        if (alSecured) {
                            paths.addPrefixPath("/_logic" + alWhere, new SecurityHandler(handler, identityManager, accessManager));
                        } else {
                            paths.addPrefixPath("/_logic" + alWhere, handler);
                        }

                        LOGGER.info("url {} bound to application logic handler {}."
                                + " access manager: {}", "/_logic" + alWhere, alClazz, alSecured);
                    } else {
                        LOGGER.error("cannot pipe application logic handler {}."
                                + " class {} does not extend ApplicationLogicHandler", alWhere, alClazz);
                    }

                } catch (Throwable t) {
                    LOGGER.error("cannot pipe application logic handler {}",
                            al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHERE_KEY), t);
                }
            }
            );
        }
    }
}
