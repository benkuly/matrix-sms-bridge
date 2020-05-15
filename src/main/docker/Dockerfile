FROM openjdk:11-jre

VOLUME ["/etc/kannel", "/var/spool/kannel", "/var/log/kannel"]

RUN apt-get update && apt-get install -y \
        kannel \
        supervisor

COPY src/main/docker/kannel /etc/default/kannel
COPY src/main/resources/application.yml /config-default/application.yml

COPY src/main/docker/supervisord.conf /etc/supervisor/conf.d/supervisord.conf

EXPOSE 8080
ARG JAR_FILE
COPY ${JAR_FILE} app.jar

ENV CONFIG_LOCATION /config/
ENTRYPOINT ["/usr/bin/supervisord"]