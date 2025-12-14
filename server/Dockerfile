FROM eclipse-temurin:21-jdk-alpine

RUN mkdir /app

COPY ./build/libs/server-all.jar /app/homeserver.jar

ENTRYPOINT ["java","-jar","/app/homeserver.jar"]
