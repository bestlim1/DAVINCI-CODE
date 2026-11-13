package stim.davincicode.common;

import java.util.ArrayList;
import java.util.List;

/**
 * STATE 메시지를 해석한 "한 사람이 볼 수 있는 게임 화면".
 * 서버는 받는 사람마다 볼 수 있는 정보만 담아서 보내고, 클라이언트 화면은 이 객체만 보고 그린다.
 *
 * 타일 표기: [uid][색 B/W][숫자 0~11 | J(조커) | ?(모름)][* 공개됨]
 * 예) "17B5*" = uid 17, 검정 5, 공개된 타일 / "3W?" = uid 3, 흰색, 숫자 모름
 */
public final class GameView {
    public static final int UNKNOWN = -2;
    public static final int JOKER = -1;
    public static final int PICK_HIDDEN = -9;

    /** SETUP: 시작 패의 색(검정·흰색 개수)을 고르는 중 */
    public enum Phase { SETUP, DRAW, JOKER, GUESS, CONTINUE, REVEAL_SELF, OVER }

    public static final class TileView {
        public int uid;
        public char color;
        public int value = UNKNOWN;
        public boolean revealed;

        public boolean known() {
            return value != UNKNOWN;
        }

        public boolean joker() {
            return value == JOKER;
        }

        public boolean black() {
            return color == 'B';
        }

        public String face() {
            if (value == JOKER) return "-";
            if (value == UNKNOWN) return "?";
            return String.valueOf(value);
        }
    }

    public static final class SeatView {
        public int pid;
        public boolean alive;
        public boolean connected;
        /** 시작 패로 고른 검정 타일 수. 안 골랐으면 -1, 다른 사람이 골랐지만 아직 비밀이면 PICK_HIDDEN */
        public int pick = -1;
        public final List<TileView> tiles = new ArrayList<>();

        public boolean picked() {
            return pick != -1;
        }

        public int hiddenCount() {
            int n = 0;
            for (TileView t : tiles) if (!t.revealed) n++;
            return n;
        }

        public int indexOf(int uid) {
            for (int i = 0; i < tiles.size(); i++) if (tiles.get(i).uid == uid) return i;
            return -1;
        }
    }

    public Phase phase = Phase.DRAW;
    public int turnPid;
    public long remainingMs = -1;
    public int deckB, deckW;
    public TileView pending;          // 이번 차례에 가져온 타일 (없으면 null)
    public int pendingPos = -1;       // 조커를 놓을 위치
    public int streak;                // 이번 차례에 연속으로 맞힌 횟수
    public int turnSec;
    public int handSize = 4;          // 처음에 받는 타일 수
    public int viewerPid;
    public final List<SeatView> seats = new ArrayList<>();

    public SeatView seat(int pid) {
        for (SeatView s : seats) if (s.pid == pid) return s;
        return null;
    }

    public SeatView me() {
        return seat(viewerPid);
    }

    public boolean myTurn() {
        return turnPid == viewerPid && phase != Phase.OVER;
    }

    public int deckTotal() {
        return deckB + deckW;
    }

    /** 타일 uid로 (자리, 타일)을 찾는다. */
    public TileView findTile(int uid) {
        for (SeatView s : seats) {
            for (TileView t : s.tiles) if (t.uid == uid) return t;
        }
        if (pending != null && pending.uid == uid) return pending;
        return null;
    }

    public SeatView ownerOf(int uid) {
        for (SeatView s : seats) {
            for (TileView t : s.tiles) if (t.uid == uid) return s;
        }
        return null;
    }

    // ---------------------------------------------------------------- 표기

    public static String code(int uid, char color, int value, boolean revealed) {
        StringBuilder sb = new StringBuilder();
        sb.append(uid).append(color);
        if (value == UNKNOWN) sb.append('?');
        else if (value == JOKER) sb.append('J');
        else sb.append(value);
        if (revealed) sb.append('*');
        return sb.toString();
    }

    public static TileView parseTile(String code) {
        if (code == null || code.isEmpty()) return null;
        TileView t = new TileView();
        int i = 0;
        while (i < code.length() && Character.isDigit(code.charAt(i))) i++;
        t.uid = i > 0 ? Integer.parseInt(code.substring(0, i)) : -1;
        t.color = code.charAt(i++);
        char v = code.charAt(i);
        if (v == '?') {
            t.value = UNKNOWN;
            i++;
        } else if (v == 'J') {
            t.value = JOKER;
            i++;
        } else {
            int j = i;
            while (j < code.length() && Character.isDigit(code.charAt(j))) j++;
            t.value = Integer.parseInt(code.substring(i, j));
            i = j;
        }
        for (; i < code.length(); i++) {
            if (code.charAt(i) == '*') t.revealed = true;
        }
        return t;
    }

    public static GameView parse(Msg m, int viewerPid) {
        GameView v = new GameView();
        v.viewerPid = viewerPid;
        try {
            v.phase = Phase.valueOf(m.s(0));
        } catch (IllegalArgumentException e) {
            v.phase = Phase.OVER;
        }
        v.turnPid = m.i(1, 0);
        v.remainingMs = m.l(2, -1);
        v.deckB = m.i(3, 0);
        v.deckW = m.i(4, 0);
        v.pending = parseTile(m.s(5));
        v.pendingPos = m.i(6, -1);
        v.streak = m.i(7, 0);
        for (String[] r : m.list(8)) {
            SeatView s = new SeatView();
            s.pid = Msg.num(r, 0, 0);
            s.alive = Msg.flag(r, 1);
            s.connected = Msg.flag(r, 2);
            String tiles = Msg.str(r, 3);
            if (!tiles.isEmpty()) {
                for (String c : tiles.split(",")) s.tiles.add(parseTile(c));
            }
            String pick = Msg.str(r, 4);
            s.pick = pick.isEmpty() ? -1 : "?".equals(pick) ? PICK_HIDDEN : Msg.num(r, 4, -1);
            v.seats.add(s);
        }
        v.turnSec = m.i(9, 0);
        v.handSize = m.i(10, 4);
        return v;
    }
}
