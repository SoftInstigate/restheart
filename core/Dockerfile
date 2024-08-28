FROM eclipse-temurin:21-jre

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

RUN apt-get update && \
	apt-get --no-install-recommends install -y && \
	apt-get clean

WORKDIR /opt/restheart
COPY target/restheart.jar .
COPY target/plugins/* plugins/

ENV RHO='/mclient/connection-string->"mongodb://host.docker.internal";/http-listener/host->"0.0.0.0"'
ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart.jar" ]
EXPOSE 8009 8080 4443
