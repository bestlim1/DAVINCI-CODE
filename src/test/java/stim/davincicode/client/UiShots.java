package stim.davincicode.client;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import stim.davincicode.client.ui.Theme;
import stim.davincicode.common.Deduction;
import stim.davincicode.common.GameView;
import stim.davincicode.common.Msg;
import stim.davincicode.common.Proto;
import stim.davincicode.server.GameServer;

/**
 * 실제 앱 창을 띄워 서버에 붙이고, 화면을 차례로 진행시키면서 스크린샷을 찍는다.
 *   java -cp ... stim.davincicode.client.UiShots 출력폴더
 */
public final class UiShots {
    static App app;
    static File out;

    /** 화면 없이 프로토콜로만 움직이는 테스트 사용자 */
    static final class Raw {
        final DataOutputStream o;
        final LinkedBlockingQueue<Msg> in = new LinkedBlockingQueue<>();
        int id;
        boolean picked; // 이번 판에 시작 패를 보냈는가

        Raw(int port, String name, int avatar) throws Exception {
            Socket s = new Socket("127.0.0.1", port);
            o = new DataOutputStream(s.getOutputStream());
            DataInputStream d = new DataInputStream(s.getInputStream());
            Thread t = new Thread(() -> {
                try {
                    while (true) in.add(Msg.parse(d.readUTF()));
                } catch (Exception e) {
                    // 끝
                }
            });
            t.setDaemon(true);
            t.start();
            send(Proto.HELLO, Proto.VERSION, "shots-device-" + name, name, avatar);
            id = expect(Proto.WELCOME).i(0, 0);
        }

        void send(String c, Object... f) throws Exception {
            synchronized (o) {
                o.writeUTF(Proto.encode(c, f));
                o.flush();
            }
        }

