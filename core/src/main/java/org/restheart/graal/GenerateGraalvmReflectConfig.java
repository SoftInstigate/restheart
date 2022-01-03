/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.graal;

import io.github.classgraph.ClassGraph;

/**
 * restheart-native js plugins access java classes via reflection,
 * this utility generates the entries to add to
 * commons/src/main/resources/META-INF/native-image/org.restheart/restheart-commons/reflect-config.json
 *
 * run: java -cp core/target/restheart.jar org.restheart.graal.GenerateGraalvmReflectConfig
 */
public class GenerateGraalvmReflectConfig {
    private static final String entry = """
    { "name": "%s",
      "allPublicFields": true,
      "allPublicConstructors": true,
      "allDeclaredConstructors": true,
      "allPublicMethods": true,
      "allDeclaredFields": true,
      "allPublicClasses": true,
      "allDeclaredClasses": true
    },""";

    public static void main(String[] args) {
        // JsonParser
        System.out.println(entry("com.google.gson.JsonParser"));
        System.out.println(entry("com.google.gson.JsonParseException"));

        //HttpClient
        System.out.println(entry("java.net.URI"));
        System.out.println(entry("java.time.Duration"));

        // JS interceptors
        System.out.println(entry("org.restheart.polyglot.JSInterceptorFactory"));

        try (var scanResult = new ClassGraph()
            // HttpClient
            .acceptPackages("java.net.http", "jdk.internal.net.http")
            .rejectClasses("jdk.internal.net.http.common.SSLFlowDelegate", "jdk.internal.net.http.common.SSLFlowDelegate$Monitor")
            // commons classes in package org.restheart.mongodb.db
            .acceptPackages("org.restheart.exchange")
            .acceptClasses( "org.restheart.mongodb.db.OperationResult", "org.restheart.mongodb.db.BulkOperationResult")
            // security classes, such as BaseAccount
            .acceptClasses( "org.restheart.security.BaseAccount",
                            "org.restheart.security.BaseAclPermission",
                            "org.restheart.security.BasePrincipal",
                            "org.restheart.security.FileRealmAccount",
                            "org.restheart.security.JwtAccount",
                            "org.restheart.security.MongoPermissions",
                            "org.restheart.security.MongoRealmAccount",
                            "org.restheart.security.PwdCredentialAccount")
            // BsonUtils
            .acceptClasses("org.restheart.utils.BsonUtils")
            .acceptClasses("org.restheart.utils.BsonUtils$ArrayBuilder")
            .acceptClasses("org.restheart.utils.BsonUtils$DocumentBuilder")
            // Bson classes, such as BsonDocument
            .acceptPackages("org.bson")
            .rejectPackages("org.bson.codecs", "org.bson.json", "org.bson.io", "org.bson.assertions", "org.bson.conversions", "org.bson.diagnostics", "org.bson.internal", "org.bson.types", "org.bson.util")
            // Mongo classes
            .acceptPackages("com.mongodb.client", "com.mongodb.internal", "com.mongodb.client.internal")
            .acceptClasses("com.mongodb.MongoClient", "com.mongodb.client.internal.MongoCollectionImpl", "com.mongodb.client.model.Filters")
            // LOGGER
            .acceptClasses("org.slf4j.Logger", "ch.qos.logback.classic.Logger", "org.apache.commons.logging.LogFactory")
            // Gson
            .acceptPackages("com.google.gson")
            .rejectPackages("com.google.gson.internal", "com.google.gson.stream", "com.google.gson.annotations")
            .enableSystemJarsAndModules()
            .scan()) {
            for (var classInfo : scanResult.getAllClasses()) {
                if ("org.bson".equals(classInfo.getPackageName()) && classInfo.getSuperclasses().stream().noneMatch(ci -> "org.bson.BsonValue".equals(ci.getName()))) {
                    continue;
                } else if ("com.google.gson".equals(classInfo.getPackageName()) && classInfo.getSuperclasses().stream().noneMatch(ci -> "com.google.gson.JsonElement".equals(ci.getName()))) {
                    continue;
                } else {
                    System.out.println(entry(classInfo.getName()));
                }
            }
        }
    }

    private static String entry(String className) {
        return entry.formatted(className);
    }
}
