package stim.davincicode.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/** 게임 방 하나. 대기 중에는 game이 null이고, 시작하면 Game이 만들어진다. */
final class Room {
    static final int[] TURN_CHOICES = { 0, 30, 45, 60, 90 };

    final int id;
    String title;
    String password = "";
    User host;
    int max = 4;
    int turnSec = 60;

    final List<User> players = new ArrayList<>();
    final List<User> spectators = new ArrayList<>();
    final Set<String> kicked = new HashSet<>();

    Game game;
    final Map<Integer, User> gameUsers = new LinkedHashMap<>(); // 시작할 때의 참가자 (중간에 나가도 결과에 남긴다)
    ScheduledFuture<?> timer;
    long deadline;
    int seq; // 상태가 바뀔 때마다 늘려서, 늦게 도착한 타이머를 무시한다

    Room(int id) {
        this.id = id;
    }

    boolean locked() {
        return !password.isEmpty();
    }

    List<User> audience() {
        List<User> all = new ArrayList<>(players);
        all.addAll(spectators);
        return all;
    }

    User member(int id) {
        for (User u : players) if (u.id == id) return u;
        for (User u : spectators) if (u.id == id) return u;
        return null;
    }

    void cancelTimers() {
        if (timer != null) timer.cancel(false);
        timer = null;
    }

    static int normalizeTurn(int sec) {
        for (int t : TURN_CHOICES) if (t == sec) return sec;
        return 60;
    }
}
