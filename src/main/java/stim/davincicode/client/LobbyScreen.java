package stim.davincicode.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;

import stim.davincicode.client.ui.Art;
import stim.davincicode.client.ui.Btn;
import stim.davincicode.client.ui.Controls;
import stim.davincicode.client.ui.Icons;
import stim.davincicode.client.ui.Modal;
import stim.davincicode.client.ui.Theme;
import stim.davincicode.client.ui.Toast;
import stim.davincicode.client.ui.UI;
import stim.davincicode.common.Msg;
import stim.davincicode.common.Proto;

/** 로비: 방 목록, 빠른 시작, 방 만들기, 접속자 목록, 로비 채팅 */
final class LobbyScreen extends JPanel implements App.Screen {
    private static final long serialVersionUID = 1L;
    private final App app;
    final ChatPanel chat;
    private final RoomList roomList = new RoomList();
    private final UserList userList = new UserList();
    private final JLabel roomCount = UI.label("", Theme.bold(14), Theme.TEXT_3);
    private final JLabel userCount = UI.label("", Theme.bold(16), Theme.TEXT);
    private final TopBar.Pill serverPill = new TopBar.Pill("", Theme.MINT, "server");
    private final TopBar.Pill pingPill = new TopBar.Pill("", Theme.TEXT_3, "wifi");
    private final TopBar.Pill hostPill = new TopBar.Pill("", Theme.CORAL, "monitor");
    private final Me me = new Me();
    private final Controls.Segmented filter = new Controls.Segmented(0, "전체", "대기 중", "빈자리").compact();
    private List<Model.LobbyRoom> rooms = new ArrayList<>();
    private List<Model.LobbyUser> users = new ArrayList<>();

