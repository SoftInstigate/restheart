FROM gcr.io/distroless/java:11

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

WORKDIR /opt/uiam
COPY Docker/etc/* /opt/uiam/etc/
COPY target/uiam.jar /opt/uiam/

ENTRYPOINT [ "java", "-Dfile.encoding=UTF-8", "-server", "-jar", "uiam.jar"]
CMD ["etc/uiam.yml"]
EXPOSE 8080 4443
