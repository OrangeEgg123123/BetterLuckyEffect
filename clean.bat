@echo off
rem ---------------------------------------------------------------
rem  Local toolchain bootstrap (JDK 21 + project-local Gradle home).
rem ---------------------------------------------------------------
setlocal
set "JAVA_HOME=%~dp0.tools\jdk-21"
set "GRADLE_USER_HOME=%~dp0.gradle-home"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JDK not found at "%JAVA_HOME%".
  exit /b 1
)
call "%~dp0gradlew.bat" clean %*
exit /b %ERRORLEVEL%