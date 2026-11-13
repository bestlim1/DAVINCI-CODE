package stim.davincicode.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import stim.davincicode.common.ChatFilter;
import stim.davincicode.common.Deduction;
import stim.davincicode.common.GameView;
import stim.davincicode.common.GameView.Phase;
import stim.davincicode.common.Msg;
import stim.davincicode.common.Names;
import stim.davincicode.common.Proto;

/** 네트워크 없이 규칙 엔진·시작 패 고르기·정보 은닉·숫자 계산·필터를 검사한다. 외부 라이브러리 없이 main으로 실행. */
public final class EngineTest {
    private static int pass, fail;

    static void check(boolean ok, String what) {
        if (ok) pass++;
        else {
            fail++;
            System.out.println("  [실패] " + what);
        }
    }

    public static void main(String[] args) {
        insertOrder();
        setupPhase();
        infoHiding();
        guessFlow();
        emptyDeck();
        forfeitFlow();
        illegalMoves();
        filters();
        simulate(3000);
        System.out.println();
        System.out.println("EngineTest: 통과 " + pass + ", 실패 " + fail);
        if (fail > 0) System.exit(1);
    }

    static Tile t(char c, int v) {
        return new Tile(0, c, v);
    }

    static void insertOrder() {
        System.out.println("- 정렬·삽입 규칙");
        List<Tile> hand = new ArrayList<>(Arrays.asList(t('B', 2), t('W', 5), t('B', 9)));
        check(Game.insertIndex(hand, t('W', 2)) == 1, "W2는 B2 바로 뒤");
        check(Game.insertIndex(hand, t('B', 5)) == 1, "B5는 W5 앞");
        check(Game.insertIndex(hand, t('B', 0)) == 0, "B0은 맨 앞");
        check(Game.insertIndex(hand, t('W', 11)) == 3, "W11은 맨 뒤");
        List<Tile> withJoker = new ArrayList<>(Arrays.asList(t('B', 2), t('B', GameView.JOKER), t('W', 5)));
        check(Game.insertIndex(withJoker, t('B', 3)) == 1, "조커 앞 B2 바로 뒤에 B3");
        check(Game.insertIndex(withJoker, t('B', 6)) == 3, "W5 뒤에 B6");
    }

    /** 시작 패까지 모두 골라서 첫 차례가 시작된 게임 */
    static Game newGame(int players, long seed) {
        Game g = rawGame(players, seed);
        Random r = new Random(seed * 31 + 7);
        for (Game.Seat s : g.seats) g.pick(s.pid, r.nextInt(g.handSize + 1));
        return g;
    }

    /** 시작 패를 고르기 전 (SETUP 단계) */
    static Game rawGame(int players, long seed) {
        List<Integer> pids = new ArrayList<>();
        for (int i = 1; i <= players; i++) pids.add(i * 10);
        Game g = new Game(pids, 60, new Random(seed));
        g.start();
        return g;
    }

    static int blacks(Game.Seat s) {
        int n = 0;
        for (Tile x : s.hand) if (x.color == 'B') n++;
        return n;
    }

    static boolean hasJoker(Game.Seat s) {
        for (Tile x : s.hand) if (x.joker()) return true;
        return false;
    }

