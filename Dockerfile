FROM openjdk:8-jdk-alpine
ADD https://github.com/TonyMacDonald1995/MadHatter/releases/latest/download/MadHatter.jar /opt
WORKDIR /opt
ENTRYPOINT [ "java", "-jar", "MadHatter.jar" ]