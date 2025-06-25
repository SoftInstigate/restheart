FROM eclipse-temurin:21 AS jre-build

# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base,java.compiler,java.desktop,java.management,java.naming,java.security.jgss,java.security.sasl,java.sql,jdk.unsupported \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=zip-6 \
         --output /javaruntime

# Define the base image
FROM debian:bookworm-slim

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

LABEL org.opencontainers.image.title="RESTHeart"
LABEL org.opencontainers.image.description="RESTHeart is Java Framework, built on top of Undertow"
LABEL org.opencontainers.image.url="https://restheart.org"
LABEL org.opencontainers.image.licenses="AGPL-3.0, Apache-2.0"
LABEL org.opencontainers.image.authors="SoftInstigate <info@softinstigate.com>"
LABEL org.opencontainers.image.vendor="SoftInstigate"
LABEL org.opencontainers.image.source="https://github.com/SoftInstigate/restheart"
LABEL org.opencontainers.image.documentation="https://restheart.org/docs"
LABEL maintainer="SoftInstigate <info@softinstigate.com>"

# Reduce image size by removing package lists after installation
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends ca-certificates && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    rm -rf /tmp/* && \
    rm -rf /var/tmp/* && \
    rm -rf /var/cache/apt/archives/*

WORKDIR /opt/restheart

# Copy the executable jar and the required libraries
COPY target/restheart.jar ./restheart.jar
COPY target/lib/*.jar lib/
COPY target/plugins/*.jar plugins/
COPY target/plugins/lib/*.jar plugins/lib/

# Set environment variables
ENV RHO='/mclient/connection-string->"mongodb://host.docker.internal";/http-listener/host->"0.0.0.0";/ping/msg->"RESTHeart is up and running"'

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart.jar" ]

EXPOSE 8009 8080 4443
