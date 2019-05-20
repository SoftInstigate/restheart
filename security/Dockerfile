FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart
COPY Docker/etc/* /opt/restheart/etc/
COPY target/restheart-security.jar /opt/restheart/

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart-security.jar", "/opt/restheart/etc/restheart-security.yml"]
EXPOSE 8080