    static void setupPhase() {
        System.out.println("- 시작 패 고르기");
        Game g = rawGame(3, 11);
        check(g.phase() == Phase.SETUP, "시작하면 시작 패 고르기 단계");
        check(g.handSize == 4, "3인 게임은 4개씩 고른다");
        for (Game.Seat s : g.seats) check(s.hand.isEmpty(), "고르기 전에는 타일이 없음");
        int a = g.seats.get(0).pid, b = g.seats.get(1).pid, c = g.seats.get(2).pid;
        GameView v0 = view(g, a);
        check(v0.phase == Phase.SETUP && v0.turnPid == 0 && v0.handSize == 4, "STATE: SETUP, 차례 없음, 4개");
        check(v0.deckB == 12 && v0.deckW == 12, "고르는 동안 더미는 검정 12 · 흰색 12 (조커는 아직 빠져 있음)");
        check(throwsIllegal(() -> g.draw(g.currentPid(), 'B')), "고르기 전에 가져오기 막음");
        check(throwsIllegal(() -> g.pick(a, 5)), "범위 밖 개수 막음");
        check(throwsIllegal(() -> g.pick(a, -1)), "음수 개수 막음");
        check(throwsIllegal(() -> g.pick(999, 2)), "참가하지 않은 사람 막음");
        List<Game.Event> ev = g.pick(a, 4);
        check(ev.size() == 1 && ev.get(0).type.equals("PICK"), "고르면 PICK 이벤트 (아직 나눠 주지 않음)");
        check(throwsIllegal(() -> g.pick(a, 1)), "두 번 고르기 막음");
        GameView mine = view(g, a), theirs = view(g, b), spec = view(g, 0);
        check(mine.seat(a).pick == 4, "내가 고른 개수는 나에게 보임");
        check(theirs.seat(a).pick == GameView.PICK_HIDDEN && theirs.seat(a).picked(), "남이 고른 개수는 '골랐음'으로만 보임");
        check(spec.seat(a).pick == GameView.PICK_HIDDEN, "관전자에게도 개수는 비밀");
        check(!theirs.seat(c).picked(), "안 고른 사람은 고르는 중으로 보임");
        g.pick(b, 0);
        ev = g.pick(c, 2);
        boolean dealt = false;
        for (Game.Event e : ev) if (e.type.equals("DEAL")) dealt = true;
        check(dealt && g.phase() == Phase.DRAW, "모두 고르면 나눠 주고 첫 차례 시작");
        check(blacks(g.seat(a)) == 4 && blacks(g.seat(b)) == 0 && blacks(g.seat(c)) == 2, "고른 개수대로 검정·흰색을 받음");
        for (Game.Seat s : g.seats) {
            check(s.hand.size() == 4, "모두 4개씩");
            check(!hasJoker(s), "시작 패에는 조커가 없음");
        }
        GameView after = view(g, b);
        check(after.deckB == 12 - 6 + 1 && after.deckW == 12 - 6 + 1, "나눠 준 뒤 더미에 조커가 섞임");
        check(after.seat(a).pick == 4, "나눠 준 뒤에는 고른 개수가 공개됨");

        // 시간 초과: 안 고른 사람만 임의로 정해 준다
        Game t = rawGame(2, 12);
        int p1 = t.seats.get(0).pid;
        t.pick(p1, 3);
        List<Game.Event> te = t.timeout();
        int timeouts = 0;
        for (Game.Event e : te) if (e.type.equals("TIMEOUT")) timeouts++;
        check(timeouts == 1, "시간 초과는 안 고른 사람만");
        check(t.phase() == Phase.DRAW && blacks(t.seat(p1)) == 3, "시간이 지나면 나눠 주고 시작 (고른 사람 것은 그대로)");

        // 고르는 중에 나가면
        Game f = rawGame(3, 13);
        int f1 = f.seats.get(0).pid, f2 = f.seats.get(1).pid, f3 = f.seats.get(2).pid;
        f.pick(f1, 1);
        f.forfeit(f2);
        check(f.phase() == Phase.SETUP && !f.seat(f2).alive, "고르는 중에 나가면 그 자리만 빠짐");
        f.pick(f3, 2);
        check(f.phase() == Phase.DRAW, "남은 사람이 다 고르면 시작");
        check(f.seat(f2).hand.isEmpty() && f.currentPid() != f2, "나간 사람은 타일도 차례도 없음");
        Game f0 = rawGame(2, 14);
        f0.forfeit(f0.seats.get(0).pid);
        check(f0.over() && f0.winner == f0.seats.get(1).pid, "2명 중 1명이 고르는 중에 나가면 바로 끝");

        // 극단적인 조합: 4명 모두 검정 3개, 3명 모두 흰색 4개
        Game x4 = rawGame(4, 15);
        check(x4.handSize == 3, "4인 게임은 3개씩 고른다");
        for (Game.Seat s : x4.seats) x4.pick(s.pid, 3);
        boolean allBlack = true;
        for (Game.Seat s : x4.seats) allBlack &= blacks(s) == 3;
        GameView xv = view(x4, 0);
        check(allBlack && xv.deckB == 1 && xv.deckW == 13, "4명 모두 검정 3개를 골라도 나눠 줄 수 있음");
        Game x3 = rawGame(3, 16);
        for (Game.Seat s : x3.seats) x3.pick(s.pid, 0);
        GameView yv = view(x3, 0);
        check(yv.deckW == 1 && yv.deckB == 13, "3명 모두 흰색 4개를 골라도 나눠 줄 수 있음");
    }

