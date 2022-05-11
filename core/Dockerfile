FROM eclipse-temurin:17-jre

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

RUN apt-get update && apt-get install && apt-get clean

WORKDIR /opt/restheart
COPY etc/restheart.yml etc/acl.yml etc/users.yml etc/
COPY etc/docker.properties etc/default.properties
COPY target/restheart.jar /opt/restheart/
COPY target/plugins/* /opt/restheart/plugins/

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "restheart.jar", "etc/restheart.yml"]
CMD ["--envFile", "etc/default.properties"]
EXPOSE 8009 8080 4443
