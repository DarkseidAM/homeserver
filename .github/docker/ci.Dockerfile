FROM container-registry.oracle.com/graalvm/jdk:21

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN microdnf install -y wget unzip findutils

# Install Android SDK Command Line Tools
# Version 11.0 (11076708)
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip \
    && unzip cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools \
    && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm cmdline-tools.zip

# Accept licenses and install platform
RUN yes | sdkmanager --licenses \
    && sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools"

WORKDIR /project

# Copy Gradle wrapper and settings first for better caching
COPY gradle/ gradle/
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/libs.versions.toml gradle/

# Give execution permission
RUN chmod +x gradlew

# Download dependencies (this step might fail if subprojects aren't there, so we might need to copy everything)
# To be safe and simple, we copy everything. 
COPY . .

# Build the server module
# This confirms that the environment is correct and the code compiles
RUN ./gradlew :server:build --no-daemon
