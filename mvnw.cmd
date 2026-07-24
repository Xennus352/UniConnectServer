@echo off
@setlocal enabledelayedexpansion

set BASEDIR=%~dp0

set MAVEN_WRAPPER_JAR=%BASEDIR%.mvn\wrapper\maven-wrapper.jar

if exist "%JAVA_HOME%" (
    set JAVACMD="%JAVA_HOME%\bin\java.exe"
) else (
    set JAVACMD="java.exe"
)

if not exist "%MAVEN_WRAPPER_JAR%" (
    echo Maven Wrapper jar not found at %MAVEN_WRAPPER_JAR%
    exit /b 1
)

%JAVACMD% -Xmx64m -Xms64m %JAVA_OPTS% %MAVEN_OPTS% -jar "%MAVEN_WRAPPER_JAR%" %*