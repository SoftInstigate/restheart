FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart
COPY etc/restheart.yml etc/default.properties etc/acl.yml etc/users.yml etc/
COPY target/restheart.jar /opt/restheart/
COPY target/plugins/* /opt/restheart/plugins/

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart.jar", "etc/restheart.yml"]
CMD ["--envFile", "etc/default.properties"]
EXPOSE 8009 8080 4443
