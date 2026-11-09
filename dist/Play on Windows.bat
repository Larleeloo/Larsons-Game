@echo off
setlocal
rem Larson's Game - double-click this file to play (Windows).
rem It needs Java 21 or newer: https://adoptium.net/temurin/releases/?version=21
title Larson's Game
cd /d "%~dp0"

if not exist "%~dp0larsons-game.jar" goto :notunzipped

rem Find a Java 21 or newer: the one on the PATH, JAVA_HOME's, or one in the usual install folders.
set "JAVA="
for /f "delims=" %%j in ('where java 2^>nul') do if not defined JAVA call :try "%%j"
if not defined JAVA if defined JAVA_HOME call :try "%JAVA_HOME%\bin\java.exe"
if not defined JAVA for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\*" "%ProgramFiles%\Java\*" "%ProgramFiles%\Microsoft\*" "%ProgramFiles%\Zulu\*" "%ProgramFiles%\Amazon Corretto\*" "%ProgramFiles%\BellSoft\*") do if not defined JAVA call :try "%%d\bin\java.exe"
if not defined JAVA goto :nojava

echo Starting Larson's Game (Java: %JAVA%)
echo This window shows what the game is doing. Leave it open while you play.
echo.
"%JAVA%" -jar "%~dp0larsons-game.jar" %*
if not "%errorlevel%"=="0" goto :failed
exit /b 0

:failed
echo.
echo  The game stopped with an error.
echo  Please send Larson the file "logs\latest.log" from the game's folder:
echo  %~dp0logs
echo.
pause
exit /b 1

:nojava
echo.
echo  Larson's Game needs Java 21 (or newer), and it is not on this computer yet.
echo.
echo  1. A web page opens now: download "Temurin 21" for Windows - the .msi file - and install it.
echo  2. Then double-click "Play on Windows" again.
echo.
start "" "https://adoptium.net/temurin/releases/?version=21&os=windows&arch=x64&package=jre"
pause
exit /b 1

:notunzipped
echo.
echo  Unzip the game first: right-click Larsons-Game.zip, choose "Extract All...",
echo  then open the folder it makes and double-click "Play on Windows" in there.
echo.
pause
exit /b 1

rem :try "path\to\java.exe" - sets JAVA when that java is version 21 or newer.
:try
if not exist "%~1" exit /b
"%~1" -version 2> "%TEMP%\larsons-game-java.txt"
set "VER="
for /f "usebackq tokens=1-3" %%a in ("%TEMP%\larsons-game-java.txt") do if /i "%%b"=="version" if not defined VER set "VER=%%~c"
if not defined VER exit /b
set "MAJOR=0"
for /f "delims=.-+_ " %%m in ("%VER%") do set /a MAJOR=%%m 2>nul
if %MAJOR% GEQ 21 set "JAVA=%~1"
exit /b
