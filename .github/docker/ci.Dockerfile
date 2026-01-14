FROM container-registry.oracle.com/graalvm/jdk:21

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN microdnf install -y curl unzip findutils shadow-utils \
    && useradd -m ciuser \
    && mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && curl -fL --proto '=https' --tlsv1.2 https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdline-tools.zip \
    && unzip cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm cmdline-tools.zip \
    && yes | sdkmanager --licenses \
    && sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools" \
    && microdnf clean all

WORKDIR /project

# Copy Gradle wrapper and settings first for better caching
COPY --chown=ciuser:ciuser gradle/ gradle/
COPY --chown=ciuser:ciuser gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY --chown=ciuser:ciuser gradle/libs.versions.toml gradle/

# Give execution permission
RUN chmod +x gradlew

# Copy source code explicitly with correct ownership
COPY --chown=ciuser:ciuser composeApp/ composeApp/
COPY --chown=ciuser:ciuser server/ server/
COPY --chown=ciuser:ciuser shared/ shared/

USER ciuser

# Build the server module
# This confirms that the environment is correct and the code compiles
RUN ./gradlew :server:build --no-daemon
