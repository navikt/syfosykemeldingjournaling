FROM navikt/java:11
COPY build/libs/*.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml -Xmx384M -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.bin'
ENV APPLICATION_PROFILE="remote"
