package stim.davincicode.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import stim.davincicode.client.ui.Art;
import stim.davincicode.client.ui.Btn;
import stim.davincicode.client.ui.Controls;
import stim.davincicode.client.ui.Field;
import stim.davincicode.client.ui.Icons;
import stim.davincicode.client.ui.Modal;
import stim.davincicode.client.ui.Theme;
import stim.davincicode.client.ui.Toast;
import stim.davincicode.client.ui.UI;
import stim.davincicode.common.GameView;
import stim.davincicode.common.Msg;
import stim.davincicode.common.Names;
import stim.davincicode.common.Proto;

/** 방 화면: 가운데는 대기실 또는 게임판, 오른쪽은 채팅과 게임 기록 */
final class RoomScreen extends JPanel implements App.Screen {
    private static final long serialVersionUID = 1L;
    private final App app;
    final ChatPanel chat;
    final BoardView board;
    private final Waiting waiting;
    private final CardLayout cards = new CardLayout();
    private final JPanel center = UI.panel(cards);
    private final JLabel title = UI.label("", Theme.bold(19), Theme.TEXT);
    private final Controls.Chip idChip = new Controls.Chip("#0", Theme.TEXT_3);
    private final Controls.Chip lockChip = new Controls.Chip("비공개", Theme.AMBER).icon("lock");
    private final Controls.Chip infoChip = new Controls.Chip("", Theme.SKY).icon("users");
    private final TopBar.Pill pingPill = new TopBar.Pill("", Theme.TEXT_3, "wifi");
    private final TopBar.Pill specPill = new TopBar.Pill("", Theme.VIOLET, "eye");
    private boolean showingBoard;
    private boolean resultPending;
    private boolean lastOver;
    private Modal resultModal;

