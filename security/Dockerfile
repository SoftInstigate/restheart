FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart
COPY Docker/etc/*.yml /opt/restheart/etc/
COPY Docker/etc/*.properties /opt/restheart/etc/
COPY target/restheart-security.jar /opt/restheart/

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart-security.jar", "etc/restheart-security.yml"]
CMD ["--envFile", "etc/default.properties"]
EXPOSE 8080
