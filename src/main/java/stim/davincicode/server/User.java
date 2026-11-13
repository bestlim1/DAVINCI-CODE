package stim.davincicode.server;

import java.util.concurrent.ScheduledFuture;

/** 접속한 사람 한 명. 연결이 잠깐 끊겨도 이 객체는 남아 있어서 재접속하면 이어서 한다. */
final class User {
    final int id;
    String key;           // 기기 ID + 닉네임 (재접속 확인에 쓴다). 닉네임을 바꾸면 같이 바뀐다
    String name;
    int avatar;

    Connection conn;      // 연결이 끊겼으면 null
    Room room;            // 로비에 있으면 null
    boolean spectator;
    boolean ready;
    ScheduledFuture<?> grace; // 재접속 대기 타이머

    long chatWindow;
    int chatCount;
    long lastReact;
    long lastReport;
    long lastProfile;

    User(int id, String key) {
        this.id = id;
        this.key = key;
    }

    void send(String line) {
        Connection c = conn;
        if (c != null) c.send(line);
    }

    boolean online() {
        return conn != null;
    }

    /** 로비 접속자 목록에 보여 줄 위치: L 로비, W 대기실, G 게임 중 */
    String where() {
        if (room == null) return "L";
        return room.game != null ? "G" : "W";
    }
}
