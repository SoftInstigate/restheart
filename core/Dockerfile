ARG ECLIPSE_TEMURIN_JRE=25-jre
FROM eclipse-temurin:${ECLIPSE_TEMURIN_JRE}

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends ca-certificates && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

LABEL org.opencontainers.image.title="RESTHeart"
LABEL org.opencontainers.image.description="RESTHeart is Java Framework, built on top of Undertow"
LABEL org.opencontainers.image.url="https://restheart.org"
LABEL org.opencontainers.image.licenses="AGPL-3.0, Apache-2.0"
LABEL org.opencontainers.image.authors="SoftInstigate <info@softinstigate.com>"
LABEL org.opencontainers.image.vendor="SoftInstigate"
LABEL org.opencontainers.image.source="https://github.com/SoftInstigate/restheart"
LABEL org.opencontainers.image.documentation="https://restheart.org/docs"
LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart

# Copy the executable jar and the required libraries
COPY target/restheart.jar ./restheart.jar
COPY target/lib/*.jar lib/
COPY target/plugins/*.jar plugins/
COPY target/plugins/lib/*.jar plugins/lib/

# Set environment variables
ENV RHO='/mclient/connection-string->"mongodb://host.docker.internal";/http-listener/host->"0.0.0.0"'

# JDK 25 optimized for RESTHeart: high-throughput REST API, ≤2GB heap, Virtual Threads
# G1GC tuned for small heap with many concurrent clients and short-lived request objects
ENTRYPOINT [ "java", \
    "-Dfile.encoding=UTF-8", \
    "-server", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=50", \
    "-XX:MaxRAMPercentage=80.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-XX:+DisableExplicitGC", \
    "-XX:+UseStringDeduplication", \
    "-Djdk.tracePinnedThreads=full", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", \
    "restheart.jar" ]

EXPOSE 8009 8080 4443