    LobbyScreen(App app) {
        super(new BorderLayout());
        this.app = app;
        setOpaque(false);
        chat = new ChatPanel(app, "로비 채팅", false);

        TopBar bar = new TopBar();
        bar.addLeft(new MiniLogo());
        bar.addCenter(serverPill);
        bar.addCenter(pingPill);
        bar.addCenter(hostPill);
        hostPill.setToolTipText("눌러서 초대 주소 복사 (브라우저 주소창에 넣으면 웹으로 바로 참가)");
        UI.hand(hostPill);
        hostPill.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String addr = hostAddress();
                if (addr != null) {
                    String url = "http://" + addr;
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
                    app.toast("초대 주소 " + url + " 를 복사했어요. 친구가 크롬 같은 브라우저 주소창에 붙여넣으면 설치 없이 바로 참가해요.", Toast.Kind.SUCCESS);
                }
            }
        });
        Btn how = Btn.icon("help", "게임 방법");
        how.addActionListener(e -> Dialogs.howTo(app));
        Btn set = Btn.icon("gear", "설정");
        set.addActionListener(e -> Dialogs.settings(app));
        Btn exit = Btn.icon("exit", "서버에서 나가기");
        exit.addActionListener(e -> {
            if (app.mode() == App.Mode.HOST) {
                Modal.confirm(app, "서버를 닫을까요?", "내 PC의 서버가 닫히고 접속한 친구들의 연결도 끊어져요.", "닫기", true, app::logout);
            } else {
                app.logout();
            }
        });
        bar.addRight(how);
        bar.addRight(set);
        bar.addRight(exit);
        bar.addRight(me);
        add(bar, BorderLayout.NORTH);

        JPanel body = UI.panel(new BorderLayout(18, 0));
        body.setBorder(UI.pad(20, 22, 22, 22));

        JPanel left = UI.panel(new BorderLayout(0, 14));
        JPanel head = UI.panel(new BorderLayout());
        JPanel titleRow = UI.panel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        titleRow.add(UI.label("게임 방", Theme.bold(24), Theme.TEXT));
        titleRow.add(roomCount);
        head.add(titleRow, BorderLayout.WEST);
        JPanel actions = UI.panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        filter.setPreferredSize(new Dimension(220, 40));
        filter.onChange(i -> roomList.refresh());
        Btn quick = new Btn("빠른 시작", Btn.Kind.MINT).icon("zap");
        quick.addActionListener(e -> app.send(Proto.QUICK));
        Btn create = new Btn("방 만들기", Btn.Kind.PRIMARY).icon("plus");
        create.addActionListener(e -> Dialogs.createRoom(app));
        actions.add(filter);
        actions.add(quick);
        actions.add(create);
        head.add(actions, BorderLayout.EAST);
        left.add(head, BorderLayout.NORTH);
        left.add(Controls.scroll(roomList), BorderLayout.CENTER);
        body.add(left, BorderLayout.CENTER);

        JPanel right = UI.panel(new BorderLayout(0, 14));
        right.setPreferredSize(new Dimension(370, 100));
        Controls.Card usersCard = new Controls.Card(new BorderLayout(0, 8));
        usersCard.setBorder(UI.pad(14, 14, 10, 14));
        usersCard.add(userCount, BorderLayout.NORTH);
        javax.swing.JScrollPane us = Controls.scroll(userList);
        us.setPreferredSize(new Dimension(300, 190));
        usersCard.add(us, BorderLayout.CENTER);
        right.add(usersCard, BorderLayout.NORTH);
        right.add(chat, BorderLayout.CENTER);
        body.add(right, BorderLayout.EAST);
        add(body, BorderLayout.CENTER);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int rw = getWidth() < 1250 ? 320 : 370;
                if (right.getPreferredSize().width != rw) {
                    right.setPreferredSize(new Dimension(rw, 100));
                    revalidate();
                }
            }
        });
    }

    private String hostAddress() {
        if (app.hostedServer() == null) return null;
        List<String> ips = LanFinder.localAddresses();
        return (ips.isEmpty() ? "127.0.0.1" : ips.get(0)) + ":" + app.hostedServer().port();
    }

    @Override
    public void onShow() {
        serverPill.set(app.serverName.isEmpty() ? "서버" : app.serverName, Theme.MINT);
        String addr = hostAddress();
        hostPill.setVisible(addr != null);
        if (addr != null) hostPill.set("내 서버 " + addr, Theme.CORAL);
        updatePing(app.ping());
        me.repaint();
    }

    void updatePing(long ms) {
        pingPill.set(TopBar.pingText(ms), TopBar.pingColor(ms));
    }

    void update(Msg m) {
        rooms = Model.parseRooms(m);
        users = Model.parseUsers(m);
        roomList.refresh();
        userList.refresh();
        userCount.setText("접속자 " + users.size() + "명");
        me.repaint();
    }

    private void join(Model.LobbyRoom r) {
        Runnable go = () -> {
            if (r.locked) {
                Modal.prompt(app, "비밀번호가 있는 방이에요", "'" + r.title + "' 방의 비밀번호를 입력해 주세요.", true,
                        pw -> app.send(r.playing ? Proto.SPECTATE : Proto.JOIN, r.id, pw));
            } else {
                app.send(r.playing ? Proto.SPECTATE : Proto.JOIN, r.id, "");
            }
        };
        if (!r.playing && r.count >= r.max) {
            app.toast("방이 꽉 찼어요. 다른 방을 골라 보세요.", Toast.Kind.WARN);
            return;
        }
        go.run();
    }

    // ================================================================ 방 목록

    private final class RoomList extends JComponent implements Scrollable {
        private static final long serialVersionUID = 1L;
        private static final int CARD_H = 122, GAP = 14;
        private final List<Model.LobbyRoom> shown = new ArrayList<>();
        private final List<Rectangle> rects = new ArrayList<>();
        private int hover = -1;

        RoomList() {
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int h = indexAt(e.getX(), e.getY());
                    if (h != hover) {
                        hover = h;
                        setCursor(java.awt.Cursor.getPredefinedCursor(h >= 0 ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = indexAt(e.getX(), e.getY());
                    if (i >= 0) join(shown.get(i));
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int indexAt(int x, int y) {
            for (int i = 0; i < rects.size(); i++) if (rects.get(i).contains(x, y)) return i;
            return -1;
        }

        void refresh() {
            shown.clear();
            int f = filter.getSelected();
            for (Model.LobbyRoom r : rooms) {
                if (f == 1 && r.playing) continue;
                if (f == 2 && (r.playing || r.count >= r.max)) continue;
                shown.add(r);
            }
            roomCount.setText(rooms.isEmpty() ? "" : rooms.size() + "개");
            revalidate();
            repaint();
        }

        private int cols() {
            return getWidth() >= 720 ? 2 : 1;
        }

        @Override
        public Dimension getPreferredSize() {
            int rows = (shown.size() + cols() - 1) / cols();
            return new Dimension(400, Math.max(300, rows * (CARD_H + GAP)));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            rects.clear();
            int w = getWidth();
            if (shown.isEmpty()) {
                paintEmpty(g, w);
                g.dispose();
                return;
            }
            int cols = cols();
            double cw = (w - GAP * (cols - 1) - 4) / (double) cols;
            for (int i = 0; i < shown.size(); i++) {
                int col = i % cols, row = i / cols;
                double x = 2 + col * (cw + GAP), y = row * (CARD_H + GAP) + 2;
                rects.add(new Rectangle((int) x, (int) y, (int) cw, CARD_H));
                paintCard(g, shown.get(i), x, y, cw, CARD_H, i == hover);
            }
            g.dispose();
        }

        private void paintEmpty(Graphics2D g, int w) {
            double cx = w / 2.0, cy = 120;
            String[] faces = { "?", "?", "?" };
            for (int i = 0; i < 3; i++) {
                Art.Look l = new Art.Look();
                l.color = i == 1 ? 'W' : 'B';
                l.back = true;
                l.face = faces[i];
                l.alpha = 0.8;
                Art.tile(g, cx - 66 + i * 46, cy - 40 + (i == 1 ? -8 : 0), 40, 56, l);
            }
            UI.center(g, "열려 있는 방이 없어요", Theme.bold(18), Theme.TEXT, cx, cy + 50);
            UI.center(g, "방을 만들거나 '빠른 시작'을 눌러 보세요. 친구가 들어오면 바로 시작할 수 있어요.", Theme.font(14), Theme.TEXT_3, cx, cy + 78);
        }

        private void paintCard(Graphics2D g, Model.LobbyRoom r, double x, double y, double w, double h, boolean hov) {
            if (hov) UI.shadow(g, x, y, w, h, 16, 10, 120, 5);
            g.setColor(hov ? Theme.SURFACE_HI : Theme.SURFACE);
            g.fill(UI.rr(x, y, w, h, 16));
            g.setColor(hov ? UI.a(Theme.CORAL, 160) : Theme.STROKE);
            g.draw(UI.rr(x + 0.5, y + 0.5, w - 1, h - 1, 16));

            double tx = x + 20;
            UI.text(g, "#" + r.id, Theme.bold(12), Theme.TEXT_3, tx, y + 16);
            double titleX = tx + UI.width(Theme.bold(12), "#" + r.id) + 8;
            if (r.locked) {
                Icons.paint(g, "lock", titleX, y + 15, 15, Theme.AMBER);
                titleX += 20;
            }
            String status = r.playing ? "게임 중" : r.count >= r.max ? "꽉 참" : "대기 중";
            Color sc = r.playing ? Theme.AMBER : r.count >= r.max ? Theme.TEXT_3 : Theme.MINT;
            double pillW = UI.width(Theme.bold(12), status) + 22;
            g.setColor(UI.a(sc, 36));
            g.fill(UI.rr(x + w - pillW - 16, y + 14, pillW, 24, 12));
            UI.center(g, status, Theme.bold(12), sc, x + w - pillW / 2 - 16, y + 26);

            String title = UI.ellipsize(r.title, Theme.bold(17), (int) (x + w - pillW - 30 - titleX));
            UI.text(g, title, Theme.bold(17), Theme.TEXT, titleX, y + 12);
            UI.text(g, "방장 " + r.host + (r.spectators > 0 ? "  ·  관전 " + r.spectators + "명" : ""), Theme.font(13), Theme.TEXT_3, tx, y + 42);

            double cx = tx, cy = y + h - 40;
            Controls.Chip.paint(g, Model.turnText(r.turnSec), Theme.SKY, "timer", cx, cy);

            // 인원 점
            double dx = x + w - 20 - r.max * 16;
            for (int i = 0; i < r.max; i++) {
                g.setColor(i < r.count ? Theme.CORAL : UI.a(Theme.TEXT, 40));
                g.fill(new java.awt.geom.Ellipse2D.Double(dx + i * 16, cy + 8, 10, 10));
            }
            String cnt = r.count + "/" + r.max;
            UI.text(g, cnt, Theme.bold(13), Theme.TEXT_2, dx - UI.width(Theme.bold(13), cnt) - 8, cy + 4);
            if (hov) {
                String act = r.playing ? "관전하기 →" : r.count >= r.max ? "자리 없음" : "입장하기 →";
                UI.text(g, act, Theme.bold(13), Theme.CORAL, x + w - 20 - UI.width(Theme.bold(13), act), y + 46);
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height - 40;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }

    // ================================================================ 접속자 목록

    private final class UserList extends JComponent implements Scrollable {
        private static final long serialVersionUID = 1L;
        private static final int ROW = 40;

        UserList() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int i = e.getY() / ROW;
                    if (i < 0 || i >= users.size()) return;
                    Model.LobbyUser u = users.get(i);
                    if (u.id == app.myId) return;
                    boolean blocked = app.prefs.isBlocked(u.name);
                    Modal.Popover.showAt(UserList.this, e.getLocationOnScreen(), Modal.Popover.menu(
                            "chat", "귓속말 보내기", (Runnable) () -> chat.whisperTo(u.name),
                            "block", blocked ? "차단 풀기" : "차단하기", (Runnable) () -> {
                                app.prefs.block(u.name, !blocked);
                                app.toast(blocked ? u.name + "님 차단을 풀었어요." : u.name + "님을 차단했어요.", Toast.Kind.INFO);
                            },
                            "flag", "신고하기", (Runnable) () -> Dialogs.report(app, u.name)));
                }
            });
            UI.hand(this);
        }

        void refresh() {
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(300, Math.max(ROW, users.size() * ROW));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            int w = getWidth();
            for (int i = 0; i < users.size(); i++) {
                Model.LobbyUser u = users.get(i);
                int y = i * ROW;
                if (u.id == app.myId) {
                    g.setColor(UI.a(Theme.CORAL, 22));
                    g.fill(UI.rr(0, y + 2, w - 4, ROW - 4, 10));
                }
                Art.avatar(g, 6, y + 6, 28, u.avatar, false);
                String n = u.name + (u.id == app.myId ? " (나)" : "");
                UI.text(g, UI.ellipsize(n, Theme.bold(14), w - 190), Theme.bold(14),
                        app.prefs.isBlocked(u.name) ? Theme.TEXT_3 : Theme.TEXT, 44, y + 10);
                String where = "L".equals(u.where) ? "로비" : "W".equals(u.where) ? "대기실" : "게임 중";
                Color wc = "L".equals(u.where) ? Theme.MINT : "W".equals(u.where) ? Theme.SKY : Theme.AMBER;
                double rx = w - 12 - UI.width(Theme.font(12), where);
                UI.text(g, where, Theme.font(12), wc, rx, y + 12);
            }
            g.dispose();
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(300, 190);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return ROW;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    void refreshMe() {
        me.revalidate();
        me.repaint();
        userList.repaint();
    }

    /** 위쪽 막대의 내 프로필 (캐릭터, 닉네임). 누르면 닉네임·캐릭터를 바꾸는 창이 뜬다 */
    private final class Me extends JComponent {
        private static final long serialVersionUID = 1L;
        private boolean hover;

        Me() {
            setToolTipText("눌러서 닉네임·캐릭터 바꾸기");
            UI.hand(this);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Dialogs.profileOnline(app);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Math.max(150, UI.width(Theme.bold(14), app.myName) + 96), 44);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            int w = getWidth(), h = 44;
            g.setColor(hover ? Theme.SURFACE_HI : UI.a(Theme.SURFACE, 160));
            g.fill(UI.rr(0, 0, w, h, h / 2.0));
            g.setColor(hover ? UI.a(Theme.CORAL, 150) : Theme.STROKE);
            g.draw(UI.rr(0.5, 0.5, w - 1, h - 1, h / 2.0));
            Art.avatar(g, 5, 5, 34, app.prefs.avatar(), false);
            UI.text(g, app.myName, Theme.bold(14), Theme.TEXT, 48, 5);
            UI.text(g, "프로필 바꾸기", Theme.font(11), hover ? Theme.CORAL : Theme.TEXT_3, 48, 24);
            Icons.paint(g, "pencil", w - 26, h / 2.0 - 7, 14, hover ? Theme.CORAL : Theme.TEXT_3);
            g.dispose();
        }
    }

    /** 왼쪽 위의 작은 로고 */
    static final class MiniLogo extends JComponent {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(196, 40);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            Art.Look a = new Art.Look();
            a.back = true;
            Art.tile(g, 2, 6, 20, 28, a);
            Art.Look b = new Art.Look();
            b.color = 'W';
            b.face = "7";
            Art.tile(g, 18, 8, 20, 28, b);
            java.awt.Font f = Theme.num(18);
            UI.text(g, "DAVINCI", f, Theme.TEXT, 48, 9);
            UI.text(g, "CODE", f, Theme.CORAL, 48 + UI.width(f, "DAVINCI") + 4, 9);
            g.dispose();
        }
    }
}
