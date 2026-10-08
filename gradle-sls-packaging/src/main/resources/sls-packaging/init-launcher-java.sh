# Select the JVM used to run the launcher configured by 'distribution.javaLauncher'
if [ -n "@javaHome@" ]; then
  LAUNCHER_JAVA_CMD="@javaHome@/bin/java"
elif [ -n "$JAVA_HOME" ]; then
  LAUNCHER_JAVA_CMD="$JAVA_HOME/bin/java"
else
  LAUNCHER_JAVA_CMD="java"
fi

LAUNCHER_CLASSPATH="@launcherClasspath@"
LAUNCHER_CMD="$LAUNCHER_JAVA_CMD -cp $LAUNCHER_CLASSPATH @launcherMainClass@"
GO_INIT_CMD="$LAUNCHER_JAVA_CMD -cp $LAUNCHER_CLASSPATH @initMainClass@"
