FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart-platform
COPY etc/restheart-platform-core.yml Docker/etc/config.properties etc/
COPY target/restheart-platform-core.jar restheart-platform-core.jar

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart-platform-core.jar", "etc/restheart-platform-core.yml"]
CMD ["--envFile", "etc/config.properties"]
EXPOSE 8080