    static GameView view(Game g, int viewer) {
        return GameView.parse(Msg.parse(g.snapshot(viewer, -1)), viewer);
    }

    static void infoHiding() {
        System.out.println("- 정보 은닉");
        Game g = newGame(3, 1);
        for (Game.Seat s : g.seats) {
            check(s.hand.size() == 4, "3인 게임은 4장씩");
        }
        int viewer = g.seats.get(0).pid;
        GameView v = view(g, viewer);
        for (GameView.SeatView s : v.seats) {
            for (GameView.TileView tv : s.tiles) {
                if (s.pid == viewer) check(tv.known(), "내 타일은 숫자가 보임");
                else check(!tv.known(), "상대 숨은 타일은 숫자가 안 보임");
            }
        }
        GameView spec = view(g, 0);
        for (GameView.SeatView s : spec.seats) for (GameView.TileView tv : s.tiles) check(!tv.known(), "관전자는 숨은 타일을 못 봄");
        Game four = newGame(4, 2);
        check(four.seats.get(0).hand.size() == 3, "4인 게임은 3장씩");
        // 가져온 타일은 본인만 숫자를 본다
        int cur = g.currentPid();
        g.draw(cur, 'B');
        int other = g.seats.get(0).pid == cur ? g.seats.get(1).pid : g.seats.get(0).pid;
        GameView mine = view(g, cur);
        GameView theirs = view(g, other);
        check(mine.pending != null && mine.pending.known(), "가져온 타일은 본인에게 보임");
        check(theirs.pending != null && !theirs.pending.known(), "가져온 타일은 남에게 안 보임");
    }

    /** 정답 숫자를 서버 객체에서 바로 꺼내 맞히기 테스트 */
    static Tile firstHidden(Game.Seat s) {
        for (Tile x : s.hand) if (!x.revealed) return x;
        return null;
    }

    static Game.Seat opponent(Game g) {
        for (Game.Seat s : g.seats) if (s.pid != g.currentPid() && s.alive) return s;
        return null;
    }

    static void guessFlow() {
        System.out.println("- 맞히기 / 틀리기 흐름");
        Game g = newGame(2, 3);
        int me = g.currentPid();
        g.draw(me, 'W');
        check(g.phase() == Phase.GUESS, "가져오면 추리 단계");
        Game.Seat op = opponent(g);
        Tile target = firstHidden(op);
        List<Game.Event> ev = g.guess(me, op.pid, target.uid, target.value);
        check(target.revealed, "맞히면 공개");
        check(g.phase() == Phase.CONTINUE, "맞히면 계속 단계");
        check(ev.get(0).type.equals("GUESS") && ev.get(0).fields[4].equals("1"), "GUESS 이벤트 정답 표시");
        int handBefore = g.seat(me).hand.size();
        Tile pending = g.pending;
        g.stop(me);
        check(g.seat(me).hand.size() == handBefore + 1, "멈추면 가져온 타일이 들어감");
        check(!pending.revealed, "멈추면 숨긴 채로 들어감");
        check(g.currentPid() != me, "멈추면 차례가 넘어감");

        int me2 = g.currentPid();
        g.draw(me2, 'B');
        Tile p2 = g.pending;
        Game.Seat op2 = opponent(g);
        Tile tgt = firstHidden(op2);
        int wrong = (tgt.value + 1) % 12;
        g.guess(me2, op2.pid, tgt.uid, wrong);
        check(p2.revealed, "틀리면 가져온 타일 공개");
        check(!tgt.revealed, "틀리면 상대 타일은 그대로");
        check(g.currentPid() != me2, "틀리면 차례가 넘어감");
    }

