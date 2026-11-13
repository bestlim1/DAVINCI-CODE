#!/usr/bin/env sh
# DAVINCI CODE 전용 서버 실행
#   ./start-server.sh                기본 포트 30000
#   ./start-server.sh --port 31000
cd "$(dirname "$0")"
exec java -Dfile.encoding=UTF-8 -jar DavinciCode-Server.jar --name "DAVINCI CODE 서버" "$@"
