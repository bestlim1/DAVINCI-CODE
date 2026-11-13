package stim.davincicode.common;

import java.util.Collection;

/**
 * 클라이언트와 서버가 함께 쓰는 프로토콜 정의.
 *
 * 한 메시지는 DataOutputStream.writeUTF 한 번으로 보낸다 (수업 코드와 같은 방식).
 * 메시지 안에서는 명령어와 필드를 탭(\t)으로 나누고,
 * 목록 필드는 레코드를 RS(\u001E)로, 레코드 안의 값을 US(\u001F)로 나눈다.
 * 사용자가 입력한 문자열은 {@link #clean}으로 제어 문자를 지워서 구분자와 절대 섞이지 않게 한다.
 */
public final class Proto {
    private Proto() { }

    public static final int VERSION = 3;
    public static final String APP_NAME = "다빈치 코드";
    public static final String APP_NAME_EN = "DAVINCI CODE";
    public static final String APP_VERSION = "1.3.1";
    public static final int DEFAULT_PORT = 30000;
    public static final int LAN_PORT = 30099;
    public static final String LAN_QUERY = "DAVINCICODE?";
    public static final String LAN_REPLY = "DAVINCICODE!";

    public static final char FS = '\t';
    public static final char RS = '\u001E';
    public static final char US = '\u001F';

    // ---- 클라이언트 → 서버 ----
    public static final String HELLO = "HELLO";       // ver, deviceId, name, avatar
    public static final String PING = "PING";         // clientTime
    public static final String CHAT = "CHAT";         // text
    public static final String WHISPER = "WHISPER";   // targetName, text
    public static final String REACT = "REACT";       // code
    public static final String CREATE = "CREATE";     // title, max, password, turnSec
    public static final String JOIN = "JOIN";         // roomId, password
    public static final String SPECTATE = "SPECTATE"; // roomId, password
    public static final String QUICK = "QUICK";
    public static final String LEAVE = "LEAVE";
    public static final String READY = "READY";       // 1/0
    public static final String SETTINGS = "SETTINGS"; // title, max, password, turnSec
    public static final String KICK = "KICK";         // userId
    public static final String START = "START";
    public static final String PICK = "PICK";         // 시작 패로 받을 검정 타일 수 (나머지는 흰색)
    public static final String DRAW = "DRAW";         // color B/W
    public static final String JOKER = "JOKER";       // position
    public static final String GUESS = "GUESS";       // targetId, tileUid, value(0~11, J)
    public static final String STOP = "STOP";
    public static final String REVEAL = "REVEAL";     // tileUid (내 타일 공개)
    public static final String REPORT = "REPORT";     // targetName, reason
    public static final String PROFILE = "PROFILE";   // name, avatar (로비에서 닉네임·캐릭터 바꾸기). 서버는 성공하면 PROFILE name, avatar 로 답한다

    // ---- 서버 → 클라이언트 ----
    public static final String WELCOME = "WELCOME";   // userId, name, serverName, resumed
    public static final String PONG = "PONG";         // clientTime
    public static final String ERROR = "ERROR";       // code, message
    public static final String NOTICE = "NOTICE";     // message
    public static final String LOBBY = "LOBBY";       // rooms, users
    public static final String ROOM = "ROOM";         // 방 정보 (방 안의 모두에게)
    public static final String LEFT = "LEFT";         // reason
    public static final String STATE = "STATE";       // 게임 상태 (받는 사람마다 다름)
    public static final String EVENT = "EVENT";       // type, ...
    public static final String RESULT = "RESULT";     // ranks
    public static final String SYS = "SYS";           // scope, text
    public static final String KICKED = "KICKED";     // reason (연결 종료 직전)

    /** 명령어와 필드를 탭으로 이어 한 줄로 만든다. */
    public static String encode(String cmd, Object... fields) {
        StringBuilder sb = new StringBuilder(cmd);
        for (Object f : fields) {
            sb.append(FS).append(f == null ? "" : f.toString());
        }
        return sb.toString();
    }

    /** 레코드 하나 (값들을 US로 잇는다). */
    public static String record(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(US);
            sb.append(values[i] == null ? "" : values[i].toString());
        }
        return sb.toString();
    }

    /** 레코드 목록 (RS로 잇는다). */
    public static String list(Collection<String> records) {
        return String.join(String.valueOf(RS), records);
    }

    /** 사용자 입력에서 제어 문자(구분자 포함)를 공백으로 바꾸고, 앞뒤 공백을 지우고, 길이를 자른다. */
    public static String clean(String s, int maxLen) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c < 0x20 || c == 0x7F ? ' ' : c);
        }
        String r = sb.toString().trim().replaceAll(" {2,}", " ");
        if (r.length() > maxLen) r = r.substring(0, maxLen).trim();
        return r;
    }

    public static String bool(boolean b) {
        return b ? "1" : "0";
    }

    /** 로그에서 보기 좋게 구분자를 바꿔 준다. */
    public static String pretty(String line) {
        return line.replace(FS, ' ').replace(RS, '|').replace(US, ',');
    }
}
