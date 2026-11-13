package stim.davincicode.server;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import stim.davincicode.common.Deduction;
import stim.davincicode.common.GameView;
import stim.davincicode.common.Msg;
import stim.davincicode.common.Proto;

/** 웹 입구(Gate) 검사: 웹 파일 전송, 접근 제한, WebSocket 프레임, 앱(TCP)과 웹이 같이 노는지, 웹과 앱이 한 방에서 한 판 끝까지 */
public final class WebTest {
    private static int pass, fail;

    static void check(boolean ok, String what) {
        if (ok) pass++;
        else {
            fail++;
            System.out.println("  [실패] " + what);
        }
    }

    /** 간단한 HTTP 요청 → 응답 전체(머리+본문) */
    static String http(int port, String request) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            s.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String get(int port, String path) throws IOException {
        return http(port, "GET " + path + " HTTP/1.1\r\nHost: test\r\n\r\n");
    }

    /** 앱(TCP)과 웹(WebSocket) 어느 쪽이든 명령을 보내는 방법 */
    interface Sender {
        void send(String cmd, Object... f) throws IOException;
    }

    /** 테스트용 WebSocket 클라이언트 (브라우저처럼 보내는 프레임은 마스크한다) */
    static final class Ws implements Sender {
        final Socket s;
        final InputStream in;
        final OutputStream out;
        final Random rnd = new Random(1);
        int id;
        String handshake;

        Ws(int port) throws Exception {
            s = new Socket("127.0.0.1", port);
            s.setSoTimeout(8000);
            out = s.getOutputStream();
            in = new BufferedInputStream(s.getInputStream());
            byte[] k = new byte[16];
            rnd.nextBytes(k);
            String key = Base64.getEncoder().encodeToString(k);
            out.write(("GET /ws HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: keep-alive, Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\nOrigin: http://127.0.0.1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            while (!head.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) head.write(in.read());
            handshake = head.toString(StandardCharsets.ISO_8859_1);
            String expect = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            check(handshake.startsWith("HTTP/1.1 101") && handshake.contains("Sec-WebSocket-Accept: " + expect), "WebSocket 연결 수락 키");
        }

        void frame(int opcode, byte[] data, boolean fin, boolean mask) throws IOException {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            b.write((fin ? 0x80 : 0) | opcode);
            int n = data.length;
            int m = mask ? 0x80 : 0;
            if (n < 126) b.write(m | n);
            else if (n < 65536) {
                b.write(m | 126);
                b.write(n >>> 8);
                b.write(n);
            } else {
                b.write(m | 127);
                for (int i = 7; i >= 0; i--) b.write((int) ((long) n >>> (8 * i)));
            }
            byte[] key = new byte[4];
            rnd.nextBytes(key);
            if (mask) b.write(key);
            for (int i = 0; i < n; i++) b.write(mask ? data[i] ^ key[i & 3] : data[i]);
            out.write(b.toByteArray());
            out.flush();
        }

        @Override
        public void send(String cmd, Object... f) throws IOException {
            frame(0x1, Proto.encode(cmd, f).getBytes(StandardCharsets.UTF_8), true, true);
        }

        /** 서버가 보낸 다음 프레임: {opcode, 내용} */
        Object[] next() throws IOException {
            int b0 = in.read(), b1 = in.read();
            if (b0 < 0 || b1 < 0) return null;
            long len = b1 & 0x7F;
            if ((b1 & 0x80) != 0) throw new IOException("서버 프레임은 마스크되면 안 됨");
            if (len == 126) len = (in.read() << 8) | in.read();
            else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
            }
            byte[] d = new DataInputStream(in).readNBytes((int) len);
            return new Object[] { b0 & 0x0F, new String(d, StandardCharsets.UTF_8) };
        }

        Msg expect(String cmd) throws IOException {
            long end = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < end) {
                Object[] f = next();
                if (f == null) return Msg.parse("__CLOSED");
                if ((int) f[0] != 1) continue;
                Msg m = Msg.parse((String) f[1]);
                if (m.cmd.equals(cmd)) return m;
            }
            return Msg.parse("__TIMEOUT");
        }
    }

