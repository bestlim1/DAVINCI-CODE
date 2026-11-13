package stim.davincicode.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import stim.davincicode.common.GameView;
import stim.davincicode.common.GameView.Phase;
import stim.davincicode.common.Proto;

/**
 * 게임 한 판의 규칙 엔진. 네트워크와 완전히 분리되어 있어서 단위 테스트와 시뮬레이션에 그대로 쓴다.
 * 모든 행동 메서드는 규칙을 검사하고, 어기면 {@link Illegal}을 던지고, 맞으면 일어난 일(Event) 목록을 돌려준다.
 *
 * 규칙 요약
 *  - 검정·흰색 0~11 타일 24개와 색마다 조커 1개. 2~3명은 4개, 4명은 3개씩 받는다.
 *  - 시작하면 모두 동시에 "검정 몇 개, 흰색 몇 개"를 받을지 고른다 (시간 안에 안 고르면 임의로 정한다).
 *    다 고르면 그 개수대로 각 색 더미에서 무작위로 나눠 준다. 조커는 나눠 준 뒤 더미에 섞는다.
 *  - 내 타일은 작은 숫자가 왼쪽, 같은 숫자면 검정이 왼쪽. 조커는 원하는 자리에 놓는다.
 *  - 차례: 더미에서 색을 골라 1개 가져온다 → 상대의 숨은 타일 하나를 골라 숫자를 말한다.
 *    맞히면 그 타일이 공개되고, 더 맞히거나 멈출 수 있다 (멈추면 가져온 타일을 숨긴 채 넣는다).
 *    틀리면 가져온 타일을 공개한 채 넣고 차례가 끝난다. 더미가 비었으면 틀렸을 때 내 타일 하나를 공개한다.
 *  - 타일이 모두 공개되면 탈락. 마지막까지 남은 사람이 이긴다.
 */
public final class Game {

