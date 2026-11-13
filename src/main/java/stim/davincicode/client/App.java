package stim.davincicode.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Taskbar;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import stim.davincicode.client.ui.Anim;
import stim.davincicode.client.ui.Art;
import stim.davincicode.client.ui.Modal;
import stim.davincicode.client.ui.Spinner;
import stim.davincicode.client.ui.Theme;
import stim.davincicode.client.ui.Toast;
import stim.davincicode.client.ui.UI;
import stim.davincicode.common.GameView;
import stim.davincicode.common.Msg;
import stim.davincicode.common.Proto;
import stim.davincicode.server.GameServer;

/**
 * 앱 창 하나와 화면 전환, 서버 연결을 관리한다.
 * 화면: 타이틀 → (온라인) 접속 → 로비 → 방(대기실/게임)
 *       타이틀 → (내 PC에서 서버 열기) 이 PC에서 서버를 열고 바로 로비로
 */
public final class App extends JFrame implements NetClient.Listener {
    private static final long serialVersionUID = 1L;

    public enum Mode { ONLINE, HOST }

    public final Prefs prefs;
    public final Sound sound;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck;
    final TitleScreen title;
    final ConnectScreen connect;
    final LobbyScreen lobby;
    final RoomScreen room;
    private String screen = "";

    private NetClient net;
    private GameServer hosted;
    private Mode mode = Mode.ONLINE;
    private String lastHost;
    private int lastPort;
    private Modal busy;
    private boolean welcomed;
    private boolean kicked;
    private java.util.function.Consumer<String> profileReply; // 프로필 변경 결과를 기다리는 창

    public int myId;
    public String myName = "";
    public String serverName = "";
    public Model.RoomInfo roomInfo;
    public GameView game;

