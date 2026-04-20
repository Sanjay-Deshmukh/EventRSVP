@echo off
echo Starting EventRSVP Web Server...
echo.
echo Please wait while the server starts...
echo.

cd /d "%~dp0"

java -cp "target/classes;%USERPROFILE%\.m2\repository\org\json\json\20230227\json-20230227.jar;%USERPROFILE%\.m2\repository\org\xerial\sqlite-jdbc\3.42.0.0\sqlite-jdbc-3.42.0.0.jar" org.example.EventWebServer

pause
