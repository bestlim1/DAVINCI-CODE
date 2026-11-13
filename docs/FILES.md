# 파일 안내

DAVINCI CODE(다빈치 코드 온라인 완성판)의 모든 파일이 어디에 쓰이는지 정리했어요.
패키지는 세 덩어리예요: **common**(클라이언트·서버 공용), **server**(서버), **client**(앱 화면). `client/ui`는 디자인 부품이에요.

## 보고서용: 네트워크 핵심 코드 읽는 순서

1. `common/Proto.java` → `common/Msg.java` : 메시지를 어떻게 만들고 나누는지
2. `server/GameServer.java` (AcceptServer) → `server/Gate.java` (앱/웹 가르기) → `server/Connection.java` (읽기·쓰기 스레드) + `server/Transport.java` (TCP·WebSocket) : 접속을 받고 메시지를 주고받는 부분
3. `server/Hub.java` : 받은 메시지를 명령어별로 처리하고, 한 명에게/방 전체에/로비 전체에 보내는 부분
4. `server/Game.java`의 `snapshot()` : 받는 사람마다 볼 수 있는 정보만 담는 정보 은닉
5. `client/NetClient.java` → `client/App.java`의 `onMessage()` : 클라이언트가 받은 메시지를 화면에 반영하는 부분

## 맨 위 폴더

| 파일 | 하는 일 |
| --- | --- |
| `README.md` | 프로젝트 소개, 실행·빌드 방법, 친구와 하는 방법, 구조, 수업 코드와 달라진 점 |
| `build.ps1` | Windows 빌드 스크립트: 컴파일 → 테스트 → JAR → 실행 파일 압축본 → 설치 프로그램(.exe) |
| `build.sh` | macOS/Linux 빌드 스크립트 |
| `.gitignore` | Git에 올리지 않을 파일 목록 (빌드 결과, 서버 데이터 등) |

## src/main/java/stim/davincicode/common — 클라이언트와 서버가 함께 쓰는 코드

| 파일 | 하는 일 |
| --- | --- |
| `Proto.java` | 통신 규약 정의. 명령어 이름(HELLO, CHAT, GUESS …), 포트(30000, 같은 Wi-Fi 검색 30099), 메시지 만들기(`encode`, `record`, `list`), 사용자 입력에서 구분자 지우기(`clean`) |
| `Msg.java` | 받은 한 줄을 명령어와 필드로 나누고(`parse`), 필드를 숫자·글자·목록으로 꺼내기 |
| `GameView.java` | 서버가 보낸 STATE를 해석한 "한 사람이 볼 수 있는 게임 상태". 타일 표기 규칙(`17B5*` 등)도 여기 있음. 화면은 이것만 보고 그린다. 시작 패 고르기 단계(`SETUP`)와 자리마다 고른 검정 수도 담음 |
| `Deduction.java` | 추리 계산. 정렬 규칙과 이미 아는 숫자로 숨은 타일마다 가능한 숫자를 계산 (조커 위치까지 경우를 나눠 계산). 숫자 고르기 창이 이미 보이는 숫자를 지우는 데 쓰고, 테스트용 자동 플레이어도 씀 |
| `ChatFilter.java` | 채팅 욕설 필터. 글자 사이에 공백·숫자를 끼워도 걸러서 ●로 가림 |
| `Names.java` | 닉네임 규칙(2~10글자, 한글·영문·숫자·_), 방 제목·채팅 길이 제한, 캐릭터 수 |

## src/main/java/stim/davincicode/server — 서버