    public App(Prefs prefs, Sound sound) {
        super(Proto.APP_NAME_EN);
        this.prefs = prefs;
        this.sound = sound;
        List<Image> icons = new ArrayList<>();
        for (int s : new int[] { 16, 24, 32, 48, 64, 128, 256 }) icons.add(Art.appIcon(s));
        setIconImages(icons);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));

        deck = new JPanel(cards) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = UI.g(g);
                Art.backdrop(g2, getWidth(), getHeight());
                g2.dispose();
            }
        };
        title = new TitleScreen(this);
        connect = new ConnectScreen(this);
        lobby = new LobbyScreen(this);
        room = new RoomScreen(this);
        deck.add(title, "title");
        deck.add(connect, "connect");
        deck.add(lobby, "lobby");
        deck.add(room, "room");
        JPanel root = new JPanel(new BorderLayout());
        root.add(deck);
        setContentPane(root);

        restoreBounds();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestQuit();
            }
        });
        Timer ping = new Timer(2000, e -> {
            lobby.updatePing(ping());
            room.updatePing(ping());
        });
        ping.start();
        show("title");
    }

    private void restoreBounds() {
        String b = prefs.bounds();
        Rectangle screenBounds = getGraphicsConfiguration().getBounds();
        try {
            String[] p = b.split(",");
            Rectangle r = new Rectangle(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            if (screenBounds.intersects(r) && r.width >= 1100 && r.height >= 720) {
                setBounds(r);
                if (p.length > 4 && "max".equals(p[4])) setExtendedState(MAXIMIZED_BOTH);
                return;
            }
        } catch (RuntimeException ignore) {
            // 처음 실행
        }
        int w = Math.min(1360, Math.max(1100, screenBounds.width - 160));
        int h = Math.min(860, Math.max(720, screenBounds.height - 120));
        setSize(w, h);
        setLocationRelativeTo(null);
    }

    private void saveBounds() {
        boolean max = (getExtendedState() & MAXIMIZED_BOTH) != 0;
        Rectangle r = max ? new Rectangle(getX(), getY(), 1280, 800) : getBounds();
        prefs.bounds(r.x + "," + r.y + "," + r.width + "," + r.height + (max ? ",max" : ""));
        prefs.save();
    }

    // ================================================================ 화면

    void show(String name) {
        if (name.equals(screen)) return;
        String old = screen;
        screen = name;
        cards.show(deck, name);
        screenFor(old).ifPresent(Screen::onHide);
        screenFor(name).ifPresent(Screen::onShow);
    }

    String screen() {
        return screen;
    }

    private java.util.Optional<Screen> screenFor(String name) {
        switch (name) {
            case "title": return java.util.Optional.of(title);
            case "connect": return java.util.Optional.of(connect);
            case "lobby": return java.util.Optional.of(lobby);
            case "room": return java.util.Optional.of(room);
            default: return java.util.Optional.empty();
        }
    }

    public void toast(String text, Toast.Kind kind) {
        Toast.show(this, text, kind);
    }

    public Mode mode() {
        return mode;
    }

    public long ping() {
        return net == null ? -1 : net.rtt();
    }

    public boolean connected() {
        return net != null && !net.isClosed();
    }

    public GameServer hostedServer() {
        return mode == Mode.HOST ? hosted : null;
    }

    /** 닉네임이 없으면 먼저 프로필을 정하게 한다. */
    void requireProfile(Runnable then) {
        if (stim.davincicode.common.Names.checkName(prefs.name()) != null) {
            Dialogs.profile(this, true, then);
        } else {
            then.run();
        }
    }

    /** 배포할 때 공식 서버 주소를 넣어 두는 곳: -Ddc.server=주소:포트 또는 앱 폴더/설정 폴더의 official-server.txt */
    String officialServer() {
        String s = System.getProperty("dc.server");
        if (s != null && !s.trim().isEmpty()) return s.trim();
        List<Path> places = new ArrayList<>();
        String appDir = System.getProperty("jpackage.app-path");
        if (appDir != null) places.add(Paths.get(appDir).getParent().resolve("app").resolve("official-server.txt"));
        places.add(Paths.get("official-server.txt"));
        places.add(prefs.dir().resolve("official-server.txt"));
        for (Path p : places) {
            try {
                if (Files.exists(p)) {
                    String v = new String(Files.readAllBytes(p), StandardCharsets.UTF_8).trim();
                    if (!v.isEmpty()) return v.split("\\s+")[0];
                }
            } catch (IOException | RuntimeException ignore) {
                // 없으면 무시
            }
        }
        return null;
    }

    // ================================================================ 연결

    void connectOnline(String host, int port) {
        stopHosted();
        mode = Mode.ONLINE;
        doConnect(host, port, "서버에 접속하는 중…");
    }

    /** 내 PC에서 서버를 열고 바로 들어간다 (같은 Wi-Fi 친구들이 접속할 수 있다). */
    void hostLocal() {
        stopHosted();
        GameServer.Config cfg = new GameServer.Config();
        cfg.name = prefs.name() + "님의 서버";
        cfg.dataDir = prefs.dir().resolve("server-data");
        cfg.lan = true;
        cfg.logger = line -> System.out.println("[내 서버] " + line);
        int port = -1;
        for (int p = Proto.DEFAULT_PORT; p < Proto.DEFAULT_PORT + 10 && port < 0; p++) {
            cfg.port = p;
            GameServer s = new GameServer(cfg);
            try {
                port = s.start();
                hosted = s;
            } catch (IOException e) {
                // 다음 포트
            }
        }
        if (port < 0) {
            Modal.alert(this, "서버를 열 수 없어요", "30000~30009 포트를 모두 다른 프로그램이 쓰고 있어요. 다른 프로그램을 닫고 다시 시도해 주세요.");
            return;
        }
        mode = Mode.HOST;
        doConnect("127.0.0.1", port, "내 PC에서 서버를 여는 중…");
    }

    private void doConnect(String host, int port, String message) {
        closeNet();
        lastHost = host;
        lastPort = port;
        welcomed = false;
        kicked = false;
        showBusy(message, true);
        new Thread(() -> {
            try {
                NetClient c = NetClient.connect(host, port, 6000, this);
                SwingUtilities.invokeLater(() -> {
                    net = c;
                    c.send(Proto.HELLO, Proto.VERSION, prefs.deviceId(), prefs.name(), prefs.avatar());
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    hideBusy();
                    stopHosted();
                    Modal.alert(this, "접속할 수 없어요", friendly(e, host, port));
                });
            }
        }, "dc-connect").start();
    }

    private static String friendly(IOException e, String host, int port) {
        if (e instanceof UnknownHostException) return "'" + host + "' 주소를 찾을 수 없어요. 주소를 다시 확인해 주세요.";
        if (e instanceof SocketTimeoutException) return host + ":" + port + " 서버가 응답하지 않아요. 같은 네트워크인지, 방화벽이 막고 있지 않은지 확인해 주세요.";
        if (e instanceof ConnectException) return host + ":" + port + " 에 열린 서버가 없어요. 서버가 켜져 있는지 확인해 주세요.";
        return "연결 중 문제가 생겼어요: " + e.getMessage();
    }

    void showBusy(String message, boolean cancellable) {
        hideBusy();
        JPanel p = UI.panel(new BorderLayout(0, 16));
        p.setBorder(UI.pad(30, 40, 26, 40));
        Spinner sp = new Spinner(44);
        p.add(sp, BorderLayout.NORTH);
        JComponent label = UI.label(message, Theme.bold(16), Theme.TEXT);
        ((javax.swing.JLabel) label).setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        p.add(label, BorderLayout.CENTER);
        if (cancellable) {
            stim.davincicode.client.ui.Btn cancel = new stim.davincicode.client.ui.Btn("취소", stim.davincicode.client.ui.Btn.Kind.SECONDARY).small();
            cancel.addActionListener(e -> {
                hideBusy();
                closeNet();
                stopHosted();
                if (!screen.equals("title") && !screen.equals("connect")) show("title");
            });
            JPanel b = UI.panel(new java.awt.FlowLayout());
            b.add(cancel);
            p.add(b, BorderLayout.SOUTH);
        }
        stim.davincicode.client.ui.Controls.Card card = new stim.davincicode.client.ui.Controls.Card(new BorderLayout());
        card.add(p);
        busy = Modal.open(this, card, false);
    }

    void hideBusy() {
        if (busy != null) busy.close();
        busy = null;
    }

    public void send(String cmd, Object... fields) {
        if (net != null) net.send(cmd, fields);
    }

    private void closeNet() {
        if (net != null) {
            NetClient n = net;
            net = null;
            n.close();
        }
    }

    private void stopHosted() {
        if (hosted != null) {
            GameServer s = hosted;
            hosted = null;
            new Thread(s::stop, "dc-stop-server").start();
        }
    }

    private void resetSession() {
        myId = 0;
        roomInfo = null;
        game = null;
        welcomed = false;
    }

    /** 로비에서 닉네임·캐릭터를 바꾼다. done은 성공하면 null, 실패하면 이유를 받는다. */
    void changeProfile(String name, int avatar, java.util.function.Consumer<String> done) {
        if (!connected()) {
            done.accept("서버와 연결이 끊겨 있어요.");
            return;
        }
        profileReply = done;
        send(Proto.PROFILE, name, avatar);
    }

    /** 로비/연결을 정리하고 타이틀로 */
    void logout() {
        if (roomInfo != null) send(Proto.LEAVE);
        closeNet();
        stopHosted();
        resetSession();
        Modal.closeAll();
        show("title");
    }

    /** 방에서 나가기 (게임 중이면 확인을 먼저 받는다) */
    void leaveRoom() {
        Runnable go = () -> send(Proto.LEAVE);
        if (game != null && roomInfo != null && roomInfo.playing && !roomInfo.isSpectator(myId)
                && game.me() != null && game.me().alive) {
            Modal.confirm(this, "게임을 나갈까요?", "게임 중에 나가면 기권으로 처리되고 남은 타일이 모두 공개돼요.", "나가기", true, go);
        } else {
            go.run();
        }
    }

    void requestQuit() {
        Runnable quit = () -> {
            saveBounds();
            if (roomInfo != null) send(Proto.LEAVE);
            closeNet();
            if (hosted != null) hosted.stop();
            dispose();
            System.exit(0);
        };
        if (mode == Mode.HOST && hosted != null && hosted.online() > 1) {
            Modal.confirm(this, "앱을 끌까요?", "내 PC의 서버가 닫혀서 접속한 친구들의 연결도 끊어져요.", "끄기", true, quit);
        } else if (game != null && roomInfo != null && roomInfo.playing && game.me() != null && game.me().alive) {
            Modal.confirm(this, "앱을 끌까요?", "게임 중에 끄면 기권으로 처리돼요.", "끄기", true, quit);
        } else {
            quit.run();
        }
    }

    // ================================================================ 받은 메시지

    @Override
    public void onMessage(Msg m) {
        switch (m.cmd) {
            case Proto.WELCOME: onWelcome(m); break;
            case Proto.ERROR: onError(m); break;
            case Proto.NOTICE: toast(m.s(0), Toast.Kind.INFO); break;
            case Proto.KICKED:
                kicked = true;
                closeNet();
                stopHosted();
                resetSession();
                hideBusy();
                Modal.closeAll();
                show("title");
                Modal.alert(this, "연결이 종료됐어요", m.s(0));
                break;
            case Proto.LOBBY:
                serverName = m.s(2);
                lobby.update(m);
                break;
            case Proto.ROOM: {
                Model.RoomInfo r = Model.parseRoom(m);
                boolean entering = roomInfo == null || roomInfo.id != r.id || !screen.equals("room");
                roomInfo = r;
                if (entering) {
                    game = null;
                    room.enter(r);
                    show("room");
                }
                room.updateRoom(r);
                break;
            }
            case Proto.LEFT:
                roomInfo = null;
                game = null;
                Modal.closeAll();
                show("lobby");
                if (!m.s(0).isEmpty()) toast(m.s(0), Toast.Kind.WARN);
                break;
            case Proto.STATE:
                game = GameView.parse(m, myId);
                room.updateGame(game);
                break;
            case Proto.EVENT:
                room.onEvent(m);
                break;
            case Proto.RESULT:
                room.onResult(m);
                break;
            case Proto.CHAT:
                if (prefs.isBlocked(m.s(2))) break;
                if ("L".equals(m.s(0))) lobby.chat.addChat(m.i(1, 0), m.s(2), m.i(3, 0), m.s(4), false);
                else room.chat.addChat(m.i(1, 0), m.s(2), m.i(3, 0), m.s(4), "S".equals(m.s(5)));
                if (m.i(1, 0) != myId && prefs.chatSound()) sound.play(Sound.Sfx.CHAT);
                break;
            case Proto.SYS:
                room.chat.addSystem(m.s(1));
                break;
            case Proto.WHISPER:
                if (prefs.isBlocked(m.s(0))) break;
                (screen.equals("room") ? room.chat : lobby.chat).addWhisper(m.s(0), m.s(1), m.i(2, 0), m.s(3), myName);
                if (!m.s(0).equals(myName) && prefs.chatSound()) sound.play(Sound.Sfx.CHAT);
                break;
            case Proto.REACT:
                room.onReact(m.i(0, 0), m.i(1, 0));
                break;
            case Proto.PROFILE: {
                // 서버가 닉네임·캐릭터 변경을 받아들였다
                myName = m.s(0);
                prefs.name(myName);
                prefs.avatar(m.i(1, prefs.avatar()));
                prefs.save();
                lobby.refreshMe();
                title.repaint();
                toast("프로필을 바꿨어요.", Toast.Kind.SUCCESS);
                if (profileReply != null) {
                    java.util.function.Consumer<String> r = profileReply;
                    profileReply = null;
                    r.accept(null);
                }
                break;
            }
            default:
                // 모르는 메시지는 무시 (새 버전 서버와의 호환)
        }
    }

    private void onWelcome(Msg m) {
        hideBusy();
        welcomed = true;
        myId = m.i(0, 0);
        myName = m.s(1);
        serverName = m.s(2);
        boolean resumed = m.b(3);
        if (mode == Mode.ONLINE) {
            prefs.addRecent(lastHost + ":" + lastPort);
            prefs.save();
        }
        if (resumed) {
            toast("다시 연결됐어요. 하던 게임을 이어서 해요.", Toast.Kind.SUCCESS);
            return;
        }
        roomInfo = null;
        game = null;
        show("lobby");
        if (mode == Mode.HOST) {
            toast("서버를 열었어요. 위쪽 '내 서버' 주소를 눌러 복사해 보내면 친구가 브라우저로도 바로 들어와요.", Toast.Kind.SUCCESS);
        }
    }

    private void onError(Msg m) {
        String code = m.s(0), text = m.s(1);
        if (profileReply != null && (code.equals("BAD_NAME") || code.equals("NAME_TAKEN") || code.equals("IN_ROOM") || code.equals("PROFILE_WAIT"))) {
            java.util.function.Consumer<String> r = profileReply;
            profileReply = null;
            r.accept(text); // 프로필 창에 이유를 보여 준다
            return;
        }
        if (!welcomed) {
            hideBusy();
            closeNet();
            stopHosted();
            Modal.alert(this, "접속할 수 없어요", text);
            if (code.equals("NAME_TAKEN") || code.equals("BAD_NAME")) Dialogs.profile(this, false, null);
            return;
        }
        if (code.equals("BAD_MOVE")) room.onBadMove(text);
        toast(text, code.equals("BAD_MOVE") ? Toast.Kind.WARN : Toast.Kind.ERROR);
    }

    @Override
    public void onClosed(NetClient source, String reason) {
        if (source != net) return;
        net = null;
        if (kicked) return;
        boolean wasPlaying = welcomed;
        if (mode == Mode.ONLINE && wasPlaying) {
            reconnect(1);
            return;
        }
        resetSession();
        stopHosted();
        hideBusy();
        Modal.closeAll();
        show("title");
        Modal.alert(this, "연결이 끊어졌어요", reason);
    }

    /** 온라인 서버와 끊기면 잠깐 동안 자동으로 다시 연결해 본다 (게임 중이면 서버가 60초 동안 자리를 지켜 준다). */
    private void reconnect(int attempt) {
        final int max = 6;
        showBusy("연결이 끊겼어요. 다시 연결하는 중… (" + attempt + "/" + max + ")", true);
        new Thread(() -> {
            try {
                Thread.sleep(Math.min(5000, 800L * attempt));
                NetClient c = NetClient.connect(lastHost, lastPort, 5000, this);
                SwingUtilities.invokeLater(() -> {
                    if (busy == null) {
                        c.close();
                        return;
                    }
                    net = c;
                    welcomed = false;
                    c.send(Proto.HELLO, Proto.VERSION, prefs.deviceId(), prefs.name(), prefs.avatar());
                });
            } catch (IOException | InterruptedException e) {
                SwingUtilities.invokeLater(() -> {
                    if (busy == null) return; // 사용자가 취소함
                    if (attempt < max) {
                        reconnect(attempt + 1);
                    } else {
                        hideBusy();
                        resetSession();
                        show("connect");
                        Modal.alert(this, "다시 연결하지 못했어요", "인터넷 연결을 확인한 뒤 다시 접속해 주세요.");
                    }
                });
            }
        }, "dc-reconnect").start();
    }

    /** 내 차례가 됐을 때 창이 뒤에 있으면 작업 표시줄에서 알려 준다. */
    void alertTurn() {
        if (!prefs.turnAlert() || isFocused()) return;
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
                Taskbar.getTaskbar().requestWindowUserAttention(this);
            }
        } catch (RuntimeException ignore) {
            // 지원하지 않는 환경
        }
    }

    void applyPrefs() {
        sound.setEnabled(prefs.sound());
        sound.setVolume(prefs.volume());
        Anim.reduced = prefs.reduceMotion();
    }

    /** 화면들이 공통으로 갖는 생명주기 */
    interface Screen {
        default void onShow() { }

        default void onHide() { }
    }
}
