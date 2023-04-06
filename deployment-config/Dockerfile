FROM amd64/eclipse-temurin:17-jdk-alpine
VOLUME /tmp
ARG JAR_FILE
COPY $JAR_FILE app.jar
ENV JAVA_OPTS=$JAVA_OPTS
ENTRYPOINT ["java", "${JAVA_OPTS}", "-jar", "/app.jar"]