    public static void main(String[] args) throws Exception {
        GameServer.Config cfg = new GameServer.Config();
        cfg.port = 0;
        cfg.bind = "127.0.0.1";
        cfg.lan = false;
        cfg.seed = 3L;
        cfg.name = "웹 테스트 서버";
        cfg.logger = l -> { };
        GameServer server = new GameServer(cfg);
        int port = server.start();

        System.out.println("- 웹 파일 보내기와 접근 제한");
        String index = get(port, "/");
        check(index.startsWith("HTTP/1.1 200") && index.contains("<title>DAVINCI CODE</title>"), "주소만 넣으면 웹 게임 화면");
        check(index.contains("Content-Security-Policy:"), "보안 헤더(CSP)");
        String js = get(port, "/app.js");
        check(js.startsWith("HTTP/1.1 200") && js.contains("text/javascript") && js.contains("WebSocket"), "app.js");
        check(get(port, "/proto.js").contains("export function analyze"), "proto.js");
        check(get(port, "/style.css").contains("text/css"), "style.css");
        check(get(port, "/icon-192.png").contains("image/png"), "아이콘");
        check(get(port, "/manifest.webmanifest").contains("\"DAVINCI CODE\""), "웹 앱 manifest");
        check(get(port, "/../../stim/davincicode/server/Hub.class").startsWith("HTTP/1.1 404"), "폴더 밖 파일 접근 차단");
        check(get(port, "/web/app.js").startsWith("HTTP/1.1 404"), "목록에 없는 경로 차단");
        check(get(port, "/health").contains("\"ok\":true"), "상태 확인 /health");
        check(http(port, "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\n\r\n").startsWith("HTTP/1.1 405"), "GET이 아닌 요청 거절");
        check(get(port, "/ws").startsWith("HTTP/1.1 400"), "업그레이드 없는 /ws 거절");
        check(http(port, "HEAD / HTTP/1.1\r\nHost: x\r\n\r\n").split("\r\n\r\n", -1)[1].isEmpty(), "HEAD는 본문 없이");

        System.out.println("- WebSocket 접속과 앱·웹 같이 채팅");
        Ws web = new Ws(port);
        web.send(Proto.HELLO, Proto.VERSION, "web-test-device-1", "웹유저", 3);
        Msg wel = web.expect(Proto.WELCOME);
        check(wel.cmd.equals(Proto.WELCOME), "웹으로 접속");
        web.id = wel.i(0, 0);
        check(web.expect(Proto.LOBBY).cmd.equals(Proto.LOBBY), "웹도 로비 정보를 받음");

        Socket tcp = new Socket("127.0.0.1", port);
        DataOutputStream tout = new DataOutputStream(tcp.getOutputStream());
        DataInputStream tin = new DataInputStream(new BufferedInputStream(tcp.getInputStream()));
        tcp.setSoTimeout(8000);
        tout.writeUTF(Proto.encode(Proto.HELLO, Proto.VERSION, "app-test-device-1", "앱유저", 1));
        tout.flush();
        Msg appWel = Msg.parse(tin.readUTF());
        check(appWel.cmd.equals(Proto.WELCOME), "같은 포트로 PC 앱도 접속");
        int appId = appWel.i(0, 0);
        Sender app = (c, fields) -> {
            synchronized (tout) {
                tout.writeUTF(Proto.encode(c, fields));
                tout.flush();
            }
        };

        web.send(Proto.CHAT, "안녕하세요 웹에서 보내요 😀");
        String got = null;
        for (int i = 0; i < 10 && got == null; i++) {
            Msg m = Msg.parse(tin.readUTF());
            if (m.cmd.equals(Proto.CHAT)) got = m.s(4);
        }
        check("안녕하세요 웹에서 보내요 😀".equals(got), "웹 → 앱 채팅 (한글·이모지 UTF-8): " + got);
        tout.writeUTF(Proto.encode(Proto.CHAT, "앱에서 답장"));
        tout.flush();
        Msg wc = web.expect(Proto.CHAT);
        while (wc.cmd.equals(Proto.CHAT) && !wc.s(4).equals("앱에서 답장")) wc = web.expect(Proto.CHAT);
        check(wc.s(4).equals("앱에서 답장"), "앱 → 웹 채팅");

        // 쪼개진 메시지 (FIN 0 + 이어지는 프레임)
        web.frame(0x1, "CHA".getBytes(StandardCharsets.UTF_8), false, true);
        web.frame(0x0, (("T" + Proto.FS + "조각난 메시지")).getBytes(StandardCharsets.UTF_8), true, true);
        got = waitChat(tin, "조각난 메시지");
        check("조각난 메시지".equals(got), "여러 프레임으로 나뉜 메시지 합치기");
        // ping → pong
        web.frame(0x9, "hi".getBytes(StandardCharsets.UTF_8), true, true);
        Object[] f = web.next();
        while (f != null && (int) f[0] != 0xA) f = web.next();
        check(f != null && "hi".equals(f[1]), "ping에 pong으로 답함");
        // 126바이트 넘는 메시지 (확장 길이)
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 150; i++) longText.append('가');
        web.send(Proto.CHAT, longText);
        got = waitChat(tin, longText.toString());
        check(got != null && got.length() == 150, "긴 메시지 (확장 길이 프레임)");

