#!/usr/bin/env bash
# DAVINCI CODE 빌드 스크립트 (macOS / Linux)
#   ./build.sh            컴파일 → 테스트 → JAR → 실행 파일 패키지
#   ./build.sh --skip-tests
#   ./build.sh --jar-only
set -euo pipefail
cd "$(dirname "$0")"
VERSION=1.3.1
SKIP_TESTS=0
JAR_ONLY=0
for a in "$@"; do
  case "$a" in
    --skip-tests) SKIP_TESTS=1 ;;
    --jar-only) JAR_ONLY=1 ;;
  esac
done

rm -rf build/classes build/test-classes build/dist build/jpackage-input build/package
mkdir -p build/classes build/test-classes build/dist build/jpackage-input build/package

echo "== 컴파일"
javac --release 17 -encoding UTF-8 -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/   # 웹 게임 파일

if [ "$SKIP_TESTS" = 0 ]; then
  echo "== 테스트"
  javac --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes $(find src/test/java -name '*.java')
  java -cp build/classes:build/test-classes stim.davincicode.server.EngineTest
  java -cp build/classes:build/test-classes stim.davincicode.server.NetTest
  java -cp build/classes:build/test-classes stim.davincicode.server.WebTest
fi

echo "== JAR"
jar --create --file build/dist/DavinciCode.jar --main-class stim.davincicode.client.ClientMain -C build/classes .
jar --create --file build/dist/DavinciCode-Server.jar --main-class stim.davincicode.server.ServerMain \
  -C build/classes stim/davincicode/common -C build/classes stim/davincicode/server -C build/classes stim/davincicode/web
cp packaging/start-server.sh packaging/start-server.bat build/dist/
[ "$JAR_ONLY" = 1 ] && exit 0

echo "== 실행 파일 패키지"
cp build/dist/DavinciCode.jar build/jpackage-input/
[ -f official-server.txt ] && cp official-server.txt build/jpackage-input/
ICON=packaging/icon.png
TYPE=app-image
case "$(uname)" in
  Darwin) TYPE=dmg ;;
esac
jpackage --type "$TYPE" --name "DAVINCI CODE" --app-version "$VERSION" --vendor "Team STIM" \
  --description "DAVINCI CODE - Number Deduction Battle" --icon "$ICON" \
  --input build/jpackage-input --main-jar DavinciCode.jar --main-class stim.davincicode.client.ClientMain \
  --add-modules java.base,java.desktop,jdk.charsets \
  --jlink-options "--strip-debug --no-header-files --no-man-pages --strip-native-commands" \
  --java-options -Dfile.encoding=UTF-8 --java-options -Xmx256m \
  --dest build/package
echo "결과물: build/dist, build/package"
