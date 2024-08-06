FROM eclipse-temurin:21-jre

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

RUN apt-get update && apt-get install -y && apt-get clean

WORKDIR /opt/restheart
COPY target/restheart-core.jar ./restheart.jar
COPY target/lib/* lib/
COPY target/plugins/* plugins/
COPY target/plugins/lib/* plugins/lib/

ENV RHO='/mclient/connection-string->"mongodb://host.docker.internal";/http-listener/host->"0.0.0.0"'
ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart.jar" ]
EXPOSE 8009 8080 4443