    static void emptyDeck() {
        System.out.println("- 더미가 빈 경우");
        Game g = newGame(2, 4);
        // 더미를 모두 비운다 (차례마다 가져오고 일부러 틀린다)
        int guard = 0;
        while (g.deckTotal() > 0 && guard++ < 100 && !g.over()) {
            int me = g.currentPid();
            g.draw(me, g.deckTotal() > 0 && view(g, me).deckB > 0 ? 'B' : 'W');
            if (g.phase() == Phase.JOKER) g.placeJoker(me, 0);
            Game.Seat op = opponent(g);
            Tile tg = firstHidden(op);
            g.guess(me, op.pid, tg.uid, (tg.value + 1) % 12);
        }
        if (g.over()) {
            check(true, "게임이 먼저 끝남");
            return;
        }
        check(g.phase() == Phase.GUESS, "더미가 비면 가져오기 없이 추리");
        int me = g.currentPid();
        Game.Seat op = opponent(g);
        Tile tg = firstHidden(op);
        int hiddenBefore = g.seat(me).hiddenCount();
        g.guess(me, op.pid, tg.uid, (tg.value + 1) % 12);
        if (hiddenBefore > 1) {
            check(g.phase() == Phase.REVEAL_SELF, "틀리면 내 타일 공개 단계");
            Tile own = firstHidden(g.seat(me));
            g.revealSelf(me, own.uid);
            check(own.revealed, "고른 내 타일 공개");
        }
        check(g.seat(me).hiddenCount() == hiddenBefore - 1, "숨은 타일이 하나 줄어듦");
    }

    static void forfeitFlow() {
        System.out.println("- 기권");
        Game g = newGame(3, 5);
        int me = g.currentPid();
        g.draw(me, 'B');
        g.forfeit(me);
        check(!g.seat(me).alive, "기권하면 탈락");
        check(g.seat(me).hiddenCount() == 0, "기권하면 모두 공개");
        check(g.currentPid() != me && !g.over(), "다음 사람 차례");
        int next = g.currentPid();
        g.forfeit(next);
        check(g.over(), "한 명 남으면 끝");
        check(g.placements().size() == 3 && g.placements().get(2) == me, "먼저 나간 사람이 꼴찌");
    }

    static void illegalMoves() {
        System.out.println("- 규칙 위반 막기");
        Game g = newGame(2, 6);
        int me = g.currentPid();
        int op = opponent(g).pid;
        check(throwsIllegal(() -> g.draw(op, 'B')), "남의 차례에 가져오기");
        check(throwsIllegal(() -> g.guess(me, op, firstHidden(g.seat(op)).uid, 3)), "가져오기 전에 추리");
        g.draw(me, 'B');
        check(throwsIllegal(() -> g.guess(me, me, firstHidden(g.seat(me)).uid, 3)), "내 타일 추리");
        check(throwsIllegal(() -> g.guess(me, op, 9999, 3)), "없는 타일");
        check(throwsIllegal(() -> g.guess(me, op, firstHidden(g.seat(op)).uid, 12)), "범위 밖 숫자");
        check(throwsIllegal(() -> g.stop(me)), "한 번도 안 맞히고 멈추기");
    }

    static boolean throwsIllegal(Runnable r) {
        try {
            r.run();
            return false;
        } catch (Game.Illegal e) {
            return true;
        }
    }

    static void filters() {
        System.out.println("- 채팅 필터 / 닉네임 / 프로토콜");
        check(ChatFilter.filter("이 시발 뭐야").equals("이 ●● 뭐야"), "욕설 가림");
        check(ChatFilter.filter("씨 1 발").contains("●"), "사이에 끼운 글자도 가림");
        check(ChatFilter.filter("안녕하세요 반가워요").equals("안녕하세요 반가워요"), "일반 문장은 그대로");
        check(Names.checkName("재윤") == null, "정상 닉네임");
        check(Names.checkName("a") != null, "너무 짧은 닉네임");
        check(Names.checkName("공 백") != null, "공백 닉네임");
        check(Names.checkName("AI루나") != null, "AI 사칭 닉네임");
        check(Proto.clean("a\tb\u001Ec\nd", 50).equals("a b c d"), "구분자 제거");
        Msg m = Msg.parse(Proto.encode("CHAT", "안녕 하세요", 3));
        check(m.cmd.equals("CHAT") && m.s(0).equals("안녕 하세요") && m.i(1, 0) == 3, "인코딩/파싱");
    }

