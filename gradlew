#!/bin/sh
# Gradle wrapper launcher; uses the pinned wrapper JAR in gradle/wrapper.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi
exec "$JAVACMD" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