        System.out.println("- 웹과 앱이 한 방에서 한 판");
        web.send(Proto.CREATE, "웹 방", 2, "", 0);
        int roomId = web.expect(Proto.ROOM).i(0, 0);
        check(roomId > 0, "웹에서 방 만들기");
        app.send(Proto.JOIN, roomId, "");
        app.send(Proto.READY, 1);
        Msg ready = web.expect(Proto.ROOM);
        while (ready.cmd.equals(Proto.ROOM) && !(ready.list(7).size() == 2 && Msg.flag(ready.list(7).get(1), 3))) ready = web.expect(Proto.ROOM);
        check(ready.cmd.equals(Proto.ROOM), "앱 사용자가 웹 방에 들어와 준비");
        web.send(Proto.START);

        // 앱 쪽: 자기 차례에 두는 스레드 (시작 패는 검정 1개)
        AtomicInteger appBlacks = new AtomicInteger(-1);
        AtomicReference<Msg> appResult = new AtomicReference<>();
        Thread appPlayer = new Thread(() -> {
            boolean picked = false;
            try {
                while (true) {
                    Msg m = Msg.parse(tin.readUTF());
                    if (m.cmd.equals(Proto.RESULT)) {
                        appResult.set(m);
                        return;
                    }
                    if (!m.cmd.equals(Proto.STATE)) continue;
                    GameView v = GameView.parse(m, appId);
                    if (v.phase == GameView.Phase.SETUP) {
                        if (!picked && !v.me().picked()) {
                            picked = true;
                            app.send(Proto.PICK, 1);
                        }
                        continue;
                    }
                    if (appBlacks.get() < 0) appBlacks.set(countBlack(v.me()));
                    if (v.myTurn()) move(app, appId, v);
                }
            } catch (IOException e) {
                // 연결 종료
            }
        });
        appPlayer.setDaemon(true);
        appPlayer.start();

