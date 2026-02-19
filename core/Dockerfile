ARG GRAALVM_VERSION=25.0.2-graalce
FROM softinstigate/graalvm:${GRAALVM_VERSION}

LABEL org.opencontainers.image.title="RESTHeart"
LABEL org.opencontainers.image.description="RESTHeart is The Backend Framework with MongoDB Data APIs. "
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

# set environment variables
ENV RHO='/mclient/connection-string->"mongodb://host.docker.internal";/http-listener/host->"0.0.0.0"'

# JDK 25 optimized for RESTHeart: low-latency REST API, 1-2 vCPU, 1-2GB container RAM
# ZGC (generational) for predictable pause times with Virtual Threads and short-lived request objects
# NOTE: Graal JIT is enabled by default to support GraalVM polyglot plugin capabilities.
# If you are not using polyglot plugins, adding -XX:-UseJVMCICompiler will reduce
# compiler memory overhead significantly in constrained containers (recommended for <512Mi heap).
ENTRYPOINT [ "java", \
    "-Dfile.encoding=UTF-8", \
    "--enable-native-access=ALL-UNNAMED", \
    "--sun-misc-unsafe-memory-access=allow", \
    "-XX:+UseZGC", \
    "-XX:+ZGenerational", \
    "-XX:MaxRAMPercentage=70.0", \
    "-XX:InitialRAMPercentage=25.0", \
    "-XX:+DisableExplicitGC", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", \
    "restheart.jar" ]

EXPOSE 8009 8080 4443