        Msg expect(String cmd) throws Exception {
            long end = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < end) {
                Msg m = in.poll(100, TimeUnit.MILLISECONDS);
                if (m != null && m.cmd.equals(cmd)) return m;
            }
            throw new IllegalStateException("기다린 메시지가 오지 않음: " + cmd);
        }
    }

    public static void main(String[] args) throws Exception {
        out = new File(args.length > 0 ? args[0] : "build/shots");
        out.mkdirs();
        System.setProperty("dc.profile", "shots");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        Prefs prefs = new Prefs();
        prefs.name("재윤");
        prefs.avatar(2);
        prefs.agreed(true);
        prefs.seenTutorial(true);
        prefs.sound(false);
        prefs.bounds("");
        prefs.save();

        GameServer.Config cfg = new GameServer.Config();
        cfg.port = 0;
        cfg.bind = "127.0.0.1";
        cfg.lan = false;
        cfg.seed = 5L;
        cfg.name = "STIM 테스트 서버";
        cfg.logger = l -> { };
        GameServer server = new GameServer(cfg);
        int port = server.start();

        SwingUtilities.invokeAndWait(() -> {
            Theme.init();
            ClientMain.styleSwing();
            app = new App(prefs, new Sound());
            app.applyPrefs();
            app.setExtendedState(0);
            app.setSize(Integer.getInteger("shots.w", 1366), Integer.getInteger("shots.h", 840));
            app.setLocation(40, 40);
            app.setVisible(true);
        });
        pause(1500);
        shot("01_title");

        // 로비를 채울 다른 사용자와 방
        Raw a = new Raw(port, "제윤", 5);
        Raw b = new Raw(port, "민수", 3);
        Raw c = new Raw(port, "하늘", 6);
        Raw b2 = new Raw(port, "도윤", 7);
        b.send(Proto.CREATE, "초보만 오세요~", 4, "", 60);
        c.send(Proto.CREATE, "고수방 (비번 있음)", 2, "1234", 30);
        b2.send(Proto.JOIN, b.expect(Proto.ROOM).i(0, 0), "");

        on(() -> app.show("connect"));
        pause(1600);
        shot("02_connect");

        on(() -> app.connectOnline("127.0.0.1", port));
        waitFor(() -> "lobby".equals(app.screen()), 8000);
        pause(600);
        a.send(Proto.CHAT, "안녕하세요! 한 판 하실 분?");
        pause(300);
        c.send(Proto.CHAT, "저요 ㅎㅎ 잠깐만요");
        pause(900);
        shot("03_lobby");
        on(() -> Dialogs.profileOnline(app));
        pause(700);
        shot("03b_profile_edit");
        on(() -> stim.davincicode.client.ui.Modal.closeAll());

        on(() -> Dialogs.createRoom(app));
        pause(700);
        shot("04_create_room");
        on(() -> stim.davincicode.client.ui.Modal.closeAll());

        app.send(Proto.CREATE, "재윤이네 추리방", 3, "", 60);
        waitFor(() -> "room".equals(app.screen()) && app.roomInfo != null, 5000);
        int roomId = app.roomInfo.id;
        Raw e = new Raw(port, "하람", 4);
        a.send(Proto.JOIN, roomId, "");
        pause(300);
        a.send(Proto.CHAT, "들어왔어요! 준비할게요");
        a.send(Proto.READY, 1);
        pause(300);
        e.send(Proto.JOIN, roomId, "");
        pause(300);
        e.send(Proto.READY, 1);
        pause(900);
        shot("05_waiting");

        app.send(Proto.START);
        waitFor(() -> app.game != null && app.game.phase == GameView.Phase.SETUP, 5000);
        pause(1600);
        a.send(Proto.PICK, 2); // 한 사람은 먼저 골라 둔 모습
        pause(700);
        shot("06a_setup_pick");
        boolean[] clicked = new boolean[1];
        on(() -> clicked[0] = app.room.board.clickPick(3));
        if (!clicked[0]) throw new IllegalStateException("시작 패 카드를 누르지 못함");
        pause(600);
        shot("06b_setup_waiting");
        // 내 차례가 될 때까지 다른 사람(제윤, 하람)은 자동으로 둔다
        long end = System.currentTimeMillis() + 60000;
        boolean drew = false;
        while (System.currentTimeMillis() < end) {
            drive(a);
            drive(e);
            GameView v = app.game;
            if (v != null && v.myTurn()) {
                if (v.phase == GameView.Phase.DRAW && !drew) {
                    pause(1600);
                    shot("06_game_my_turn");
                    app.send(Proto.DRAW, "W");
                    drew = true;
                    pause(1200);
                } else if (v.phase == GameView.Phase.GUESS) {
                    break;
                } else if (v.phase == GameView.Phase.JOKER) {
                    app.send(Proto.JOKER, 0);
                    pause(500);
                }
            }
            pause(60);
        }
        pause(500);
        shot("07_game_guess_phase");
        on(() -> app.room.board.openFirstTarget());
        pause(500);
        shot("08_guess_popup");
        on(() -> app.room.board.repaint());
        // 이후는 자동으로 끝까지 진행
        a.send(Proto.CHAT, "오 그건 어떻게 알았어요?");
        app.send(Proto.REACT, 0);
        boolean shotMid = false;
        end = System.currentTimeMillis() + 180000;
        while (System.currentTimeMillis() < end && app.game != null && app.game.phase != GameView.Phase.OVER) {
            drive(a);
            drive(e);
            GameView v = app.game;
            if (v != null && v.myTurn()) {
                pause(250);
                move(app, v);
                pause(900);
                if (!shotMid && v.phase == GameView.Phase.CONTINUE) {
                    shot("09_game_continue");
                    shotMid = true;
                }
            }
            pause(40);
        }
        pause(1200);
        shot("10_game_over");
        pause(1800);
        shot("11_result");
        on(() -> stim.davincicode.client.ui.Modal.closeAll());
        pause(800);

        on(() -> Dialogs.howTo(app));
        pause(900);
        shot("12_howto");
        on(() -> clickButton(app.getRootPane(), "다음"));
        pause(900);
        shot("12b_howto_setup");
        on(() -> stim.davincicode.client.ui.Modal.closeAll());
        on(() -> Dialogs.settings(app));
        pause(700);
        shot("13_settings");
        on(() -> stim.davincicode.client.ui.Modal.closeAll());
        on(() -> Dialogs.profile(app, true, null));
        pause(700);
        shot("14_profile");
        on(() -> stim.davincicode.client.ui.Modal.closeAll());

        // 관전 화면: 다른 방에서 네 사람이 하는 게임을 본다
        app.send(Proto.LEAVE);
        waitFor(() -> "lobby".equals(app.screen()), 5000);
        Raw d = new Raw(port, "지수", 1);
        d.send(Proto.CREATE, "4인전 구경", 4, "", 30);
        int specRoom = d.expect(Proto.ROOM).i(0, 0);
        Raw[] four = { d, new Raw(port, "서연", 0), new Raw(port, "민준", 5), new Raw(port, "유나", 6) };
        for (int i = 1; i < four.length; i++) {
            four[i].send(Proto.JOIN, specRoom, "");
            four[i].send(Proto.READY, 1);
        }
        pause(600);
        d.send(Proto.START);
        pause(300);
        app.send(Proto.SPECTATE, specRoom, "");
        // 사람처럼 천천히 둬서 게임 중간 모습을 찍는다
        long until = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < until) {
            for (Raw r : four) drive(r);
            pause(420);
        }
        shot("15_spectate_4p");

        server.stop();
        System.out.println("스크린샷 저장: " + out.getAbsolutePath());
        System.exit(0);
    }

    /** 테스트 사용자가 자기 차례면 간단한 전략으로 둔다 */
    static void drive(Raw r) throws Exception {
        Msg m;
        GameView last = null;
        while ((m = r.in.poll()) != null) {
            if (m.cmd.equals(Proto.STATE)) last = GameView.parse(m, r.id);
        }
        if (last == null) return;
        if (last.phase == GameView.Phase.SETUP) {
            // 시작 패는 모두 동시에 고른다 (검정 2개)
            if (!r.picked && last.me() != null && !last.me().picked()) {
                r.picked = true;
                r.send(Proto.PICK, Math.min(2, last.handSize));
            }
            return;
        }
        r.picked = false;
        if (last.myTurn()) moveRaw(r, last);
    }

    static void moveRaw(Raw r, GameView v) throws Exception {
        Object[] mv = choose(v, r.id);
        r.send((String) mv[0], (Object[]) mv[1]);
    }

    static void move(App app, GameView v) {
        Object[] mv = choose(v, app.myId);
        app.send((String) mv[0], (Object[]) mv[1]);
    }

    static Object[] choose(GameView v, int me) {
        switch (v.phase) {
            case DRAW: return new Object[] { Proto.DRAW, new Object[] { v.deckB > v.deckW ? "B" : "W" } };
            case JOKER: return new Object[] { Proto.JOKER, new Object[] { 0 } };
            case REVEAL_SELF:
                for (GameView.TileView t : v.me().tiles) if (!t.revealed) return new Object[] { Proto.REVEAL, new Object[] { t.uid } };
                break;
            default:
        }
        Deduction.Result r = Deduction.analyze(v);
        int bp = 0, bu = 0;
        int[] best = null;
        for (GameView.SeatView s : v.seats) {
            if (s.pid == me || !s.alive) continue;
            for (GameView.TileView t : s.tiles) {
                if (t.revealed) continue;
                int[] c = t.known() ? new int[] { t.value } : r.of(t.uid);
                if (c != null && (best == null || c.length < best.length)) {
                    best = c;
                    bp = s.pid;
                    bu = t.uid;
                }
            }
        }
        if (v.phase == GameView.Phase.CONTINUE && (best == null || best.length > 1)) return new Object[] { Proto.STOP, new Object[0] };
        int val = best[0];
        return new Object[] { Proto.GUESS, new Object[] { bp, bu, val == GameView.JOKER ? "J" : String.valueOf(val) } };
    }

    /** 화면에서 글자가 같은 버튼을 찾아 누른다 (대화상자 넘기기용) */
    static boolean clickButton(java.awt.Container root, String text) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.AbstractButton && text.equals(((javax.swing.AbstractButton) c).getText()) && c.isShowing()) {
                ((javax.swing.AbstractButton) c).doClick();
                return true;
            }
            if (c instanceof java.awt.Container && clickButton((java.awt.Container) c, text)) return true;
        }
        return false;
    }

    static void on(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    static void waitFor(BooleanSupplier cond, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            boolean[] ok = new boolean[1];
            on(() -> ok[0] = cond.getAsBoolean());
            if (ok[0]) return;
            pause(80);
        }
        throw new IllegalStateException("시간 안에 화면이 바뀌지 않음");
    }

    static void shot(String name) throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        on(() -> {
            javax.swing.JRootPane rp = app.getRootPane();
            BufferedImage b = new BufferedImage(rp.getWidth(), rp.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = b.createGraphics();
            rp.paint(g);
            g.dispose();
            img[0] = b;
        });
        ImageIO.write(img[0], "png", new File(out, name + ".png"));
        System.out.println("  찍음: " + name);
    }
}
