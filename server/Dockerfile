FROM container-registry.oracle.com/graalvm/jdk:21

# Create a non-root user
RUN useradd -m serveruser

WORKDIR /app

# Ensure the user has permissions to the app directory
RUN mkdir -p /app/data /app/config && chown -R serveruser:serveruser /app

# Copy the jar with correct ownership
COPY --chown=serveruser:serveruser ./build/libs/server-all.jar /app/homeserver.jar

USER serveruser

ENTRYPOINT ["java", "-XX:+UseParallelGC", "-jar", "/app/homeserver.jar"]
