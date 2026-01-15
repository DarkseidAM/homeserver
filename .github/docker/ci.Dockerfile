FROM eclipse-temurin:21-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update && apt-get --no-install-recommends install -y curl findutils unzip \
    && useradd -m ciuser \
    && mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && curl -fL --proto '=https' --tlsv1.2 https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdline-tools.zip \
    && unzip cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm cmdline-tools.zip \
    && yes | sdkmanager --licenses \
    && sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools" \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /project

# Copy Gradle wrapper and settings first for better caching
COPY gradle/ gradle/
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/libs.versions.toml gradle/

# Give execution permission
RUN chmod +x gradlew

# Copy source code explicitly
COPY composeApp/ composeApp/
COPY server/ server/
COPY shared/ shared/
COPY androidApp/ androidApp/

# Create necessary writable directories for Gradle and set permissions for ciuser
# limiting write access to only what is needed for the build.
RUN mkdir -p /project/.gradle /project/.kotlin /project/build \
        composeApp/build server/build shared/build androidApp/build \
    && chown -R ciuser:ciuser \
        /project/.gradle /project/.kotlin /project/build \
        composeApp/build server/build shared/build androidApp/build

USER ciuser

# Build the server and android modules
# This confirms that the environment is correct and the code compiles
RUN ./gradlew :server:build :androidApp:assembleDebug --no-daemon
