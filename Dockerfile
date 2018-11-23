FROM navikt/java:10

COPY build/libs/syfosmjoark-*-all.jar app.jar
#ENV JAVA_OPTS="'-Dlogback.configurationFile=logback-remote.xml' '-XX:+UseG1GC'"
