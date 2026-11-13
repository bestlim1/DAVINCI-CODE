package stim.davincicode.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

import stim.davincicode.client.ui.Art;
import stim.davincicode.client.ui.Btn;
import stim.davincicode.client.ui.Controls;
import stim.davincicode.client.ui.Field;
import stim.davincicode.client.ui.Modal;
import stim.davincicode.client.ui.Theme;
import stim.davincicode.client.ui.Toast;
import stim.davincicode.client.ui.UI;
import stim.davincicode.common.Names;
import stim.davincicode.common.Proto;

/**
 * 채팅 창 (로비·방 공용). 말풍선 목록, 빠른 채팅, 귓속말(/w 닉네임 내용), 차단·신고, 방에서는 리액션과 게임 기록 탭.
 */
final class ChatPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    static final String[] PHRASES = { "안녕하세요! 잘 부탁해요", "나이스!", "아깝다…", "잠시만요!", "한 판 더 할까요?", "GG! 즐거웠어요" };
    static final String[] REACTIONS = { "굿!", "ㅋㅋㅋ", "헉!", "아깝다", "고마워", "GG" };
    static final Color[] REACTION_COLORS = { Theme.MINT, Theme.AMBER, Theme.SKY, Theme.VIOLET, new Color(0xFF7EB6), Theme.CORAL };

    private final App app;
    private final boolean roomMode;
    private final ChatView chat = new ChatView();
    private final LogView log = new LogView();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = UI.panel(cards);
    private final Field input = new Field("메시지 입력").limit(Names.CHAT_MAX);
    private final Tabs tabs;
    private final JScrollPane chatScroll;
    private final JScrollPane logScroll;

    ChatPanel(App app, String title, boolean roomMode) {
        super(new BorderLayout());
        this.app = app;
        this.roomMode = roomMode;
        setOpaque(false);
        setBorder(UI.pad(14, 14, 14, 14));

        tabs = new Tabs(roomMode ? new String[] { "채팅", "게임 기록" } : new String[] { title });
        add(tabs, BorderLayout.NORTH);
        chatScroll = Controls.scroll(chat);
        logScroll = Controls.scroll(log);
        body.add(chatScroll, "chat");
        body.add(logScroll, "log");
        body.setBorder(UI.pad(8, 0, 8, 0));
        add(body, BorderLayout.CENTER);

        JPanel bottom = UI.panel(new BorderLayout(0, 8));
        if (roomMode) {
            JPanel reacts = UI.panel(new java.awt.GridLayout(1, REACTIONS.length, 5, 0));
            for (int i = 0; i < REACTIONS.length; i++) {
                int code = i;
                Btn b = new Btn(REACTIONS[i], Btn.Kind.OUTLINE).tiny().accent(REACTION_COLORS[i]);
                b.setToolTipText("리액션 보내기");
                b.addActionListener(e -> app.send(Proto.REACT, code));
                reacts.add(b);
            }
            bottom.add(reacts, BorderLayout.NORTH);
        }
        JPanel line = UI.panel(new BorderLayout(8, 0));
        Btn quick = Btn.icon("zap", "빠른 채팅");
        quick.setPreferredSize(new Dimension(46, 46));
        quick.addActionListener(e -> {
            Object[] items = new Object[PHRASES.length * 3];
            for (int i = 0; i < PHRASES.length; i++) {
                String p = PHRASES[i];
                items[i * 3] = "chat";
                items[i * 3 + 1] = p;
                items[i * 3 + 2] = (Runnable) () -> app.send(Proto.CHAT, p);
            }
            Modal.Popover.show(quick, Modal.Popover.menu(items), true);
        });
        Btn send = new Btn("", Btn.Kind.PRIMARY).icon("send");
        send.setPreferredSize(new Dimension(50, 46));
        send.setToolTipText("보내기 (Enter)");
        send.addActionListener(e -> submit());
        input.addActionListener(e -> submit());
        input.setToolTipText("귓속말: /w 닉네임 내용");
        line.add(quick, BorderLayout.WEST);
        line.add(input, BorderLayout.CENTER);
        line.add(send, BorderLayout.EAST);
        bottom.add(line, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    private void submit() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        if (text.startsWith("/w ") || text.startsWith("/귓 ")) {
            String[] p = text.split(" ", 3);
            if (p.length < 3 || p[2].trim().isEmpty()) {
                app.toast("귓속말은 '/w 닉네임 내용' 처럼 써 주세요.", Toast.Kind.WARN);
                return;
            }
            app.send(Proto.WHISPER, p[1], p[2]);
        } else {
            app.send(Proto.CHAT, text);
        }
        input.setText("");
    }

    void whisperTo(String name) {
        input.setText("/w " + name + " ");
        input.requestFocusInWindow();
    }

    void clear() {
        chat.items.clear();
        chat.relayout();
        log.items.clear();
        log.relayout();
        tabs.unread = false;
    }

    void addChat(int senderId, String name, int avatar, String text, boolean spectator) {
        ChatView.Item it = new ChatView.Item();
        it.kind = 0;
        it.senderId = senderId;
        it.name = name;
        it.avatar = avatar;
        it.text = text;
        it.mine = senderId == app.myId;
        it.spectator = spectator;
        chat.add(it);
    }

    void addSystem(String text) {
        ChatView.Item it = new ChatView.Item();
        it.kind = 1;
        it.text = text;
        chat.add(it);
    }

    void addWhisper(String from, String to, int avatar, String text, String me) {
        ChatView.Item it = new ChatView.Item();
        it.kind = 2;
        it.name = from;
        it.to = to;
        it.avatar = avatar;
        it.text = text;
        it.mine = from.equals(me);
        chat.add(it);
    }

    void addLog(String text, Color color) {
        log.add(text, color);
        if (tabs.selected != 1 && roomMode) {
            tabs.unread = true;
            tabs.repaint();
        }
    }

    // ================================================================ 탭

    private final class Tabs extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String[] names;
        int selected;
        boolean unread;

        Tabs(String[] names) {
            this.names = names;
            if (names.length > 1) UI.hand(this);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (names.length < 2) return;
                    selected = e.getX() < tabW() ? 0 : 1;
                    if (selected == 1) unread = false;
                    cards.show(body, selected == 0 ? "chat" : "log");
                    repaint();
                }
            });
        }

        private int tabW() {
            return 104;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, 34);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            for (int i = 0; i < names.length; i++) {
                double x = i * tabW();
                boolean sel = i == selected;
                UI.text(g, names[i], Theme.bold(16), sel ? Theme.TEXT : Theme.TEXT_3, x, 4);
                if (sel && names.length > 1) {
                    g.setColor(Theme.CORAL);
                    g.fill(UI.rr(x, 30, UI.width(Theme.bold(16), names[i]), 3, 1.5));
                }
                if (i == 1 && unread) {
                    g.setColor(Theme.CORAL);
                    g.fill(new java.awt.geom.Ellipse2D.Double(x + UI.width(Theme.bold(16), names[i]) + 5, 6, 7, 7));
                }
            }
            g.setColor(Theme.STROKE);
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g.dispose();
        }
    }

    // ================================================================ 말풍선 목록

    private final class ChatView extends JComponent implements Scrollable {
        private static final long serialVersionUID = 1L;
        private static final int PAD = 4, AV = 30, LINE = 20;

        static final class Item {
            int kind; // 0 채팅, 1 시스템, 2 귓속말
            int senderId;
            String name, to, text;
            int avatar;
            boolean mine, spectator;
            List<String> lines = new ArrayList<>();
            int height;
            int y;
            Rectangle hit = new Rectangle();
        }

        final List<Item> items = new ArrayList<>();
        private int laidW = -1;
        private int total;

        ChatView() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isRightMouseButton(e) && e.getClickCount() < 2) return;
                    for (Item it : items) {
                        if ((it.kind == 0 || it.kind == 2) && !it.mine && it.hit.contains(e.getPoint()) && it.senderId >= 0) {
                            userMenu(it.name, e);
                            return;
                        }
                    }
                }
            });
        }

        private void userMenu(String name, MouseEvent e) {
            Modal.Popover.showAt(this, e.getLocationOnScreen(), Modal.Popover.menu(
                    "chat", name + "님에게 귓속말", (Runnable) () -> whisperTo(name),
                    "block", "차단하기", (Runnable) () -> Modal.confirm(app, name + "님을 차단할까요?",
                            "차단하면 이 사람의 채팅과 귓속말이 보이지 않아요. 설정에서 언제든 풀 수 있어요.", "차단", true, () -> {
                                app.prefs.block(name, true);
                                app.toast(name + "님을 차단했어요.", Toast.Kind.INFO);
                            }),
                    "flag", "신고하기", (Runnable) () -> Dialogs.report(app, name)));
        }

        void add(Item it) {
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            boolean atBottom = true;
            if (sp != null) {
                JScrollBar bar = sp.getVerticalScrollBar();
                atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 40;
            }
            items.add(it);
            if (items.size() > 300) items.remove(0);
            relayout();
            if ((atBottom || it.mine) && sp != null) Controls.scrollToBottom(sp);
        }

        void relayout() {
            int w = Math.max(200, getWidth());
            laidW = getWidth();
            int maxText = (int) Math.min(w * 0.74, w - AV - 40) - 20;
            int y = 6;
            for (Item it : items) {
                it.y = y;
                if (it.kind == 1) {
                    it.lines = UI.wrap(it.text, Theme.font(13), w - 60);
                    it.height = it.lines.size() * 18 + 12;
                } else {
                    it.lines = UI.wrap(it.text, Theme.font(14), maxText);
                    int head = (it.mine && it.kind == 0) ? 0 : 18;
                    it.height = head + it.lines.size() * LINE + 16;
                }
                y += it.height + 10;
            }
            total = y;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, total);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (getWidth() != laidW) {
                relayout();
            }
            Graphics2D g = UI.g(g0);
            Rectangle clip = g.getClipBounds();
            int w = getWidth();
            if (items.isEmpty()) {
                UI.center(g, "아직 대화가 없어요. 먼저 인사해 보세요!", Theme.font(13), Theme.TEXT_3, w / 2.0, 40);
            }
            for (Item it : items) {
                if (clip != null && (it.y > clip.y + clip.height || it.y + it.height < clip.y)) continue;
                if (it.kind == 1) paintSys(g, it, w);
                else paintBubble(g, it, w);
            }
            g.dispose();
        }

        private void paintSys(Graphics2D g, Item it, int w) {
            double bw = 0;
            for (String l : it.lines) bw = Math.max(bw, UI.width(Theme.font(13), l));
            bw += 24;
            double x = (w - bw) / 2;
            g.setColor(UI.a(Theme.SURFACE_HI, 160));
            g.fill(UI.rr(x, it.y, bw, it.height, 10));
            double ty = it.y + 6;
            for (String l : it.lines) {
                UI.center(g, l, Theme.font(13), Theme.TEXT_2, w / 2.0, ty + 9);
                ty += 18;
            }
        }

        private void paintBubble(Graphics2D g, Item it, int w) {
            boolean whisper = it.kind == 2;
            double tw = 0;
            for (String l : it.lines) tw = Math.max(tw, UI.width(Theme.font(14), l));
            double bw = tw + 22;
            double bh = it.lines.size() * LINE + 14;
            double by = it.y + ((it.mine && !whisper) ? 0 : 18);
            double bx;
            Color fill, ink = Theme.TEXT;
            if (it.mine) {
                bx = w - PAD - bw;
                fill = whisper ? new Color(0x4B3A86) : Theme.CORAL_DEEP;
                ink = Color.WHITE;
                if (whisper) UI.text(g, "귓속말 → " + it.to, Theme.bold(12), Theme.VIOLET, w - PAD - UI.width(Theme.bold(12), "귓속말 → " + it.to), it.y);
            } else {
                bx = PAD + AV + 10;
                Art.avatar(g, PAD, it.y + 4, AV, it.avatar, false);
                String head = whisper ? it.name + " → 나 (귓속말)" : it.name + (it.spectator ? " · 관전" : "");
                UI.text(g, head, Theme.bold(12), whisper ? Theme.VIOLET : UI.mix(Theme.avatar(it.avatar), Color.WHITE, 0.25), bx + 2, it.y);
                fill = whisper ? new Color(0x2E2752) : Theme.SURFACE_HI;
            }
            g.setColor(fill);
            g.fill(UI.rr(bx, by, bw, bh, 12));
            if (whisper) {
                g.setColor(UI.a(Theme.VIOLET, 120));
                g.draw(UI.rr(bx + 0.5, by + 0.5, bw - 1, bh - 1, 12));
            }
            double ty = by + 7;
            for (String l : it.lines) {
                UI.text(g, l, Theme.font(14), ink, bx + 11, ty + (LINE - UI.fm(Theme.font(14)).getHeight()) / 2.0);
                ty += LINE;
            }
            it.hit.setBounds((int) (it.mine ? bx : PAD), it.y, (int) (it.mine ? bw : bx + bw), it.height);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 20;
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
            return false;
        }
    }

    // ================================================================ 게임 기록

    private static final class LogView extends JComponent implements Scrollable {
        private static final long serialVersionUID = 1L;
        private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

        static final class Entry {
            String text, time;
            Color color;
            List<String> lines;
            int y, h;
        }

        final List<Entry> items = new ArrayList<>();
        private int laidW = -1, total;

        void add(String text, Color color) {
            Entry e = new Entry();
            e.text = text;
            e.color = color;
            e.time = LocalTime.now().format(HM);
            items.add(e);
            if (items.size() > 400) items.remove(0);
            relayout();
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (sp != null) Controls.scrollToBottom(sp);
        }

        void relayout() {
            laidW = getWidth();
            int w = Math.max(200, getWidth()) - 70;
            int y = 4;
            for (Entry e : items) {
                e.lines = UI.wrap(e.text, Theme.font(13), w);
                e.y = y;
                e.h = e.lines.size() * 18 + 10;
                y += e.h;
            }
            total = y;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, total);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (laidW != getWidth()) relayout();
            Graphics2D g = UI.g(g0);
            if (items.isEmpty()) UI.center(g, "게임이 시작되면 여기에 모든 추리가 기록돼요.", Theme.font(13), Theme.TEXT_3, getWidth() / 2.0, 40);
            for (Entry e : items) {
                g.setColor(e.color);
                g.fill(new java.awt.geom.Ellipse2D.Double(4, e.y + 9, 7, 7));
                UI.text(g, e.time, Theme.font(11), Theme.TEXT_3, 18, e.y + 6);
                double ty = e.y + 4;
                for (String l : e.lines) {
                    UI.text(g, l, Theme.font(13), Theme.TEXT, 58, ty);
                    ty += 18;
                }
            }
            g.dispose();
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height - 30;
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

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = UI.g(g0);
        g.setColor(UI.a(Theme.SURFACE, 230));
        g.fill(UI.rr(0, 0, getWidth(), getHeight(), 18));
        g.setColor(Theme.STROKE);
        g.draw(UI.rr(0.5, 0.5, getWidth() - 1, getHeight() - 1, 18));
        g.dispose();
    }
}
