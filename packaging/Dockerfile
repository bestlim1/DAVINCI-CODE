# DAVINCI CODE 전용 서버 컨테이너
#   (프로젝트 폴더에서) .\build.ps1 -JarOnly
#   docker build -f packaging/Dockerfile -t davincicode-server .
#   docker run -d -p 30000:30000 -v davincicode-data:/data --name davincicode davincicode-server
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/dist/DavinciCode-Server.jar /app/server.jar
EXPOSE 30000
VOLUME ["/data"]
# 30000번 포트 하나로 PC 앱(TCP)과 웹 브라우저(http://주소:30000, WebSocket)를 모두 받는다
# 클라우드에서는 같은 네트워크 검색(UDP)이 필요 없어서 --no-lan
CMD ["java", "-Dfile.encoding=UTF-8", "-Xmx256m", "-jar", "/app/server.jar", "--port", "30000", "--data", "/data", "--no-lan", "--name", "DAVINCI CODE 공식 서버"]