    public static final class Illegal extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Illegal(String message) {
            super(message);
        }
    }

    /** 클라이언트에게 알릴 일. onlyFor가 0이면 모두에게, 아니면 그 사람에게만 보낸다. */
    public static final class Event {
        public final String type;
        public final int onlyFor;
        public final Object[] fields;

        Event(String type, int onlyFor, Object... fields) {
            this.type = type;
            this.onlyFor = onlyFor;
            this.fields = fields;
        }

        public String encode() {
            Object[] all = new Object[fields.length + 1];
            all[0] = type;
            System.arraycopy(fields, 0, all, 1, fields.length);
            return Proto.encode(Proto.EVENT, all);
        }

        @Override
        public String toString() {
            return Proto.pretty(encode());
        }
    }

    static final class Seat {
        final int pid;
        final List<Tile> hand = new ArrayList<>();
        boolean alive = true;
        boolean connected = true;
        int pick = -1;            // 시작 패로 고른 검정 타일 수 (아직 안 골랐으면 -1)

        Seat(int pid) {
            this.pid = pid;
        }

        int hiddenCount() {
            int n = 0;
            for (Tile t : hand) if (!t.revealed) n++;
            return n;
        }
    }

    final List<Seat> seats = new ArrayList<>();
    private final List<Tile> deckB = new ArrayList<>();
    private final List<Tile> deckW = new ArrayList<>();
    private final Tile jokerB, jokerW;
    final int turnSec;
    final int handSize;
    private final Random rnd;

    Phase phase = Phase.SETUP;
    private int turn;
    Tile pending;
    int pendingPos = -1;
    int streak;
    boolean autoDrawn;           // 시간 초과로 대신 가져온 경우 (추리 시간을 짧게 준다)
    final List<Integer> eliminated = new ArrayList<>();
    int winner;
    int turnCount;

    /** 처음에 받는 타일 수: 2~3명은 4개, 4명은 3개 */
    public static int handSize(int players) {
        return players >= 4 ? 3 : 4;
    }

    public Game(List<Integer> pids, int turnSec, Random rnd) {
        if (pids.size() < 2 || pids.size() > 4) throw new IllegalArgumentException("players: " + pids.size());
        this.turnSec = turnSec;
        this.rnd = rnd;
        this.handSize = handSize(pids.size());

        List<Integer> uids = new ArrayList<>();
        for (int i = 0; i < 26; i++) uids.add(i + 1);
        Collections.shuffle(uids, rnd);

        int u = 0;
        for (int n = 0; n <= 11; n++) {
            deckB.add(new Tile(uids.get(u++), 'B', n));
            deckW.add(new Tile(uids.get(u++), 'W', n));
        }
        jokerB = new Tile(uids.get(u++), 'B', GameView.JOKER);
        jokerW = new Tile(uids.get(u), 'W', GameView.JOKER);
        Collections.shuffle(deckB, rnd);
        Collections.shuffle(deckW, rnd);
        for (int pid : pids) seats.add(new Seat(pid));
        turn = rnd.nextInt(seats.size());
    }

    /** 시작 패 고르기 단계로 들어간다. */
    public List<Event> start() {
        List<Event> ev = new ArrayList<>();
        phase = Phase.SETUP;
        ev.add(new Event("SETUP", 0, handSize));
        return ev;
    }

    // ------------------------------------------------------------------ 조회

    Seat current() {
        return seats.get(turn);
    }

    public int currentPid() {
        return current().pid;
    }

    public Phase phase() {
        return phase;
    }

    Seat seat(int pid) {
        for (Seat s : seats) if (s.pid == pid) return s;
        return null;
    }

    int deckTotal() {
        return deckB.size() + deckW.size();
    }

    int aliveCount() {
        int n = 0;
        for (Seat s : seats) if (s.alive) n++;
        return n;
    }

    public boolean over() {
        return phase == Phase.OVER;
    }

    void setConnected(int pid, boolean connected) {
        Seat s = seat(pid);
        if (s != null) s.connected = connected;
    }

    /** 순위: 우승자 → 늦게 탈락한 순서 */
    public List<Integer> placements() {
        List<Integer> out = new ArrayList<>();
        if (winner != 0) out.add(winner);
        for (int i = eliminated.size() - 1; i >= 0; i--) out.add(eliminated.get(i));
        return out;
    }

    // ------------------------------------------------------------------ 행동

    /** 시작 패로 받을 검정 타일 수를 고른다 (나머지는 흰색). 모두 고르면 바로 나눠 준다. */
    public List<Event> pick(int pid, int black) {
        if (phase != Phase.SETUP) throw new Illegal("시작 패는 게임을 시작할 때만 고를 수 있어요.");
        Seat s = seat(pid);
        if (s == null || !s.alive) throw new Illegal("이 게임에 참가하고 있지 않아요.");
        if (s.pick >= 0) throw new Illegal("이미 시작 패를 골랐어요.");
        if (black < 0 || black > handSize) throw new Illegal("검정 타일 수를 0~" + handSize + " 중에서 골라 주세요.");
        s.pick = black;
        List<Event> ev = new ArrayList<>();
        ev.add(new Event("PICK", 0, pid));
        if (allPicked()) deal(ev);
        return ev;
    }

    public List<Event> draw(int pid, char color) {
        Seat me = requireTurn(pid, Phase.DRAW);
        List<Tile> pile = color == 'B' ? deckB : color == 'W' ? deckW : null;
        if (pile == null) throw new Illegal("가져올 타일 색을 골라 주세요.");
        if (pile.isEmpty()) throw new Illegal((color == 'B' ? "검정" : "흰색") + " 타일이 다 떨어졌어요.");
        List<Event> ev = new ArrayList<>();
        doDraw(me, pile, ev);
        return ev;
    }

    private void doDraw(Seat me, List<Tile> pile, List<Event> ev) {
        Tile t = pile.remove(rnd.nextInt(pile.size()));
        pending = t;
        ev.add(new Event("DRAW", 0, me.pid, t.color, t.uid));
        phase = t.joker() ? Phase.JOKER : Phase.GUESS;
    }

    public List<Event> placeJoker(int pid, int pos) {
        Seat me = requireTurn(pid, Phase.JOKER);
        if (pos < 0 || pos > me.hand.size()) throw new Illegal("조커를 놓을 자리를 다시 골라 주세요.");
        pendingPos = pos;
        phase = Phase.GUESS;
        List<Event> ev = new ArrayList<>();
        ev.add(new Event("JOKERPOS", pid, pid, pos));
        return ev;
    }

    public List<Event> guess(int pid, int targetPid, int uid, int value) {
        Seat me = requireTurn(pid, Phase.GUESS, Phase.CONTINUE);
        Seat target = seat(targetPid);
        if (target == null || target == me) throw new Illegal("상대의 타일을 골라 주세요.");
        if (!target.alive) throw new Illegal("이미 탈락한 사람이에요.");
        Tile tile = find(target, uid);
        if (tile == null) throw new Illegal("그 타일을 찾을 수 없어요.");
        if (tile.revealed) throw new Illegal("이미 공개된 타일이에요.");
        if (value < GameView.JOKER || value > 11) throw new Illegal("0~11 중에서 골라 주세요.");

        List<Event> ev = new ArrayList<>();
        boolean ok = tile.value == value;
        ev.add(new Event("GUESS", 0, pid, targetPid, uid, value == GameView.JOKER ? "J" : String.valueOf(value), Proto.bool(ok)));
        if (ok) {
            tile.revealed = true;
            ev.add(new Event("REVEAL", 0, targetPid, uid));
            streak++;
            if (target.hiddenCount() == 0) eliminate(target, ev);
            if (aliveCount() == 1) {
                finish(ev);
                return ev;
            }
            phase = Phase.CONTINUE;
        } else {
            failTurn(me, ev);
        }
        return ev;
    }

    public List<Event> stop(int pid) {
        Seat me = requireTurn(pid, Phase.CONTINUE);
        List<Event> ev = new ArrayList<>();
        placePending(me, false, ev);
        nextTurn(ev);
        return ev;
    }

    public List<Event> revealSelf(int pid, int uid) {
        Seat me = requireTurn(pid, Phase.REVEAL_SELF);
        Tile t = find(me, uid);
        if (t == null || t.revealed) throw new Illegal("공개할 내 숨은 타일을 골라 주세요.");
        List<Event> ev = new ArrayList<>();
        revealOwn(me, t, ev);
        return ev;
    }

    /** 제한 시간이 지났을 때 서버가 대신 하는 행동 */
    public List<Event> timeout() {
        List<Event> ev = new ArrayList<>();
        if (phase == Phase.OVER) return ev;
        if (phase == Phase.SETUP) {
            // 아직 안 고른 사람은 동전을 던지듯 색을 하나씩 정해 준다
            for (Seat s : seats) {
                if (!s.alive || s.pick >= 0) continue;
                int black = 0;
                for (int i = 0; i < handSize; i++) if (rnd.nextBoolean()) black++;
                s.pick = black;
                ev.add(new Event("TIMEOUT", 0, s.pid, phase.name()));
            }
            deal(ev);
            return ev;
        }
        Seat me = current();
        ev.add(new Event("TIMEOUT", 0, me.pid, phase.name()));
        switch (phase) {
            case DRAW: {
                List<Tile> pile = deckB.isEmpty() ? deckW : deckW.isEmpty() ? deckB : (rnd.nextBoolean() ? deckB : deckW);
                doDraw(me, pile, ev);
                autoDrawn = true;
                if (phase == Phase.JOKER) {
                    pendingPos = me.hand.size();
                    phase = Phase.GUESS;
                }
                break;
            }
            case JOKER:
                pendingPos = me.hand.size();
                phase = Phase.GUESS;
                autoDrawn = true;
                break;
            case GUESS:
                failTurn(me, ev);
                break;
            case CONTINUE:
                placePending(me, false, ev);
                nextTurn(ev);
                break;
            case REVEAL_SELF: {
                List<Tile> hidden = hiddenOf(me);
                revealOwn(me, hidden.get(rnd.nextInt(hidden.size())), ev);
                break;
            }
            default:
                break;
        }
        return ev;
    }

    /** 나가거나 재접속 시간이 지나 기권한 경우: 모든 타일을 공개하고 탈락시킨다. */
    public List<Event> forfeit(int pid) {
        List<Event> ev = new ArrayList<>();
        Seat s = seat(pid);
        if (s == null || !s.alive || phase == Phase.OVER) return ev;
        ev.add(new Event("FORFEIT", 0, pid));
        if (phase == Phase.SETUP) {
            // 아직 패를 받기 전이면 자리만 빼고, 남은 사람이 다 골랐으면 나눠 준다
            eliminate(s, ev);
            if (aliveCount() == 1) finish(ev);
            else if (allPicked()) deal(ev);
            return ev;
        }
        boolean wasTurn = current() == s;
        if (wasTurn) placePending(s, true, ev);
        for (Tile t : s.hand) t.revealed = true;
        eliminate(s, ev);
        if (aliveCount() == 1) {
            finish(ev);
            return ev;
        }
        if (wasTurn) nextTurn(ev);
        return ev;
    }

    // ------------------------------------------------------------------ 내부

    private Seat requireTurn(int pid, Phase... allowed) {
        if (phase == Phase.OVER) throw new Illegal("게임이 끝났어요.");
        if (phase == Phase.SETUP) throw new Illegal("먼저 시작 패의 색을 골라 주세요.");
        Seat me = current();
        if (me.pid != pid) throw new Illegal("내 차례가 아니에요.");
        for (Phase p : allowed) if (p == phase) return me;
        switch (phase) {
            case DRAW: throw new Illegal("먼저 타일을 가져와 주세요.");
            case JOKER: throw new Illegal("조커를 놓을 자리를 먼저 골라 주세요.");
            case REVEAL_SELF: throw new Illegal("공개할 내 타일을 먼저 골라 주세요.");
            case CONTINUE: throw new Illegal("더 맞히거나 차례를 마쳐 주세요.");
            default: throw new Illegal("지금은 할 수 없는 행동이에요.");
        }
    }

    private boolean allPicked() {
        for (Seat s : seats) if (s.alive && s.pick < 0) return false;
        return true;
    }

    /** 고른 개수대로 각 색 더미에서 무작위로 나눠 주고, 조커를 더미에 섞은 뒤 첫 차례를 시작한다. */
    private void deal(List<Event> ev) {
        for (Seat s : seats) {
            if (!s.alive) continue;
            for (int i = 0; i < handSize; i++) {
                // 인원 × 개수가 12를 넘지 않아서 한 색이 모자랄 일은 없지만, 혹시 몰라 다른 색으로 채운다
                boolean wantBlack = i < s.pick;
                List<Tile> pile = (wantBlack && !deckB.isEmpty()) || deckW.isEmpty() ? deckB : deckW;
                s.hand.add(pile.remove(pile.size() - 1));
            }
            s.hand.sort((a, b) -> a.key() - b.key());
        }
        deckB.add(jokerB);
        deckW.add(jokerW);
        Collections.shuffle(deckB, rnd);
        Collections.shuffle(deckW, rnd);
        while (!seats.get(turn).alive) turn = (turn + 1) % seats.size();
        ev.add(new Event("DEAL", 0));
        beginTurn(ev);
    }

    private static Tile find(Seat s, int uid) {
        for (Tile t : s.hand) if (t.uid == uid) return t;
        return null;
    }

    private static List<Tile> hiddenOf(Seat s) {
        List<Tile> out = new ArrayList<>();
        for (Tile t : s.hand) if (!t.revealed) out.add(t);
        return out;
    }

    private void beginTurn(List<Event> ev) {
        pending = null;
        pendingPos = -1;
        streak = 0;
        autoDrawn = false;
        turnCount++;
        phase = deckTotal() > 0 ? Phase.DRAW : Phase.GUESS;
        ev.add(new Event("TURN", 0, current().pid, deckTotal()));
    }

    private void failTurn(Seat me, List<Event> ev) {
        if (pending != null) {
            placePending(me, true, ev);
            nextTurn(ev);
            return;
        }
        // 더미가 비어 있으면 내 숨은 타일 하나를 공개해야 한다
        List<Tile> hidden = hiddenOf(me);
        if (hidden.size() == 1) revealOwn(me, hidden.get(0), ev);
        else phase = Phase.REVEAL_SELF;
    }

    private void revealOwn(Seat me, Tile t, List<Event> ev) {
        t.revealed = true;
        ev.add(new Event("REVEAL", 0, me.pid, t.uid));
        if (me.hiddenCount() == 0) eliminate(me, ev);
        if (aliveCount() == 1) {
            finish(ev);
            return;
        }
        nextTurn(ev);
    }

    private void placePending(Seat me, boolean revealed, List<Event> ev) {
        if (pending == null) return;
        Tile t = pending;
        pending = null;
        t.revealed = revealed;
        int at;
        if (t.joker()) {
            at = pendingPos < 0 ? me.hand.size() : Math.min(pendingPos, me.hand.size());
        } else {
            at = insertIndex(me.hand, t);
        }
        me.hand.add(at, t);
        pendingPos = -1;
        ev.add(new Event("PLACE", 0, me.pid, t.uid, Proto.bool(revealed)));
    }

    /** 숫자 타일이 들어갈 자리: 자기보다 작은 숫자 타일 중 가장 오른쪽 것의 바로 뒤 */
    static int insertIndex(List<Tile> hand, Tile t) {
        int idx = 0;
        for (int i = 0; i < hand.size(); i++) {
            Tile h = hand.get(i);
            if (!h.joker() && h.key() < t.key()) idx = i + 1;
        }
        return idx;
    }

    private void eliminate(Seat s, List<Event> ev) {
        if (!s.alive) return;
        s.alive = false;
        eliminated.add(s.pid);
        ev.add(new Event("ELIM", 0, s.pid));
    }

    private void nextTurn(List<Event> ev) {
        int n = seats.size();
        for (int i = 1; i <= n; i++) {
            int j = (turn + i) % n;
            if (seats.get(j).alive) {
                turn = j;
                break;
            }
        }
        beginTurn(ev);
    }

    private void finish(List<Event> ev) {
        Seat cur = current();
        if (pending != null) placePending(cur, false, ev);
        for (Seat s : seats) {
            if (s.alive) winner = s.pid;
        }
        phase = Phase.OVER;
        ev.add(new Event("OVER", 0, winner));
    }

    // ------------------------------------------------------------------ 상태 전송

    /**
     * viewer(0이면 관전자)가 볼 수 있는 정보만 담은 STATE 메시지. 게임이 끝나면 모든 타일을 공개한다.
     * 시작 패를 고르는 동안에는 차례가 없고(0), 다른 사람이 고른 개수는 "?"(골랐음)로만 보인다.
     */
    public String snapshot(int viewer, long remainingMs) {
        boolean over = phase == Phase.OVER;
        boolean setup = phase == Phase.SETUP;
        Seat cur = current();
        String pendingCode = pending == null ? "" : pending.codeFor(viewer, cur.pid, over);
        int pos = (pending != null && viewer == cur.pid) ? pendingPos : -1;
        List<String> recs = new ArrayList<>();
        for (Seat s : seats) {
            StringBuilder tiles = new StringBuilder();
            for (Tile t : s.hand) {
                if (tiles.length() > 0) tiles.append(',');
                tiles.append(t.codeFor(viewer, s.pid, over));
            }
            String pick = s.pick < 0 ? "" : (!setup || viewer == s.pid) ? String.valueOf(s.pick) : "?";
            recs.add(Proto.record(s.pid, Proto.bool(s.alive), Proto.bool(s.connected), tiles, pick));
        }
        return Proto.encode(Proto.STATE, phase.name(), setup ? 0 : cur.pid, remainingMs, deckB.size(), deckW.size(),
                pendingCode, pos, streak, Proto.list(recs), turnSec, handSize);
    }
}
