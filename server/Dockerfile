FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY ./build/libs/server-all.jar /app/homeserver.jar

RUN mkdir -p /app/data

ENTRYPOINT ["java","-jar","/app/homeserver.jar"]
