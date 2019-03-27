FROM openjdk:11.0-jre-slim-stretch

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/restheart
COPY Docker/etc/* /opt/restheart/etc/
COPY Docker/entrypoint.sh /opt/restheart/
COPY target/restheart.jar /opt/restheart/

RUN chmod +x /opt/restheart/entrypoint.sh

ENTRYPOINT ["./entrypoint.sh"]
CMD ["etc/restheart.yml"]
EXPOSE 8080 4443
