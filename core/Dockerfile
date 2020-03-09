FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart
COPY Docker/etc/*.yml Docker/etc/*.properties etc/users.yml etc/acl.yml /opt/restheart/etc/
COPY target/restheart-security.jar plugins /opt/restheart/

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart-security.jar", "etc/restheart-security.yml"]
CMD ["--envFile", "etc/default-security.properties"]
EXPOSE 8080
