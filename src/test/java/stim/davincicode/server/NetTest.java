package stim.davincicode.server;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import stim.davincicode.common.Deduction;
import stim.davincicode.common.GameView;
import stim.davincicode.common.Msg;
import stim.davincicode.common.Proto;

/** 실제 소켓으로 서버에 붙어서 접속 → 로비 채팅 → 방 → 시작 패 고르기 → 대전 → 결과 → 재접속 → 관전 → 기권 → 프로필까지 검사한다. */
public final class NetTest {
    private static int pass, fail;

    static void check(boolean ok, String what) {
        if (ok) pass++;
        else {
            fail++;
            System.out.println("  [실패] " + what);
        }
    }

    /** 테스트용 원시 클라이언트 (화면 없이 프로토콜만 주고받는다) */
    static final class Raw {
        final String name;
        final String device;
        Socket socket;
        DataOutputStream out;
        final LinkedBlockingQueue<Msg> inbox = new LinkedBlockingQueue<>();
        int id;
        boolean picked; // 이번 판에 시작 패를 이미 보냈는가
        final List<String> log = new ArrayList<>();

        Raw(String name, String device) {
            this.name = name;
            this.device = device;
        }

        Raw connect(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            Thread t = new Thread(() -> {
                try {
                    while (true) {
                        String line = in.readUTF();
                        synchronized (log) {
                            log.add(line);
                        }
                        inbox.add(Msg.parse(line));
                    }
                } catch (IOException e) {
                    inbox.add(Msg.parse("__CLOSED"));
                }
            });
            t.setDaemon(true);
            t.start();
            // 실제 앱처럼 10초마다 PING을 보낸다 (안 보내면 서버가 45초 뒤 응답 없는 연결로 보고 끊는다)
            Thread ping = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        Thread.sleep(10_000);
                        send(Proto.PING, System.nanoTime());
                    }
                } catch (Exception e) {
                    // 끝
                }
            });
            ping.setDaemon(true);
            ping.start();
            return this;
        }

        void send(String cmd, Object... f) throws IOException {
            synchronized (this) {
                out.writeUTF(Proto.encode(cmd, f));
                out.flush();
            }
        }

        Msg hello() throws Exception {
            send(Proto.HELLO, Proto.VERSION, device, name, 2);
            Msg m = expect(Proto.WELCOME, Proto.ERROR);
            if (m.cmd.equals(Proto.WELCOME)) id = m.i(0, 0);
            return m;
        }

        /** 원하는 명령어가 올 때까지 다른 메시지는 건너뛴다. */
        Msg expect(String... cmds) throws InterruptedException {
            long end = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < end) {
                Msg m = inbox.poll(200, TimeUnit.MILLISECONDS);
                if (m == null) continue;
                for (String c : cmds) if (m.cmd.equals(c)) return m;
            }
            return Msg.parse("__TIMEOUT");
        }

        /** 조건에 맞는 메시지가 올 때까지 기다린다. */
        Msg expectWhere(String cmd, java.util.function.Predicate<Msg> p) throws InterruptedException {
            long end = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < end) {
                Msg m = inbox.poll(200, TimeUnit.MILLISECONDS);
                if (m != null && m.cmd.equals(cmd) && p.test(m)) return m;
            }
            return Msg.parse("__TIMEOUT");
        }

        void close() throws IOException {
            socket.close();
        }
    }

    public static void main(String[] args) throws Exception {
        GameServer.Config cfg = new GameServer.Config();
        cfg.port = 0;
        cfg.bind = "127.0.0.1";
        cfg.lan = false;
        cfg.seed = 7L;
        cfg.dataDir = java.nio.file.Files.createTempDirectory("dc-test");
        cfg.logger = line -> { if (System.getenv("DC_LOG") != null) System.out.println("    [서버] " + line); };
        GameServer server = new GameServer(cfg);
        int port = server.start();

        System.out.println("- 접속과 닉네임");
        Raw a = new Raw("재윤", "device-aaaa-1111").connect(port);
        check(a.hello().cmd.equals(Proto.WELCOME), "재윤 접속");
        check(a.expect(Proto.LOBBY).cmd.equals(Proto.LOBBY), "접속하면 로비 정보");
        Raw dup = new Raw("재윤", "device-zzzz-9999").connect(port);
        Msg dm = dup.hello();
        check(dm.cmd.equals(Proto.ERROR) && dm.s(0).equals("NAME_TAKEN"), "다른 기기의 같은 닉네임 거절");
        Raw bad = new Raw("a b", "device-bbbb-0000").connect(port);
        check(bad.hello().s(0).equals("BAD_NAME"), "규칙에 안 맞는 닉네임 거절");
        Raw b = new Raw("제윤", "device-bbbb-2222").connect(port);
        check(b.hello().cmd.equals(Proto.WELCOME), "제윤 접속");

        System.out.println("- 로비 채팅 / 귓속말 / 필터");
        a.send(Proto.CHAT, "안녕 시발 반가워");
        Msg chat = b.expect(Proto.CHAT);
        check(chat.s(0).equals("L") && chat.s(2).equals("재윤"), "로비 채팅이 다른 사람에게 감");
        check(chat.s(4).equals("안녕 ●● 반가워"), "욕설이 가려짐: " + chat.s(4));
        a.send(Proto.WHISPER, "제윤", "비밀이야");
        Msg w = b.expect(Proto.WHISPER);
        check(w.s(0).equals("재윤") && w.s(3).equals("비밀이야"), "귓속말 도착");
        for (int i = 0; i < 7; i++) a.send(Proto.CHAT, "도배" + i);
        check(a.expectWhere(Proto.ERROR, m -> m.s(0).equals("CHAT_FLOOD")).cmd.equals(Proto.ERROR), "도배 방지");

        System.out.println("- 방 만들기 / 참가 / 준비");
        a.send(Proto.CREATE, "초보만 오세요", 2, "", 30);
        Msg room = a.expect(Proto.ROOM);
        int roomId = room.i(0, 0);
        check(room.s(1).equals("초보만 오세요") && room.i(2, 0) == a.id && room.i(6, -1) == 30, "방 정보와 방장");
        a.send("ADD_BOT");
        check(a.expectWhere(Proto.ERROR, m -> m.s(0).equals("UNKNOWN")).cmd.equals(Proto.ERROR), "AI 추가 요청은 없는 명령");
        Msg lobby = b.expectWhere(Proto.LOBBY, m -> !m.list(0).isEmpty());
        check(lobby.list(0).size() == 1 && Msg.str(lobby.list(0).get(0), 1).equals("초보만 오세요"), "로비에 방이 보임");
        b.send(Proto.JOIN, roomId, "");
        Msg r2 = b.expect(Proto.ROOM);
        check(r2.list(7).size() == 2, "참가하면 2명");
        a.send(Proto.START);
        check(a.expectWhere(Proto.ERROR, m -> m.s(0).equals("NOT_READY")).cmd.equals(Proto.ERROR), "준비 전 시작 거절");
        b.send(Proto.CHAT, "방 채팅 테스트");
        Msg rc = a.expectWhere(Proto.CHAT, m -> m.s(0).equals("R"));
        check(rc.s(4).equals("방 채팅 테스트"), "방 채팅");

        System.out.println("- 시작 패 고르기");
        b.send(Proto.READY, 1);
        a.expectWhere(Proto.ROOM, m -> m.list(7).size() == 2 && Msg.flag(m.list(7).get(1), 3));
        a.send(Proto.START);
        GameView s0 = GameView.parse(a.expect(Proto.STATE), a.id);
        check(s0.phase == GameView.Phase.SETUP && s0.handSize == 4 && s0.remainingMs > 15_000, "시작하면 시작 패 고르기 (2명은 4개, 제한 시간 있음)");
        check(s0.seat(a.id).tiles.isEmpty() && !s0.seat(a.id).picked(), "고르기 전에는 타일이 없음");
        a.send(Proto.DRAW, "B");
        check(a.expectWhere(Proto.ERROR, m -> m.s(0).equals("BAD_MOVE")).cmd.equals(Proto.ERROR), "고르기 전에 가져오기는 거절");
        a.send(Proto.PICK, 4);
        GameView seenByB = GameView.parse(b.expectWhere(Proto.STATE, m -> GameView.parse(m, b.id).seat(a.id).picked()), b.id);
        check(seenByB.seat(a.id).pick == GameView.PICK_HIDDEN, "상대에게는 '골랐음'만 보이고 개수는 비밀");
        a.send(Proto.PICK, 1);
        check(a.expectWhere(Proto.ERROR, m -> m.s(0).equals("BAD_MOVE")).cmd.equals(Proto.ERROR), "두 번 고르기는 거절");
        b.send(Proto.PICK, 0);
        Msg sa = a.expectWhere(Proto.STATE, m -> !"SETUP".equals(m.s(0)));
        Msg sb = b.expectWhere(Proto.STATE, m -> !"SETUP".equals(m.s(0)));
        GameView va = GameView.parse(sa, a.id);
        GameView vb = GameView.parse(sb, b.id);
        boolean allBlack = true, allWhite = true, hidden = true;
        for (GameView.TileView t : va.seat(a.id).tiles) allBlack &= t.black() && t.known();
        for (GameView.TileView t : vb.seat(b.id).tiles) allWhite &= !t.black() && t.known();
        for (GameView.TileView t : vb.seat(a.id).tiles) if (t.known()) hidden = false;
        check(va.phase == GameView.Phase.DRAW && va.seat(a.id).tiles.size() == 4, "다 고르면 4개씩 나눠 주고 첫 차례");
        check(allBlack, "검정 4개를 고른 사람은 검정 4개를 받음");
        check(allWhite, "흰색 4개를 고른 사람은 흰색 4개를 받음");
        check(hidden, "상대에게는 내 숫자가 안 보임 (색만 보임)");

        System.out.println("- 대전 한 판");
        a.picked = true;
        b.picked = true;
        a.inbox.add(sa);
        b.inbox.add(sb);
        Msg result = playOut(new Raw[] { a, b });
        check(result.cmd.equals(Proto.RESULT), "게임이 끝나고 결과가 옴");
        List<String[]> ranks = result.list(0);
        check(ranks.size() == 2 && Msg.num(ranks.get(0), 3, 0) == 1 && Msg.num(ranks.get(1), 3, 0) == 2, "순위 1위 · 2위");

        System.out.println("- 관전 / 재접속");
        Raw c = new Raw("관전러", "device-cccc-3333").connect(port);
        c.hello();
        a.send(Proto.SETTINGS, "재접속 테스트", 2, "", 0);
        // 규칙을 바꾸면 준비가 풀리므로, 바뀐 규칙(시간 무제한)이 반영된 뒤에 준비한다
        b.expectWhere(Proto.ROOM, m -> m.i(6, -1) == 0);
        b.send(Proto.READY, 1);
        a.expectWhere(Proto.ROOM, m -> m.list(7).size() == 2 && Msg.flag(m.list(7).get(1), 3));
        a.send(Proto.START);
        a.expect(Proto.STATE);
        a.send(Proto.PICK, 2);
        b.send(Proto.PICK, 2);
        a.expectWhere(Proto.STATE, m -> !"SETUP".equals(m.s(0)));
        c.send(Proto.SPECTATE, roomId, "");
        Msg cs = c.expect(Proto.STATE);
        boolean specHidden = true;
        for (GameView.SeatView s : GameView.parse(cs, 0).seats) for (GameView.TileView t : s.tiles) if (t.known() && !t.revealed) specHidden = false;
        check(specHidden, "관전자는 숨은 숫자를 못 봄");
        b.close();
        Msg sys = a.expectWhere(Proto.SYS, m -> m.s(1).contains("연결이 끊겼어요"));
        check(sys.cmd.equals(Proto.SYS), "연결 끊김 알림");
        Msg dcState = a.expectWhere(Proto.STATE, m -> {
            GameView v = GameView.parse(m, a.id);
            return v.seat(b.id) != null && !v.seat(b.id).connected;
        });
        check(dcState.cmd.equals(Proto.STATE), "끊긴 사람 표시");
        Raw b2 = new Raw("제윤", "device-bbbb-2222").connect(port);
        Msg wel = b2.hello();
        check(wel.cmd.equals(Proto.WELCOME) && wel.b(3) && b2.id == b.id, "같은 기기로 재접속하면 이어서 함");
        check(b2.expect(Proto.STATE).cmd.equals(Proto.STATE), "재접속하면 게임 상태를 다시 받음");

        System.out.println("- 게임 중 나가기 (기권)");
        b2.send(Proto.LEAVE);
        Msg res2 = a.expect(Proto.RESULT);
        check(res2.cmd.equals(Proto.RESULT) && Msg.num(res2.list(0).get(0), 0, 0) == a.id, "나간 사람이 지고 남은 사람이 이김");
        check(b2.expect(Proto.LEFT).cmd.equals(Proto.LEFT), "나간 사람은 로비로");

        System.out.println("- 시작 패를 고르는 중에 나가기");
        c.send(Proto.JOIN, roomId, "");
        a.expectWhere(Proto.ROOM, m -> m.list(7).size() == 2);
        c.send(Proto.READY, 1);
        a.expectWhere(Proto.ROOM, m -> m.list(7).size() == 2 && Msg.flag(m.list(7).get(1), 3));
        a.send(Proto.START);
        Msg cSetup = c.expectWhere(Proto.STATE, m -> "SETUP".equals(m.s(0)));
        check(cSetup.cmd.equals(Proto.STATE) && GameView.parse(cSetup, c.id).me() != null, "관전하던 사람도 참가해서 시작 패를 고름");
        a.send(Proto.PICK, 1);
        c.send(Proto.LEAVE);
        Msg res3 = a.expect(Proto.RESULT);
        check(res3.cmd.equals(Proto.RESULT) && Msg.num(res3.list(0).get(0), 0, 0) == a.id, "고르는 중에 나가면 남은 사람이 이김");
        check(c.expect(Proto.LEFT).cmd.equals(Proto.LEFT), "나간 사람은 로비로");

        System.out.println("- 신고 / 없어진 기능");
        a.send(Proto.REPORT, "제윤", "테스트 신고");
        check(a.expect(Proto.NOTICE).s(0).contains("신고"), "신고 접수");
        a.send("RANKING");
        check(a.expectWhere(Proto.ERROR, m -> m.s(0).equals("UNKNOWN")).cmd.equals(Proto.ERROR), "랭킹 요청은 없는 명령");

        System.out.println("- 프로필(닉네임·캐릭터) 바꾸기");
        a.send(Proto.PROFILE, "재윤새이름", 4);
        check(a.expect(Proto.ERROR).s(0).equals("IN_ROOM"), "방 안에서는 프로필을 못 바꿈");
        a.send(Proto.LEAVE);
        a.expect(Proto.LEFT);
        Thread.sleep(2100); // 너무 자주 바꾸지 못하게 2초 간격
        a.send(Proto.PROFILE, "제윤", 4);
        check(a.expect(Proto.ERROR).s(0).equals("NAME_TAKEN"), "다른 사람이 쓰는 닉네임은 거절");
        a.send(Proto.PROFILE, "a b", 4);
        check(a.expect(Proto.ERROR).s(0).equals("BAD_NAME"), "규칙에 안 맞는 닉네임은 거절");
        Thread.sleep(2100);
        a.send(Proto.PROFILE, "재윤새이름", 4);
        Msg pm = a.expect(Proto.PROFILE);
        check(pm.s(0).equals("재윤새이름") && pm.i(1, -1) == 4, "닉네임·캐릭터가 바뀜");
        Msg seen = c.expectWhere(Proto.LOBBY, m -> {
            for (String[] u : m.list(1)) if (Msg.str(u, 1).equals("재윤새이름") && Msg.num(u, 2, -1) == 4) return true;
            return false;
        });
        check(seen.cmd.equals(Proto.LOBBY), "다른 사람 접속자 목록에도 바로 반영");

        System.out.println("- 같은 기기·같은 닉네임으로 또 접속");
        Raw a2 = new Raw("재윤새이름", "device-aaaa-1111").connect(port);
        Msg wel2 = a2.hello();
        check(a.expect(Proto.KICKED).cmd.equals(Proto.KICKED), "먼저 접속한 창은 끊김");
        check(wel2.s(1).equals("재윤새이름"), "바꾼 닉네임으로 다시 접속");

        server.stop();
        System.out.println();
        System.out.println("NetTest: 통과 " + pass + ", 실패 " + fail);
        System.exit(fail > 0 ? 1 : 0);
    }

    /** 각 클라이언트가 자기 차례에 간단한 전략으로 둔다. RESULT가 오면 끝. */
    static Msg playOut(Raw[] players) throws Exception {
        long end = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < end) {
            for (Raw p : players) {
                Msg m = p.inbox.poll(20, TimeUnit.MILLISECONDS);
                if (m == null) continue;
                if (m.cmd.equals(Proto.RESULT)) {
                    for (Raw o : players) if (o != p) o.expect(Proto.RESULT);
                    return m;
                }
                if (!m.cmd.equals(Proto.STATE)) continue;
                GameView v = GameView.parse(m, p.id);
                if (v.phase == GameView.Phase.SETUP) {
                    // 시작 패: 아직 안 보냈으면 검정 2개를 고른다 (모두 동시에 고르므로 차례가 없다)
                    if (!p.picked && v.me() != null && !v.me().picked()) {
                        p.picked = true;
                        p.send(Proto.PICK, 2);
                    }
                    continue;
                }
                p.picked = false;
                if (!v.myTurn()) continue;
                Thread.sleep(30); // 사람보다 훨씬 빠르지만 서버의 도배 차단(초당 40개)에는 걸리지 않는 속도
                move(p, v);
            }
        }
        return Msg.parse("__TIMEOUT");
    }

    static void move(Raw p, GameView v) throws IOException {
        switch (v.phase) {
            case DRAW:
                p.send(Proto.DRAW, v.deckB > 0 ? "B" : "W");
                return;
            case JOKER:
                p.send(Proto.JOKER, 0);
                return;
            case REVEAL_SELF:
                for (GameView.TileView t : v.me().tiles) {
                    if (!t.revealed) {
                        p.send(Proto.REVEAL, t.uid);
                        return;
                    }
                }
                return;
            default:
        }
        Deduction.Result r = Deduction.analyze(v);
        int bestPid = 0, bestUid = 0;
        int[] best = null;
        for (GameView.SeatView s : v.seats) {
            if (s.pid == p.id || !s.alive) continue;
            for (GameView.TileView t : s.tiles) {
                if (t.revealed) continue;
                int[] c = r.of(t.uid);
                if (c != null && (best == null || c.length < best.length)) {
                    best = c;
                    bestPid = s.pid;
                    bestUid = t.uid;
                }
            }
        }
        if (v.phase == GameView.Phase.CONTINUE && (best == null || best.length > 1)) {
            p.send(Proto.STOP);
            return;
        }
        int val = best[0];
        p.send(Proto.GUESS, bestPid, bestUid, val == GameView.JOKER ? "J" : String.valueOf(val));
    }
}