    RoomScreen(App app) {
        super(new BorderLayout());
        this.app = app;
        setOpaque(false);
        chat = new ChatPanel(app, "채팅", true);
        board = new BoardView(app);
        waiting = new Waiting();

        TopBar bar = new TopBar();
        Btn leave = new Btn("나가기", Btn.Kind.GHOST).small().icon("back");
        leave.addActionListener(e -> app.leaveRoom());
        bar.addLeft(leave);
        bar.addCenter(title);
        bar.addCenter(idChip);
        bar.addCenter(lockChip);
        bar.addCenter(infoChip);
        Btn how = Btn.icon("help", "게임 방법");
        how.addActionListener(e -> Dialogs.howTo(app));
        Btn set = Btn.icon("gear", "설정");
        set.addActionListener(e -> Dialogs.settings(app));
        bar.addRight(specPill);
        bar.addRight(pingPill);
        bar.addRight(how);
        bar.addRight(set);
        add(bar, BorderLayout.NORTH);

        center.add(waiting, "wait");
        center.add(board, "board");
        JPanel body = UI.panel(new BorderLayout(16, 0));
        body.setBorder(UI.pad(14, 16, 16, 16));
        body.add(center, BorderLayout.CENTER);
        chat.setPreferredSize(new Dimension(360, 100));
        body.add(chat, BorderLayout.EAST);
        add(body, BorderLayout.CENTER);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int cw = getWidth() < 1250 ? 310 : 360;
                if (chat.getPreferredSize().width != cw) {
                    chat.setPreferredSize(new Dimension(cw, 100));
                    revalidate();
                }
            }
        });
    }

    void updatePing(long ms) {
        pingPill.set(TopBar.pingText(ms), TopBar.pingColor(ms));
    }

    void enter(Model.RoomInfo r) {
        chat.clear();
        board.reset();
        resultPending = false;
        lastOver = false;
        if (resultModal != null) resultModal.close();
        showBoard(false);
    }

    @Override
    public void onHide() {
        board.stopTimers();
        if (resultModal != null) resultModal.close();
    }

    private void showBoard(boolean b) {
        showingBoard = b;
        cards.show(center, b ? "board" : "wait");
    }

    void updateRoom(Model.RoomInfo r) {
        title.setText(UI.ellipsize(r.title, Theme.bold(19), 360));
        idChip.set("#" + r.id, Theme.TEXT_3);
        lockChip.setVisible(r.locked);
        String info = r.members.size() + "/" + r.max + "명  ·  " + Model.turnText(r.turnSec);
        infoChip.set(info, Theme.SKY);
        specPill.setVisible(!r.spectators.isEmpty());
        specPill.set("관전 " + r.spectators.size(), Theme.VIOLET);
        board.updateWho(r);
        waiting.update(r);
        if (r.playing && !showingBoard && app.game != null) showBoard(true);
        if (!r.playing && showingBoard && !resultPending && (resultModal == null || resultModal.isClosed())) {
            showBoard(false);
        }
        revalidate();
        repaint();
    }

    void updateGame(GameView v) {
        boolean newGame = !showingBoard || (lastOver && v.phase != GameView.Phase.OVER) || board.view() == null;
        if (newGame) {
            board.reset();
            if (app.roomInfo != null) board.updateWho(app.roomInfo);
            chat.addLog("새 게임이 시작됐어요.", Theme.CORAL);
            if (resultModal != null) resultModal.close();
            resultPending = false;
        }
        lastOver = v.phase == GameView.Phase.OVER;
        board.update(v);
        if (!showingBoard) showBoard(true);
    }

    void onEvent(Msg m) {
        board.onEvent(m, chat);
    }

    void onReact(int pid, int code) {
        board.onReact(pid, code);
        String name = board.nameOf(pid);
        chat.addLog(name + " : " + ChatPanel.REACTIONS[Math.max(0, Math.min(5, code))], Theme.TEXT_3);
    }

    void onBadMove(String text) {
        board.repaint();
    }

    /** 결과는 마지막 공개 장면과 축하 효과를 잠깐 보여 준 뒤에 띄운다 */
    void onResult(Msg m) {
        resultPending = true;
        Timer t = new Timer(1900, e -> {
            resultPending = false;
            if (!app.screen().equals("room")) return;
            showResult(m);
        });
        t.setRepeats(false);
        t.start();
    }

    private void showResult(Msg m) {
        List<String[]> ranks = m.list(0);
        int myPlace = 0;
        for (String[] r : ranks) if (Msg.num(r, 0, 0) == app.myId) myPlace = Msg.num(r, 3, 0);
        boolean spectator = myPlace == 0;
        String head = spectator ? "게임 종료" : myPlace == 1 ? "승리했어요!" : myPlace + "위로 마쳤어요";
        String sub = spectator ? "관전한 게임의 최종 순위예요." : myPlace == 1 ? "마지막까지 타일을 지켜 냈어요." : "다시 하려면 대기실에서 준비를 눌러 주세요.";

        JPanel list = UI.panel(null);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (String[] r : ranks) {
            list.add(new RankRow(r));
            list.add(Box.createVerticalStrut(8));
        }
        Btn primary = new Btn("대기실로", Btn.Kind.PRIMARY);
        Btn secondary = new Btn("로비로 나가기", Btn.Kind.SECONDARY);
        Modal.Dialog d = Modal.dialog(app, head, sub, list, 460, true, secondary, primary);
        resultModal = d.modal;
        resultModal.onClose(() -> {
            if (app.roomInfo != null && !app.roomInfo.playing && app.screen().equals("room")) showBoard(false);
        });
        primary.addActionListener(e -> d.close());
        secondary.addActionListener(e -> {
            d.close();
            app.send(Proto.LEAVE);
        });
    }

    private static final class RankRow extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String[] r;

        RankRow(String[] r) {
            this.r = r;
            setAlignmentX(0f);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(400, 58);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 58);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            int place = Msg.num(r, 3, 0);
            int w = getWidth();
            g.setColor(place == 1 ? UI.a(Theme.AMBER, 30) : Theme.SURFACE_LO);
            g.fill(UI.rr(0, 0, w, 58, 14));
            Color medal = place == 1 ? Theme.AMBER : place == 2 ? new Color(0xC9D3E6) : place == 3 ? new Color(0xD9965B) : Theme.TEXT_3;
            g.setColor(medal);
            g.fill(new java.awt.geom.Ellipse2D.Double(14, 15, 28, 28));
            UI.center(g, String.valueOf(place), Theme.num(15), new Color(0x161A28), 28, 29);
            Art.avatar(g, 54, 11, 36, Msg.num(r, 2, 0), false);
            UI.text(g, Msg.str(r, 1), Theme.bold(15), Theme.TEXT, 102, (58 - UI.fm(Theme.bold(15)).getHeight()) / 2.0);
            if (place == 1) {
                String d = "우승";
                double cw = UI.width(Theme.bold(13), d) + 24;
                g.setColor(UI.a(Theme.AMBER, 40));
                g.fill(UI.rr(w - cw - 14, 16, cw, 26, 13));
                UI.center(g, d, Theme.bold(13), Theme.AMBER, w - cw / 2 - 14, 29);
            }
            g.dispose();
        }
    }

    // ================================================================ 대기실

    private final class Waiting extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Seats seats = new Seats();
        private final Controls.Segmented max = new Controls.Segmented(2, "2명", "3명", "4명");
        private final Controls.Segmented time = new Controls.Segmented(3, "30초", "45초", "60초", "90초", "무제한");
        private final Field titleField = new Field("방 제목").limit(Names.TITLE_MAX);
        private final Field.Pass pass = new Field.Pass("비밀번호 (선택)").limit(12);
        private final Btn apply = new Btn("이름·비밀번호 적용", Btn.Kind.SECONDARY).small();
        private final Btn action = new Btn("준비하기", Btn.Kind.PRIMARY).large();
        private final JLabel hint = UI.label("", Theme.font(14), Theme.TEXT_2);
        private final JLabel specs = UI.label("", Theme.font(13), Theme.TEXT_3);
        private final List<JComponent> hostOnly = new ArrayList<>();
        private Model.RoomInfo room;

        Waiting() {
            super(new BorderLayout(0, 16));
            setOpaque(false);
            setBorder(UI.pad(6, 6, 6, 6));

            JPanel head = UI.panel(new BorderLayout());
            JPanel ht = UI.panel(null);
            ht.setLayout(new BoxLayout(ht, BoxLayout.Y_AXIS));
            ht.add(UI.label("대기실", Theme.bold(26), Theme.TEXT));
            ht.add(Box.createVerticalStrut(4));
            ht.add(hint);
            head.add(ht, BorderLayout.WEST);
            JPanel hr = UI.panel(new GridBagLayout());
            hr.add(specs);
            head.add(hr, BorderLayout.EAST);
            add(head, BorderLayout.NORTH);

            JPanel mid = UI.panel(new BorderLayout(0, 16));
            mid.add(seats, BorderLayout.CENTER);

            Controls.Card set = new Controls.Card(new GridLayout(1, 2, 28, 0));
            set.setBorder(UI.pad(18, 22, 18, 22));
            JPanel c1 = UI.panel(null);
            c1.setLayout(new BoxLayout(c1, BoxLayout.Y_AXIS));
            c1.add(labeled("최대 인원", max));
            c1.add(Box.createVerticalStrut(12));
            c1.add(labeled("한 번에 쓸 수 있는 시간", time));
            JPanel c2 = UI.panel(null);
            c2.setLayout(new BoxLayout(c2, BoxLayout.Y_AXIS));
            JPanel names = UI.panel(new GridLayout(1, 2, 8, 0));
            names.add(titleField);
            names.add(pass);
            c2.add(labeled("방 이름 · 비밀번호", names));
            c2.add(Box.createVerticalStrut(8));
            JPanel ap = UI.panel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            ap.add(apply);
            c2.add(ap);
            for (java.awt.Component c : c1.getComponents()) ((JComponent) c).setAlignmentX(0f);
            for (java.awt.Component c : c2.getComponents()) ((JComponent) c).setAlignmentX(0f);
            set.add(c1);
            set.add(c2);
            mid.add(set, BorderLayout.SOUTH);
            add(mid, BorderLayout.CENTER);

            JPanel foot = UI.panel(new GridBagLayout());
            action.setPreferredSize(new Dimension(320, 54));
            foot.add(action);
            add(foot, BorderLayout.SOUTH);

            hostOnly.add(max);
            hostOnly.add(time);
            hostOnly.add(titleField);
            hostOnly.add(pass);
            hostOnly.add(apply);

            max.onChange(i -> send(false));
            time.onChange(i -> send(false));
            apply.addActionListener(e -> send(true));
            titleField.addActionListener(e -> send(true));
            action.addActionListener(e -> act());
        }

        private JComponent labeled(String label, JComponent c) {
            JPanel p = UI.panel(new BorderLayout(0, 6));
            p.add(UI.label(label, Theme.bold(13), Theme.TEXT_2), BorderLayout.NORTH);
            p.add(c, BorderLayout.CENTER);
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            return p;
        }

        private int turnValue() {
            int[] v = { 30, 45, 60, 90, 0 };
            return v[time.getSelected()];
        }

        /** 방장이 설정을 바꾸면 바로 서버에 보낸다. 비밀번호 칸을 비워 두면 기존 비밀번호를 유지한다 ("*"). */
        private void send(boolean withText) {
            if (room == null) return;
            String pw = new String(pass.getPassword());
            String sendPw = withText ? pw : "*";
            if (withText && pw.isEmpty() && room.locked) sendPw = "";
            String t = withText ? titleField.getText() : room.title;
            app.send(Proto.SETTINGS, t, max.getSelected() + 2, sendPw, turnValue());
            if (withText) {
                pass.setText("");
                app.toast(pw.isEmpty() ? "방 이름을 바꿨어요." + (room.locked ? " 비밀번호도 없앴어요." : "") : "방 이름과 비밀번호를 적용했어요.", Toast.Kind.SUCCESS);
            }
        }

        private void act() {
            if (room == null) return;
            boolean host = room.hostId == app.myId;
            if (room.isSpectator(app.myId)) {
                app.send(Proto.JOIN, room.id, "");
            } else if (host) {
                app.send(Proto.START);
            } else {
                Model.Member me = room.member(app.myId);
                app.send(Proto.READY, Proto.bool(me == null || !me.ready));
            }
        }

        void update(Model.RoomInfo r) {
            room = r;
            boolean host = r.hostId == app.myId;
            boolean spectator = r.isSpectator(app.myId);
            for (JComponent c : hostOnly) c.setEnabled(host);
            max.setSelected(r.max - 2);
            int[] tv = { 30, 45, 60, 90, 0 };
            for (int i = 0; i < tv.length; i++) if (tv[i] == r.turnSec) time.setSelected(i);
            if (!titleField.isFocusOwner()) titleField.setText(r.title);
            seats.repaint();

            Model.Member me = r.member(app.myId);
            if (spectator) {
                action.setText(r.members.size() < r.max && !r.playing ? "참가하기" : "관전 중");
                action.setEnabled(r.members.size() < r.max && !r.playing);
                action.kind(Btn.Kind.SECONDARY);
                hint.setText("관전 중이에요. 자리가 있으면 게임에 참가할 수 있어요.");
            } else if (host) {
                boolean can = r.members.size() >= 2 && r.allReady();
                action.setText("게임 시작");
                action.setEnabled(can);
                action.kind(Btn.Kind.PRIMARY);
                action.icon("play");
                hint.setText(r.members.size() < 2 ? "친구가 들어오기를 기다리는 중이에요. 2명부터 시작할 수 있어요."
                        : r.allReady() ? "모두 준비됐어요. 게임을 시작하세요!" : "모두 준비하면 시작할 수 있어요.");
            } else {
                boolean ready = me != null && me.ready;
                action.setText(ready ? "준비 취소" : "준비하기");
                action.setEnabled(true);
                action.kind(ready ? Btn.Kind.SECONDARY : Btn.Kind.MINT);
                action.icon(ready ? "close" : "check");
                hint.setText(ready ? "방장이 시작하기를 기다리는 중이에요." : "준비를 누르면 방장이 게임을 시작할 수 있어요.");
            }
            StringBuilder sb = new StringBuilder();
            for (Model.Member s : r.spectators) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.name);
            }
            specs.setText(sb.length() == 0 ? "" : "관전: " + sb);
            action.revalidate();
            revalidate();
        }

        /** 자리 카드들 */
        private final class Seats extends JComponent {
            private static final long serialVersionUID = 1L;
            private final List<Object[]> hits = new ArrayList<>(); // {Rectangle2D, "kick", id}
            private Object[] hover;

            Seats() {
                MouseAdapter m = new MouseAdapter() {
                    @Override
                    public void mouseMoved(MouseEvent e) {
                        Object[] h = hitAt(e);
                        if (h != hover) {
                            hover = h;
                            setCursor(java.awt.Cursor.getPredefinedCursor(h != null ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
                            repaint();
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = null;
                        repaint();
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        Object[] h = hitAt(e);
                        if (h == null || room == null) return;
                        if ("kick".equals(h[1])) {
                            Model.Member mm = room.member((Integer) h[2]);
                            if (mm == null) return;
                            Modal.confirm(app, mm.name + "님을 내보낼까요?", "내보낸 사람은 이 방에 다시 들어올 수 없어요.", "내보내기", true,
                                    () -> app.send(Proto.KICK, mm.id));
                        }
                    }
                };
                addMouseListener(m);
                addMouseMotionListener(m);
            }

            private Object[] hitAt(MouseEvent e) {
                for (Object[] h : hits) if (((Rectangle2D) h[0]).contains(e.getPoint())) return h;
                return null;
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(600, 250);
            }

            @Override
            protected void paintComponent(Graphics g0) {
                if (room == null) return;
                Graphics2D g = UI.g(g0);
                hits.clear();
                int n = room.max;
                double gap = 16;
                double cw = Math.min(210, (getWidth() - gap * (n - 1)) / n);
                double total = cw * n + gap * (n - 1);
                double x0 = (getWidth() - total) / 2;
                double ch = Math.min(getHeight() - 8, 250);
                double y = (getHeight() - ch) / 2;
                boolean host = room.hostId == app.myId;
                for (int i = 0; i < n; i++) {
                    double x = x0 + i * (cw + gap);
                    Model.Member m = i < room.members.size() ? room.members.get(i) : null;
                    if (m == null) paintEmpty(g, x, y, cw, ch);
                    else paintMember(g, m, x, y, cw, ch, host);
                }
                g.dispose();
            }

            private void paintEmpty(Graphics2D g, double x, double y, double w, double h) {
                g.setColor(UI.a(Color.WHITE, 40));
                g.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND, 1f, new float[] { 7, 7 }, 0));
                g.draw(UI.rr(x + 1, y + 1, w - 2, h - 2, 20));
                g.setStroke(new java.awt.BasicStroke(1f));
                Icons.paint(g, "users", x + w / 2 - 14, y + h / 2 - 40, 28, UI.a(Theme.TEXT_3, 150));
                UI.center(g, "빈자리", Theme.bold(16), Theme.TEXT_3, x + w / 2, y + h / 2 + 4);
                UI.center(g, "친구를 기다리는 중", Theme.font(13), UI.a(Theme.TEXT_3, 170), x + w / 2, y + h / 2 + 28);
            }

            private void paintMember(Graphics2D g, Model.Member m, double x, double y, double w, double h, boolean host) {
                boolean isHost = m.id == room.hostId;
                boolean me = m.id == app.myId;
                UI.shadow(g, x, y, w, h, 20, 12, 110, 5);
                g.setColor(Theme.SURFACE);
                g.fill(UI.rr(x, y, w, h, 20));
                g.setColor(me ? UI.a(Theme.CORAL, 160) : Theme.STROKE);
                g.draw(UI.rr(x + 0.5, y + 0.5, w - 1, h - 1, 20));
                // 카드 높이에 맞춰 캐릭터 크기와 줄 간격을 정한다 (작은 창에서도 겹치지 않게)
                double ad = Math.max(34, Math.min(Math.min(90, w * 0.48), h - 96));
                double total = ad + 78;
                double ax = x + (w - ad) / 2, ay = y + Math.max(10, (h - total) / 2);
                Art.avatar(g, ax, ay, ad, m.avatar, false);
                if (isHost) {
                    double cs = Math.max(20, ad * 0.33);
                    g.setColor(Theme.AMBER);
                    g.fill(new java.awt.geom.Ellipse2D.Double(ax + ad - cs * 0.8, ay - cs * 0.2, cs, cs));
                    Icons.paint(g, "crown", ax + ad - cs * 0.8 + cs * 0.18, ay - cs * 0.2 + cs * 0.17, cs * 0.64, new Color(0x3A2A00));
                }
                String name = UI.ellipsize(m.name + (me ? " (나)" : ""), Theme.bold(16), (int) w - 20);
                UI.center(g, name, Theme.bold(16), Theme.TEXT, x + w / 2, ay + ad + 18);
                String st;
                Color sc;
                if (!m.connected) {
                    st = "연결 끊김";
                    sc = Theme.RED;
                } else if (isHost) {
                    st = "방장";
                    sc = Theme.AMBER;
                } else if (m.ready) {
                    st = "준비 완료";
                    sc = Theme.MINT;
                } else {
                    st = "준비 중";
                    sc = Theme.TEXT_3;
                }
                double pw = UI.width(Theme.bold(13), st) + 26;
                double pillY = ay + ad + 40;
                g.setColor(UI.a(sc, 34));
                g.fill(UI.rr(x + (w - pw) / 2, pillY, pw, 28, 14));
                UI.center(g, st, Theme.bold(13), sc, x + w / 2, pillY + 14);
                if (host && !me) {
                    Rectangle2D r = new Rectangle2D.Double(x + w - 38, y + 10, 28, 28);
                    boolean hv = hover != null && "kick".equals(hover[1]) && r.equals(hover[0]);
                    g.setColor(hv ? UI.a(Theme.RED, 70) : UI.a(Color.WHITE, 16));
                    g.fill(new java.awt.geom.Ellipse2D.Double(r.getX(), r.getY(), 28, 28));
                    Icons.paint(g, "close", r.getX() + 6, r.getY() + 6, 16, hv ? Theme.RED : Theme.TEXT_3);
                    hits.add(new Object[] { r, "kick", m.id });
                }
            }
        }
    }
}
