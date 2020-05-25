[TOC]

# RESTHeart Platform packager

This projects packages the RESTHeart Platform files into a single zip file to make it available to the public.

## Automatic build and deployment

Tag the release with a version number and the Bitbucket pipeline will build, package and upload to the S3 bucket at `download.restheart.com`.

## Manual build and deployment

Clone this repository, then cd into it and clone (or copy) the `restheart-platform-core`,  `restheart-platform-security` and `si-lka` projects.

To [build](build.sh) and [package](package.sh) a new release of the RESTHeart Platform:

```
$ ./build.sh
$ ./package.sh <version>
```

The "version" parameter is mandatory. This performs the following steps:

1. Cleans-up previous folders and zip files
1. Creates the `dist/` folder
1. builds both core and security projects with Maven;
1. creates a folder named `restheart-platform-<version>/` into `dist/`;
1. moves the `template/` folder's content into `dist/restheart-platform-<version>/`;
1. compress the `dist/restheart-platform-<version>/` folder into a zip file named `dist/restheart-platform-<version>.zip`.

The structure of the distributable zip file will be like this:

```
.
├── Docker/
│   ├── Dockerfile-core
│   ├── Dockerfile-security
│   └── etc/
│       ├── acl.yml
│       ├── core.properties
│       ├── restheart-platform-core.yml
│       ├── restheart-platform-security.yml
│       └── users.yml
├── core.log
├── docker-compose.yml
├── etc/
│   ├── bwcv3.properties
│   ├── default.properties
│   ├── restheart-platform-core.yml
│   ├── restheart-platform-security.yml
│   └── standalone.properties
├── lickey/
│   └── COMM-LICENSE.txt
├── restheart-platform-core.jar
├── restheart-platform-security.jar
└── security.log

```

Finally, upload the zip file to publish it:

```bash
$ ./upload.sh
```

The [upload.sh](upload.sh) script copies any file with name pattern `dist/restheart-platform-*.zip` to the s3 bucket named `download.restheart.com`.

## Set new version

The `setversion.sh` script can be used to update the parent POM and all modules referencing it. For example:

    $ ./setversion.sh 4.2.0-SNAPSHOT

## Build a project with restheart-platform dependencies

Just include the BOM POM in your pom.xml. Add a `dependencyManagement` element, like this:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.softinstigate.restheart</groupId>
            <artifactId>restheart-platform</artifactId>
            <version>4.1.15</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Example

### Parent POM

A complete example of a project's parent POM extending the restheart-platform. Note the two moudules.

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.acme</groupId>
    <artifactId>restheart-platform</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>core</module>
        <module>security</module>
    </modules>

    <properties>
        <!-- System -->
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <checkstyle.file.path>checkstyle-checker.xml</checkstyle.file.path>
        <dependency.locations.enabled>false</dependency.locations.enabled>
    </properties>

    <organization>
        <name>SoftInstigate</name>
        <url>https://softinstigate.com</url>
    </organization>

    <repositories>
        <repository>
            <id>restheart-platform-release</id>
            <name>S3 Release Repository</name>
            <url>s3://maven.softinstigate.com/release</url>
        </repository>
    </repositories>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.softinstigate.restheart</groupId>
                <artifactId>restheart-platform</artifactId>
                <version>4.1.15</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <extensions>
            <extension>
                <groupId>org.springframework.build</groupId>
                <artifactId>aws-maven</artifactId>
                <version>5.0.0.RELEASE</version>
            </extension>
        </extensions>
        <testResources>
            <testResource>
                <directory>src/test/java</directory>
                <excludes>
                    <exclude>**/*.java</exclude>
                </excludes>
            </testResource>
        </testResources>
        <finalName>${project.artifactId}-${project.version}-nodeps</finalName>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-shade-plugin</artifactId>
                    <version>3.2.1</version>
                    <configuration>
                        <finalName>${project.artifactId}</finalName>
                        <createDependencyReducedPom>true</createDependencyReducedPom>
                        <filters>
                            <filter>
                                <artifact>*:*</artifact>
                                <excludes>
                                    <exclude>META-INF/*.SF</exclude>
                                    <exclude>META-INF/*.DSA</exclude>
                                    <exclude>META-INF/*.RSA</exclude>
                                </excludes>
                            </filter>
                            <filter>
                                <!-- removing overlapping classes, defined also in guava -->
                                <artifact>com.google.guava:failureaccess</artifact>
                                <excludes>
                                    <exclude>com/google/common/util/concurrent/internal/InternalFutureFailureAccess.class</exclude>
                                    <exclude>com/google/common/util/concurrent/internal/InternalFutures.class</exclude>
                                </excludes>
                            </filter>
                        </filters>
                    </configuration>
                    <executions>
                        <execution>
                            <phase>package</phase>
                            <goals>
                                <goal>shade</goal>
                            </goals>
                            <configuration>
                                <transformers>
                                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                        <mainClass>${mainclass}</mainClass>
                                    </transformer>
                                </transformers>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-assembly-plugin</artifactId>
                    <version>3.1.1</version>
                    <executions>
                        <execution>
                            <id>bin</id>
                            <phase>package</phase>
                            <goals>
                                <goal>single</goal>
                            </goals>
                            <configuration>
                                <id>bin</id>
                                <appendAssemblyId>false</appendAssemblyId>
                                <finalName>${project.artifactId}-${project.version}</finalName>
                                <descriptors>
                                    <descriptor>assembly.xml</descriptor>
                                </descriptors>
                                <attach>false</attach>
                                <tarLongFileMode>posix</tarLongFileMode>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-jar-plugin</artifactId>
                    <version>3.1.2</version>
                    <configuration>
                        <archive>
                            <manifest>
                                <addClasspath>true</addClasspath>
                                <mainClass>${mainclass}</mainClass>
                                <addDefaultImplementationEntries>true</addDefaultImplementationEntries>
                                <addDefaultSpecificationEntries>true</addDefaultSpecificationEntries>
                            </manifest>
                        </archive>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.8.1</version>
                    <configuration>
                        <debug>true</debug>
                        <!--<compilerArgument>-Xlint</compilerArgument>-->
                        <showDeprecation>true</showDeprecation>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>2.22.2</version>
                    <executions>
                        <execution>
                            <goals>
                                <goal>integration-test</goal>
                                <goal>verify</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>

</project>
```

### restheart-security POM

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.restheart</groupId>
    <artifactId>security</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>com.acme</groupId>
        <artifactId>restheart-platform</artifactId>
        <version>1.0.0</version>
    </parent>

    <properties>
        <mainclass>org.restheart.security.Bootstrapper</mainclass>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.restheart</groupId>
            <artifactId>restheart-platform-security</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>

```
### restheart-core POM

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.acme</groupId>
    <artifactId>core</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>com.acme</groupId>
        <artifactId>restheart-platform</artifactId>
        <version>1.0.0</version>
    </parent>

    <properties>
        <mainclass>org.restheart.Bootstrapper</mainclass>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.restheart</groupId>
            <artifactId>restheart-platform-core</artifactId>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.artifactId}-${project.version}-nodeps</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>

```

