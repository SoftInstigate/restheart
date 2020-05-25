FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart-platform
COPY etc/restheart-platform-security.yml etc/security-docker.properties etc/
COPY target/restheart-platform-security.jar restheart-platform-security.jar

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart-platform-security.jar", "etc/restheart-platform-security.yml"]
CMD ["--envFile", "etc/security-docker.properties"]
EXPOSE 8080 4443
