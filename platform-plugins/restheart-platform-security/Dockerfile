FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart-platform
COPY Docker/etc/* etc/
COPY target/restheart-platform-security.jar restheart-platform-security.jar

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart-platform-security.jar", "etc/restheart-platform-security.yml"]
EXPOSE 8080 4443