| 파일 | 하는 일 |
| --- | --- |
| `Gate.java` | **입구**. 같은 포트로 들어온 연결의 첫 바이트를 보고 PC 앱(TCP)인지 웹 브라우저(HTTP)인지 가른다. 브라우저에는 웹 게임 화면 파일을 보내 주고, `/ws` 요청은 WebSocket으로 바꿔서 게임 연결로 넘긴다 |
| `Transport.java` | 메시지 한 줄을 주고받는 방법 두 가지: PC 앱용 `Tcp`(readUTF/writeUTF)와 웹용 `WebSocket`(프레임 읽기·쓰기를 직접 구현) |
| `ServerMain.java` | 창 없는 전용 서버의 시작점. 실행 옵션(`--port`, `--data` 등)과 콘솔 명령(status, rooms, notice, kick, stop) 처리, 로그 파일 저장 |
| `GameServer.java` | 서버 본체. ServerSocket을 열고 **AcceptServer 스레드**로 접속을 받고, 모든 상태 변경을 처리하는 **게임 로직 스레드**와 타이머를 관리. 앱 안에서 여는 서버(내 PC에서 열기)도 이 클래스를 씀 |
| `Connection.java` | 클라이언트 1명과의 연결 (수업 코드의 UserService). **읽기 스레드**가 `readUTF`로 받아 로직 스레드에 넘기고, **쓰기 스레드**가 큐에 쌓인 메시지를 `writeUTF`로 보냄. 초당 40개 넘게 보내면 도배로 끊음 |
| `Hub.java` | 서버의 두뇌. 접속 인사·재접속, 로비 목록, 방 만들기·참가·관전·준비·설정·내보내기·시작, 채팅·귓속말·리액션·신고, 프로필(닉네임·캐릭터) 바꾸기, 시작 패 고르기·게임 행동 처리, 제한 시간 예약(시작 패는 20초), 사람마다 다른 상태 전송, 결과(순위) 전송 |
| `Room.java` | 방 하나의 데이터: 제목, 비밀번호, 최대 인원, 제한 시간 설정, 참가자, 관전자, 진행 중인 게임, 타이머 |
| `User.java` | 접속자 한 명: 연결, 있는 곳(로비/방), 준비 여부, 재접속 대기 타이머, 채팅 도배 계산용 값 |
| `Game.java` | **규칙 엔진**. 타일 만들기·섞기, 시작 패 고르기(`SETUP`: 모두 동시에 검정 개수 선택 → 고른 대로 나눠 주기, 시간 초과면 임의로), 정렬, 가져오기/조커 놓기/추리/멈추기/내 타일 공개/시간 초과/기권, 탈락과 순위. `snapshot()`이 받는 사람마다 볼 수 있는 정보만 담은 STATE를 만듦 (정보 은닉의 핵심) |
| `Tile.java` | 서버만 아는 진짜 타일: 색, 숫자, 공개 여부. 받는 사람에 맞춰 숫자를 가린 표기(`codeFor`)를 만듦 |
| `Reports.java` | 신고 기록(`data/reports.log`)에 신고자·대상·사유·대상의 최근 채팅을 한 줄씩 덧붙임 |
| `LanBeacon.java` | 같은 Wi-Fi에서 서버를 찾을 수 있게 UDP 30099 포트로 "DAVINCICODE?"에 서버 이름·포트·접속자 수를 답함 |

## src/main/java/stim/davincicode/client — 앱 (화면과 연결)

