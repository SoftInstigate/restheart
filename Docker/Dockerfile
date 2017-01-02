FROM openjdk:8u111-jre-alpine

MAINTAINER SoftInstigate <info@softinstigate.com>

WORKDIR /opt/restheart
COPY etc/* /opt/restheart/etc/
COPY restheart.jar /opt/restheart/

ENTRYPOINT ["java", "-server", "-jar", "/opt/restheart/restheart.jar"]
CMD ["etc/restheart.yml"]
EXPOSE 8080 4443
