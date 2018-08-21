FROM openjdk:8u171-jre-alpine

LABEL maintainer="SoftInstigate <info@softinstigate.com>"

RUN apk upgrade --update && apk add --update libstdc++ curl ca-certificates bash

WORKDIR /opt/restheart
COPY Docker/etc/* /opt/restheart/etc/
COPY Docker/entrypoint.sh /opt/restheart/
COPY target/restheart.jar /opt/restheart/

RUN chmod +x /opt/restheart/entrypoint.sh

ENTRYPOINT ["./entrypoint.sh"]
CMD ["etc/restheart.yml"]
EXPOSE 8080 4443
