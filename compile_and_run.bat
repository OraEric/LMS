@echo off
title Student Library System
cls

cd /d "%~dp0"

echo     Student Library Project
echo.

where javac >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java JDK was not found.
    echo.
    echo Please install a JDK and make sure javac is in your PATH.
    echo.
    pause
    exit /b 1
)

echo [1/2] Compiling Java code...
javac -encoding UTF-8 -cp ".;*" *.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Running Java server...
echo.
echo Open Web Page in your Browser:
echo http://localhost:8080/login.html
echo.

java -cp ".;*" Main

pause