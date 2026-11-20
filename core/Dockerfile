ARG GRAALVM_VERSION=25.4.4.1.1-graalce
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

# Run as the non-root user of the distroless base image. The UID is numeric so that Kubernetes
# can verify runAsNonRoot. The files stay owned by root and readable by everyone: any UID can
# run RESTHeart (OpenShift assigns an arbitrary one), and none can modify it.
USER 65532

# JDK 25 optimized for RESTHeart: low-latency REST API, 1-2 vCPU, 1-2GB container RAM
# Default G1 GC is the best fit: MongoDB is the bottleneck, not the JVM.
# ZGC would add CPU and memory overhead with no benefit when virtual threads
# are mostly parked on I/O. G1 pause times are short enough at 1-2GB heaps.
# NOTE: Graal JIT is enabled by default to support GraalVM polyglot plugin capabilities.
# If you are not using polyglot plugins, adding -XX:-UseJVMCICompiler will reduce
# compiler memory overhead significantly in constrained containers (recommended for <512Mi heap).
# The GraalVM polyglot resource cache goes to /tmp: the default, under the home directory, is not
# writable with an arbitrary UID (OpenShift) or a read-only root filesystem (the Helm chart).
# SECURITY, TEMPORARY: -XX:-UseBASE64Intrinsics mitigates Java4Shell (CVE-2026-114938), a heap
# out-of-bounds write in the AVX-512 VBMI Base64 decode intrinsic, present both in HotSpot and in
# the Graal JIT, on x86-64 CPUs with AVX-512 VBMI. JDK 25.0.5.0.3 and earlier are affected, and no
# GraalVM Community 25 build with the fix exists yet. The flag only costs Base64 decoding speed.
# It does not cover the other CVEs of the OpenJDK advisory of 2026-11-17: as soon as a GraalVM
# Community build on JDK 25.0.5.1 or later is released, bump GRAALVM_VERSION above (and
# graalvm.version in pom.xml), then remove the flag.
ENTRYPOINT [ "java", \
    "-Dfile.encoding=UTF-8", \
    "-Dpolyglot.engine.userResourceCache=/tmp/graalvm-cache", \
    "--enable-native-access=ALL-UNNAMED", \
    "--sun-misc-unsafe-memory-access=allow", \
    "-XX:MaxRAMPercentage=70.0", \
    "-XX:InitialRAMPercentage=60.0", \
    "-XX:+DisableExplicitGC", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-XX:+UseCompactObjectHeaders", \
    "-XX:-UseBASE64Intrinsics", \
    "-jar", \
    "restheart.jar" ]

EXPOSE 8009 8080 4443
