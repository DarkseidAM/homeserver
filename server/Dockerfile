FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY ./build/libs/server-all.jar /app/homeserver.jar

RUN mkdir -p /app/data
RUN mkdir -p /app/config

ENTRYPOINT ["java","-jar","/app/homeserver.jar"]
