@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  com.arianesline.ariane startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and COM_ARIANESLINE_ARIANE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=
set MODULE_PATH=%APP_HOME%\lib\com.arianesline.ariane-25.2.1.jar;%APP_HOME%\lib\com.arianesline.cavelib-25.2.1.jar;%APP_HOME%\lib\com.arianesline.ariane.plugin.api-25.2.1.jar;%APP_HOME%\lib\com.arianesline.cavelib.api-25.2.1.jar;%APP_HOME%\lib\com.arianesline.controls-25.2.1.jar;%APP_HOME%\lib\com.arianesline.licensing-25.2.1.jar;%APP_HOME%\lib\com.arianesline.maptile-25.2.1.jar;%APP_HOME%\lib\com.arianesline.mnemo-25.2.1.jar;%APP_HOME%\lib\com.arianesline.graph-25.2.1.jar;%APP_HOME%\lib\com.arianesline.IGRF-25.2.1.jar;%APP_HOME%\lib\com.arianesline.typedef-25.2.1.jar;%APP_HOME%\lib\com.arianesline.obj-25.2.1.jar;%APP_HOME%\lib\com.arianesline.cuda-25.2.1.jar;%APP_HOME%\lib\com.arianesline.linkedmedia-25.2.1.jar;%APP_HOME%\lib\jaxb-runtime-4.0.5.jar;%APP_HOME%\lib\jaxb-core-4.0.5.jar;%APP_HOME%\lib\jakarta.xml.bind-api-4.0.2.jar;%APP_HOME%\lib\parsson-1.1.4.jar;%APP_HOME%\lib\jakarta.json-api-2.1.3.jar;%APP_HOME%\lib\jsat-25.2.1.jar;%APP_HOME%\lib\javafx-fxml-23.0.1-mac-aarch64.jar;%APP_HOME%\lib\javafx-web-23.0.1-mac-aarch64.jar;%APP_HOME%\lib\javafx-controls-23.0.1-mac-aarch64.jar;%APP_HOME%\lib\javafx-media-23.0.1-mac-aarch64.jar;%APP_HOME%\lib\javafx-swing-23.0.1-mac-aarch64.jar;%APP_HOME%\lib\javafx-graphics-23.0.1-mac-aarch64.jar;%APP_HOME%\lib\javafx-base-23.0.1-mac-aarch64.jar;%APP_HOME%\lib\jSerialComm-2.11.0.jar;%APP_HOME%\lib\angus-activation-2.0.2.jar;%APP_HOME%\lib\jakarta.activation-api-2.1.3.jar;%APP_HOME%\lib\txw2-4.0.5.jar;%APP_HOME%\lib\istack-commons-runtime-4.1.2.jar

@rem Execute com.arianesline.ariane
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %COM_ARIANESLINE_ARIANE_OPTS%  -classpath "%CLASSPATH%" --module-path "%MODULE_PATH%" --module com.arianesline.ariane/com.arianesline.ariane.ArianeLauncher %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable COM_ARIANESLINE_ARIANE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%COM_ARIANESLINE_ARIANE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
