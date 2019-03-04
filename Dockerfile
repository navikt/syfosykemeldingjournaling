FROM navikt/java:11
COPY build/libs/syfosmjoark-*-all.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml -XX:MaxRAMPercentage=75 -Xmx256M'
ENV APPLICATION_PROFILE="remote"
