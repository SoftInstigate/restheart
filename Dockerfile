FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart
COPY etc/restheart.yml Docker/etc/standalone.properties Docker/etc/default.properties etc/
COPY target/restheart.jar plugins /opt/restheart/

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart.jar", "etc/restheart.yml"]
CMD ["--envFile", "etc/standalone.properties"]
EXPOSE 8009 8080 4443