| 파일 | 하는 일 |
| --- | --- |
| `ClientMain.java` | 앱 시작점. 글꼴·Swing 기본 스타일 설정, 예상 못 한 오류를 `crash.log`에 남기고 안내 |
| `App.java` | 앱 창 하나와 화면 전환(타이틀 → 접속 → 로비 → 방), 서버 연결·자동 재접속, 내 PC에서 서버 열기, **받은 메시지를 알맞은 화면에 나눠 주기(`onMessage`)**, 종료 확인 |
| `NetClient.java` | 서버와의 연결. 읽기 스레드가 받은 메시지를 화면 스레드로 넘기고, 쓰기 스레드가 보냄. 10초마다 PING으로 연결 확인·지연 시간 측정 |
| `LanFinder.java` | 같은 Wi-Fi의 서버를 UDP 브로드캐스트로 찾기, 이 PC의 IP 주소 알아내기 |
| `Model.java` | 서버가 보낸 방·로비 정보를 화면에서 쓰기 좋은 객체로 바꿈 |
| `Prefs.java` | 사용자 설정 저장(닉네임, 캐릭터, 소리, 최근 서버, 차단 목록, 기기 ID, 창 크기). `-Ddc.profile=2`로 프로필 분리 |
| `Sound.java` | 효과음을 코드로 합성해서 재생 (가져오기, 정답, 오답, 내 차례, 승리 등) |
| `TitleScreen.java` | 첫 화면: 떠다니는 타일 배경, 로고, 온라인 대전/내 PC에서 서버 열기/게임 방법/설정, 프로필 |
| `ConnectScreen.java` | 어디서 할지 고르는 화면: 같은 Wi-Fi 서버 목록, 내 PC에서 서버 열기, 주소로 접속, 공식 서버 |
| `LobbyScreen.java` | 로비: 방 목록(필터), 빠른 시작, 방 만들기, 접속자 목록, 로비 채팅, 서버 정보·핑, 오른쪽 위 내 프로필(누르면 닉네임·캐릭터 바꾸기) |
| `RoomScreen.java` | 방 화면: 대기실(자리 카드, 내보내기, 방 설정, 준비/시작/참가 버튼), 게임판과 채팅 배치, 결과 창 |
| `BoardView.java` | **게임판**: 시작 패 고르기 카드(검정·흰색 조합), 상대·내 타일 줄, 더미, 가져온 타일, 제한 시간, 숫자 고르기 창(이미 보이는 숫자는 지워 줌), 조커 놓을 자리, 우클릭 메모, 애니메이션(뒤집기·흔들기·축하) |
| `ChatPanel.java` | **채팅창**: 말풍선 목록, 귓속말(`/w`), 빠른 채팅, 리액션 버튼, 우클릭 메뉴(귓속말·차단·신고), 게임 기록 탭 |
| `Dialogs.java` | 대화상자 모음: 프로필(처음 이름 정하기, 접속 중 바꾸기), 방 만들기, 설정, 신고, 정보·개인정보, 게임 방법(그림 6쪽) |
| `TopBar.java` | 화면 위쪽 막대와 작은 상태 표시(서버 이름, 핑) |

## src/main/java/stim/davincicode/client/ui — 디자인 부품

| 파일 | 하는 일 |
| --- | --- |
| `Theme.java` | 색과 글꼴 (한글이 잘 나오는 글꼴 자동 선택) |
| `UI.java` | 그리기 도우미: 부드러운 그림자, 글자 그리기, 줄바꿈, 말줄임, 글자 폭을 정확히 재는 라벨 |
| `Anim.java` | 애니메이션 시계와 부드러운 움직임 계산 |
| `Btn.java` | 버튼 (기본/보조/외곽선/위험 등) |
| `Field.java` | 둥근 입력창, 비밀번호 입력창, 글자 수 제한 |
| `Controls.java` | 스위치, 여러 개 중 하나 고르기, 슬라이더, 카드, 얇은 스크롤바, 칩, 설정 줄 |
| `Modal.java` | 창 안에 띄우는 대화상자(확인·입력), 말풍선 메뉴(팝오버) |
| `Toast.java` | 위쪽에 잠깐 뜨는 알림 |
| `Spinner.java` | 기다릴 때 도는 표시 |
| `Icons.java` | 코드로 그린 아이콘 40여 개 |
| `Art.java` | 캐릭터 얼굴 8종, 타일(앞면·뒷면·조커·공개 표시), 로고, 배경, 게임판 펠트, 앱 아이콘 |

## src/main/resources/stim/davincicode/web — 웹 버전 (브라우저용)

서버가 이 파일들을 브라우저에 보내 준다. 빌드하면 JAR 안에 같이 들어간다.