    /**
     * 자동 플레이어끼리 수천 판을 돌려 예외·무한 루프·규칙 위반이 없는지 확인한다.
     * 숫자 고르기 창이 정답을 지워 버리는 일이 절대 없는지(판 위에 보이는 숫자만 지운다)도 함께 확인한다.
     */
    static void simulate(int games) {
        System.out.println("- 자동 대전 " + games + "판");
        Random rnd = new Random(42);
        int deductionChecks = 0, deductionMiss = 0, jokerMiss = 0, pickerChecks = 0, pickerMiss = 0;
        int[] wins = new int[3];
        long totalTurns = 0;
        for (int gi = 0; gi < games; gi++) {
            int n = 2 + gi % 3;
            List<Integer> pids = new ArrayList<>();
            int[] levels = new int[n];
            for (int i = 0; i < n; i++) {
                pids.add(i + 1);
                levels[i] = rnd.nextInt(3);
            }
            Game g = new Game(pids, 60, new Random(rnd.nextLong()));
            g.start();
            checkInvariants(g);
            int steps = 0;
            while (!g.over() && steps < 2000) {
                steps++;
                int cur = g.currentPid();
                // 보이는 정보만으로 계산한 후보에 실제 숫자가 들어 있어야 한다.
                // 손에 조커가 하나도 없을 때는 정확해야 하고, 조커가 섞이면 근사치라 따로 센다
                if (steps % 3 == 0 && g.phase() != Phase.SETUP) {
                    GameView v = view(g, cur);
                    Deduction.Result r = Deduction.analyze(v);
                    boolean jokerInHand = g.pending != null && g.pending.joker();
                    for (Game.Seat s : g.seats) jokerInHand |= hasJoker(s);
                    for (Game.Seat s : g.seats) {
                        if (s.pid == cur) continue;
                        for (Tile tile : s.hand) {
                            if (tile.revealed) continue;
                            pickerChecks++;
                            if (!r.unknown(tile.color, tile.value)) pickerMiss++;
                            int[] c = r.of(tile.uid);
                            deductionChecks++;
                            boolean found = false;
                            if (c != null) for (int x : c) if (x == tile.value) found = true;
                            if (!found) {
                                if (jokerInHand) jokerMiss++;
                                else deductionMiss++;
                            }
                        }
                    }
                }
                if (rnd.nextInt(60) == 0) {
                    g.timeout();
                } else {
                    SimPlayer.act(g, cur, levels[cur - 1], rnd);
                }
                checkInvariants(g);
            }
            check(g.over(), "게임이 끝남 (판 " + gi + ")");
            if (!g.over()) break;
            check(g.seat(g.winner).alive, "우승자는 살아 있음");
            check(g.placements().size() == n, "순위 인원");
            wins[levels[g.winner - 1]]++;
            totalTurns += g.turnCount;
        }
        check(pickerMiss == 0, "숫자 고르기 창이 정답을 지움: " + pickerMiss + "/" + pickerChecks);
        check(deductionMiss == 0, "조커가 없는 장면에서 계산 후보가 정답을 빠뜨림: " + deductionMiss + "/" + deductionChecks);
        System.out.println("  숫자 고르기 창 검사 " + pickerChecks + "회, 후보 계산 검사 " + deductionChecks + "회 (조커가 섞인 장면 근사 오차 " + jokerMiss + "회)");
        System.out.println("  실력별 우승 (대충/보통/신중): " + Arrays.toString(wins) + ", 평균 " + (totalTurns / games) + "턴");
    }

    static void checkInvariants(Game g) {
        int total = g.deckTotal() + (g.pending != null ? 1 : 0);
        for (Game.Seat s : g.seats) {
            total += s.hand.size();
            int prev = -1;
            for (Tile x : s.hand) {
                if (x.joker()) continue;
                if (x.key() <= prev) {
                    check(false, "손패 정렬이 깨짐: " + s.hand);
                    return;
                }
                prev = x.key();
            }
            if (s.alive && s.hiddenCount() == 0 && !g.over() && g.phase() != Phase.SETUP) check(false, "숨은 타일이 없는데 살아 있음");
        }
        // 시작 패를 고르는 동안에는 조커 2개가 아직 더미 밖에 있다
        int expected = g.phase() == Phase.SETUP ? 24 : 26;
        if (total != expected) check(false, "타일 개수가 맞지 않음: " + total + " (기대 " + expected + ")");
    }
}
