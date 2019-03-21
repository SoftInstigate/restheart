FROM openjdk:11.0-jre-slim-stretch

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/uiam
COPY Docker/etc/* /opt/uiam/etc/
COPY Docker/entrypoint.sh /opt/uiam/
COPY target/uiam.jar /opt/uiam/

RUN chmod +x /opt/uiam/entrypoint.sh

ENTRYPOINT ["./entrypoint.sh"]
CMD ["etc/uiam.yml"]
EXPOSE 8080 4443