        // 웹 쪽 (시작 패는 검정 3개)
        Msg result = null;
        int webBlacks = -1;
        boolean webPicked = false, sawSetup = false;
        long end = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < end && result == null) {
            Object[] fr = web.next();
            if (fr == null) break;
            if ((int) fr[0] != 1) continue;
            Msg m = Msg.parse((String) fr[1]);
            if (m.cmd.equals(Proto.RESULT)) result = m;
            if (!m.cmd.equals(Proto.STATE)) continue;
            GameView v = GameView.parse(m, web.id);
            if (v.phase == GameView.Phase.SETUP) {
                sawSetup = true;
                if (!webPicked && !v.me().picked()) {
                    webPicked = true;
                    web.send(Proto.PICK, 3);
                }
                continue;
            }
            if (webBlacks < 0) webBlacks = countBlack(v.me());
            if (!v.myTurn()) continue;
            Thread.sleep(30);
            move(web, web.id, v);
        }
        appPlayer.join(5000);
        check(sawSetup, "웹에서도 시작 패 고르기 단계가 옴");
        check(webBlacks == 3 && appBlacks.get() == 1, "웹·앱 모두 고른 대로 받음 (웹 검정 " + webBlacks + ", 앱 검정 " + appBlacks.get() + ")");
        check(result != null && result.list(0).size() == 2, "웹으로 끝까지 진행하고 결과 받음");
        check(appResult.get() != null && appResult.get().list(0).size() == 2, "앱도 같은 결과를 받음");

        System.out.println("- https 시도 (크롬이 주소창 입력을 https로 먼저 시도하는 경우)");
        try (Socket tls = new Socket("127.0.0.1", port)) {
            tls.setSoTimeout(5000);
            long t0 = System.currentTimeMillis();
            // TLS ClientHello의 앞부분 (0x16 = handshake)
            tls.getOutputStream().write(new byte[] { 0x16, 0x03, 0x01, 0x02, 0x00, 0x01, 0x00, 0x01, (byte) 0xFC, 0x03, 0x03 });
            tls.getOutputStream().flush();
            boolean closed;
            try {
                closed = tls.getInputStream().read() == -1;
            } catch (IOException reset) {
                closed = true;
            }
            long took = System.currentTimeMillis() - t0;
            check(closed && took < 1500, "https 접속은 바로 끊어서 크롬이 http로 다시 오게 함 (" + took + "ms)");
        }

        System.out.println("- 이상한 프레임 막기");
        Ws bad = new Ws(port);
        bad.frame(0x1, Proto.encode(Proto.PING, "1").getBytes(StandardCharsets.UTF_8), true, false); // 마스크 없음
        Object[] after = bad.next();
        check(after == null, "마스크 없는 프레임이면 연결을 끊음");
        Ws polite = new Ws(port);
        polite.frame(0x8, new byte[] { 0x03, (byte) 0xE8 }, true, true);
        Object[] closeEcho = polite.next();
        check(closeEcho != null && (int) closeEcho[0] == 0x8, "닫기 요청에 닫기로 답함");

        tcp.close();
        server.stop();
        System.out.println();
        System.out.println("WebTest: 통과 " + pass + ", 실패 " + fail);
        System.exit(fail > 0 ? 1 : 0);
    }

    /** 앱 쪽에서 원하는 채팅이 올 때까지 다른 메시지(내 채팅 메아리, 로비 갱신)는 건너뛴다 */
    static String waitChat(DataInputStream tin, String want) throws IOException {
        for (int i = 0; i < 40; i++) {
            Msg m = Msg.parse(tin.readUTF());
            if (m.cmd.equals(Proto.CHAT) && m.s(4).equals(want)) return m.s(4);
        }
        return null;
    }

    static int countBlack(GameView.SeatView s) {
        int n = 0;
        for (GameView.TileView t : s.tiles) if (t.black()) n++;
        return n;
    }

    static void move(Sender p, int myId, GameView v) throws IOException {
        switch (v.phase) {
            case DRAW:
                p.send(Proto.DRAW, v.deckB > 0 ? "B" : "W");
                return;
            case JOKER:
                p.send(Proto.JOKER, 0);
                return;
            case REVEAL_SELF:
                for (GameView.TileView t : v.me().tiles) if (!t.revealed) { p.send(Proto.REVEAL, t.uid); return; }
                return;
            default:
        }
        Deduction.Result r = Deduction.analyze(v);
        int bp = 0, bu = 0;
        int[] best = null;
        for (GameView.SeatView s : v.seats) {
            if (s.pid == myId || !s.alive) continue;
            for (GameView.TileView t : s.tiles) {
                if (t.revealed) continue;
                int[] c = r.of(t.uid);
                if (c != null && (best == null || c.length < best.length)) {
                    best = c;
                    bp = s.pid;
                    bu = t.uid;
                }
            }
        }
        if (v.phase == GameView.Phase.CONTINUE && (best == null || best.length > 1)) {
            p.send(Proto.STOP);
            return;
        }
        p.send(Proto.GUESS, bp, bu, best[0] == GameView.JOKER ? "J" : String.valueOf(best[0]));
    }
}
