@echo off
REM ============================================================
REM RENTAL MANAGEMENT SYSTEM - Desktop App Launcher
REM ============================================================
echo Starting Rental Management System...
echo.

cd /d "%~dp0"

REM Run using Maven JavaFX plugin
mvn javafx:run

pause
