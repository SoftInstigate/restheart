FROM openjdk:8u111-jre-alpine

MAINTAINER SoftInstigate <info@softinstigate.com>

RUN apk upgrade --update && apk add --update libstdc++ curl ca-certificates bash

WORKDIR /opt/restheart
COPY etc/* /opt/restheart/etc/
COPY entrypoint.sh /opt/restheart/
COPY restheart.jar /opt/restheart/

RUN chmod +x /opt/restheart/entrypoint.sh

ENTRYPOINT ["./entrypoint.sh"]
CMD ["etc/restheart.yml"]
EXPOSE 8080 4443
