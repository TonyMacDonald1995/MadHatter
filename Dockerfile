FROM eclipse-temurin:11-jre-alpine

WORKDIR /app

COPY build/libs/MadHatter.jar /app

ENTRYPOINT [ "java", "-jar", "MadHatter.jar" ]