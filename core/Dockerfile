FROM eclipse-temurin:21-jre

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

RUN apt-get update && \
	apt-get --no-install-recommends install -y && \
	apt-get clean

WORKDIR /opt/restheart
COPY target/restheart.jar ./restheart.jar
COPY target/lib/*.jar lib/
COPY target/plugins/*.jar plugins/
COPY target/plugins/lib/*.jar plugins/lib/

ENV RHO='/mclient/connection-string->"mongodb://host.docker.internal";/http-listener/host->"0.0.0.0"'
ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart.jar" ]
EXPOSE 8009 8080 4443
