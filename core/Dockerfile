FROM eclipse-temurin:17.0.1_12-jdk

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart
COPY target/restheart.jar .
COPY target/plugins/* plugins/

ENV RHO='/mongo-uri->"mongodb://host.docker.internal";/http-host->"0.0.0.0"'
ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart.jar" ]
EXPOSE 8009 8080 4443
