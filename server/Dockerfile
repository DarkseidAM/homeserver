FROM ghcr.io/graalvm/jdk-community:21

WORKDIR /app

COPY ./build/libs/server-all.jar /app/homeserver.jar

RUN mkdir -p /app/data
RUN mkdir -p /app/config

ENTRYPOINT ["java", "-XX:+UseParallelGC", "-jar", "/app/homeserver.jar"]
