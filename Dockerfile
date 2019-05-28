FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart
COPY etc/restheart.yml Docker/etc/config.properties etc/
COPY target/restheart.jar /opt/restheart/

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart.jar", "etc/restheart.yml", "--envFile", "etc/config.properties"]
EXPOSE 8080 4443
