@echo off
rem DAVINCI CODE 전용 서버 실행 (창을 닫으면 서버도 꺼져요)
rem 포트를 바꾸려면: start-server.bat --port 31000
chcp 65001 > nul
cd /d "%~dp0"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar DavinciCode-Server.jar --name "DAVINCI CODE 서버" %*
pause
