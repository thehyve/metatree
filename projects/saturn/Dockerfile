FROM openjdk:17

ADD build/distributions/*.tar /opt/
COPY entrypoint.sh /entrypoint.sh
EXPOSE 8090

USER root

RUN chmod u+x /entrypoint.sh

WORKDIR "/opt/config"
ENTRYPOINT ["/entrypoint.sh"]
