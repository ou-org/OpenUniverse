#!/bin/sh

######################################################
# OpenUnvierse — The UaC (Universe as Code) platform #
######################################################

JAVA_EXEC=""  # Locate Java
if [ -x "./jre/bin/java" ]; then
  JAVA_EXEC="./jre/bin/java"
elif [ -n "$OU_JAVA_HOME" ]; then
  JAVA_EXEC="$OU_JAVA_HOME/bin/java"
elif [ -n "$JAVA_HOME" ]; then
  JAVA_EXEC="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_EXEC=java
fi

if [ -z "$JAVA_EXEC" ]; then
  echo "Java not found."
  exit 1
fi

# JVM options
JVM_ARGS_FILE="jvm.options"
if [ -f "$JVM_ARGS_FILE" ]; then
  JVM_ARGS=$(cat "$JVM_ARGS_FILE")
else
  JVM_ARGS=""
fi

exec "$JAVA_EXEC" $JVM_ARGS \
  -Dorg.ou.selfcontained=true \
  -jar "$0" "$@"
