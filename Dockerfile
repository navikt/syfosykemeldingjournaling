FROM navikt/java:10
COPY build/install/* /app
#ENV JAVA_OPTS="'-Dlogback.configurationFile=logback-remote.xml' '-XX:+UseG1GC'"
ENTRYPOINT ["/app/bin/syfosmjoark"]
