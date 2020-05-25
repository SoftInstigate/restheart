FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart-platform
COPY etc/restheart-platform-core.yml etc/core-docker.properties etc/
COPY target/restheart-platform-core.jar restheart-platform-core.jar

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart-platform-core.jar", "etc/restheart-platform-core.yml"]
CMD ["--envFile", "etc/core-docker.properties"]
EXPOSE 8080
