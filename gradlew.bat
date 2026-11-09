@ECHO OFF
SETLOCAL
SET DIR=%~dp0
SET WRAPPER_JAR=%DIR%gradle\wrapper\gradle-wrapper.jar
SET WRAPPER_MAIN=org.gradle.wrapper.GradleWrapperMain
IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO gradle-wrapper.jar missing
  EXIT /B 1
)
SET JAVA_EXE=java
IF DEFINED JAVA_HOME SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" %WRAPPER_MAIN% %*
ENDLOCAL
