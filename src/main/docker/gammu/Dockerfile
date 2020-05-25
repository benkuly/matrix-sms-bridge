FROM ubuntu:focal

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

VOLUME ["/data", "/config"]

RUN apt-get update && apt-get install -y \
        locales \
        openjdk-11-jre-headless \
        gammu gammu-smsd \
        supervisor

RUN echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen \
        && locale-gen en_US.UTF-8

RUN update-rc.d -f gammu-smsd remove

COPY src/main/resources/application.yml /config-default/application.yml

COPY src/main/docker/gammu/supervisord.conf /etc/supervisor/conf.d/supervisord.conf

RUN mkdir -p /var/log/supervisor

EXPOSE 8080
ARG JAR_FILE
COPY ${JAR_FILE} app.jar

ENV CONFIG_LOCATION /config/application.yml
ENV GAMMU_CONFIG /config/gammu-smsdrc
ENTRYPOINT ["/usr/bin/supervisord"]