| 파일 | 하는 일 |
| --- | --- |
| `index.html` | 웹 화면 뼈대: 첫 화면(캐릭터·닉네임), 로비, 방 |
| `style.css` | PC 앱과 같은 색·타일·말풍선 디자인. 휴대폰 화면에서는 아래쪽 탭과 채팅 서랍으로 바뀐다 |
| `proto.js` | Java `common` 패키지를 JavaScript로 옮긴 것: 메시지 만들기·나누기, 게임 상태 해석, 추리 계산 (Java와 같은 답이 나오는지 테스트함) |
| `app.js` | 웹 화면 전체: WebSocket 연결·자동 재접속, 로비(내 프로필 눌러 닉네임·캐릭터 바꾸기), 방 설정, 게임판(시작 패 고르기·타일 고르기·숫자 고르기·조커 자리·메모), 채팅·귓속말·리액션·차단·신고, 효과음(Web Audio), 결과·설정·게임 방법 |
| `manifest.webmanifest`, `icon-192.png`, `icon-512.png` | 휴대폰 홈 화면에 추가할 때 쓰는 이름과 아이콘 |

## src/test/java — 테스트와 도구

| 파일 | 하는 일 |
| --- | --- |
| `server/EngineTest.java` | 네트워크 없이 규칙 엔진·시작 패 고르기 검사 + 자동 대전 3,000판 (9,108개 항목) |
| `server/NetTest.java` | 실제 소켓으로 접속·채팅·방·대전·시작 패 고르기·관전·재접속·기권·신고·프로필 검사 (45개 항목) |
| `server/WebTest.java` | 웹 입구 검사: 웹 파일 전송·접근 차단·WebSocket 프레임·앱↔웹 채팅·웹과 앱이 한 방에서 한 판 (33개 항목) |
| `server/DeductionDump.java` | 자동 대전 장면(시작 패 고르기 포함)과 Java 해석·계산 결과를 저장 (웹 버전과 비교용) |
| `server/SimPlayer.java` | 테스트용 자동 플레이어. 자기에게 보이는 STATE만 보고 둔다 (규칙 엔진을 수천 판 돌려 보는 데 씀) |
| `../web/parity.mjs` | (`src/test/web/`) 저장한 장면으로 웹 `proto.js`가 Java와 같은 답을 내는지 Node.js로 비교 |
| `client/FlowCheck.java` | 앱 창을 띄워 내 PC 서버 열기 → 친구와 한 판(게임판에서 시작 패 카드 클릭) → 같은 Wi-Fi 검색 흐름 검사 (10개 항목) |
| `client/UiShots.java` | 앱을 자동으로 조작하며 모든 화면 스크린샷 저장 |
| `tools/IconGen.java` | 코드로 그린 앱 아이콘을 `icon.ico`, `icon.png`, 스토어용 PNG로 저장 |

## packaging — 배포용 파일

| 파일 | 하는 일 |
| --- | --- |
| `icon.ico`, `icon.png`, `icon-1024.png` | 앱 아이콘 (설치 프로그램·창·스토어용) |
| `store-44.png` ~ `store-300.png` | Microsoft Store 타일 이미지 |
| `start-server.bat`, `start-server.sh` | 전용 서버를 더블클릭/명령 한 줄로 실행 |
| `Dockerfile` | 클라우드에 전용 서버를 컨테이너로 띄우는 설정 |

## docs — 문서

| 파일 | 하는 일 |
| --- | --- |
| `FILES.md` | 이 문서 |
| `PROTOCOL.md` | 통신 규약: 모든 메시지 형식, 타일 표기, 정보 은닉, 시간 제한·재접속 규칙 |
| `PRIVACY.md` | 앱이 저장하는 정보와 지우는 방법 |
| `screenshots/` | 화면 캡처 12장 (PC 앱 10장, 웹 2장 — README용) |
