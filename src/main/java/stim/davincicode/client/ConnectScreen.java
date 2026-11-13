package stim.davincicode.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import stim.davincicode.client.ui.Btn;
import stim.davincicode.client.ui.Controls;
import stim.davincicode.client.ui.Field;
import stim.davincicode.client.ui.Icons;
import stim.davincicode.client.ui.Spinner;
import stim.davincicode.client.ui.Theme;
import stim.davincicode.client.ui.Toast;
import stim.davincicode.client.ui.UI;
import stim.davincicode.common.Proto;

/** 어디서 할지 고르는 화면: 같은 Wi-Fi 서버 / 내 PC에서 열기 / 주소로 접속 / (있으면) 공식 서버 */
final class ConnectScreen extends JPanel implements App.Screen {
    private static final long serialVersionUID = 1L;
    private final App app;
    private final JPanel lanList = UI.panel(null);
    private final Field host = new Field("예: 192.168.0.12 또는 play.example.com").icon("server").limit(80);
    private final Field port = new Field(String.valueOf(Proto.DEFAULT_PORT)).limit(5);
    private final JPanel recentRow = UI.panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel officialSlot = UI.panel(new BorderLayout());
    private Timer scanTimer;
    private volatile boolean scanning;
    private boolean firstScanDone;

    ConnectScreen(App app) {
        super(new BorderLayout());
        this.app = app;
        setOpaque(false);

        TopBar bar = new TopBar();
        Btn back = Btn.icon("back", "처음으로");
        back.addActionListener(e -> app.show("title"));
        bar.addLeft(back);
        bar.addCenter(UI.label("온라인 대전", Theme.bold(20), Theme.TEXT));
        add(bar, BorderLayout.NORTH);

        JPanel body = UI.panel(new GridBagLayout());
        JPanel cols = UI.panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 10, 0, 10);
        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 1;
        cols.add(lanCard(), c);
        c.gridx = 1;
        JPanel right = UI.panel(null);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(officialSlot);
        right.add(hostCard());
        right.add(Box.createVerticalStrut(18));
        right.add(addressCard());
        right.add(Box.createVerticalGlue());
        cols.add(right, c);
        body.add(cols);
        add(body, BorderLayout.CENTER);
    }

    private JComponent header(String icon, String title, String desc) {
        JPanel p = UI.panel(new BorderLayout(14, 0));
        JComponent badge = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(44, 44);
            }

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = UI.g(g0);
                g.setColor(UI.a(Theme.CORAL, 36));
                g.fill(UI.rr(0, 0, 44, 44, 12));
                Icons.paint(g, icon, 11, 11, 22, Theme.CORAL);
                g.dispose();
            }
        };
        JPanel bp = UI.panel(new BorderLayout());
        bp.add(badge, BorderLayout.NORTH);
        p.add(bp, BorderLayout.WEST);
        JPanel t = UI.panel(null);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.add(UI.label(title, Theme.bold(18), Theme.TEXT));
        t.add(Box.createVerticalStrut(4));
        t.add(UI.paragraph(desc, Theme.font(14), Theme.TEXT_2, 330));
        for (java.awt.Component x : t.getComponents()) ((JComponent) x).setAlignmentX(0f);
        p.add(t, BorderLayout.CENTER);
        return p;
    }

    private JComponent lanCard() {
        Controls.Card card = new Controls.Card(new BorderLayout(0, 16)) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(470, 520);
            }
        };
        card.setBorder(UI.pad(24, 24, 24, 24));
        JPanel head = UI.panel(new BorderLayout());
        head.add(header("wifi", "같은 Wi-Fi에서 찾기", "친구가 '내 PC에서 서버 열기'를 누르면 여기에 자동으로 나타나요."), BorderLayout.CENTER);
        Btn refresh = Btn.icon("refresh", "다시 찾기");
        refresh.addActionListener(e -> scan());
        JPanel rp = UI.panel(new BorderLayout());
        rp.add(refresh, BorderLayout.NORTH);
        head.add(rp, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        lanList.setLayout(new BoxLayout(lanList, BoxLayout.Y_AXIS));
        JPanel wrap = UI.panel(new BorderLayout());
        wrap.add(lanList, BorderLayout.NORTH);
        card.add(Controls.scroll(wrap), BorderLayout.CENTER);
        showScanning();
        return card;
    }

    private JComponent hostCard() {
        Controls.Card card = new Controls.Card(new BorderLayout(0, 18));
        card.setBorder(UI.pad(24, 24, 24, 24));
        card.add(header("monitor", "내 PC에서 서버 열기", "이 PC가 서버가 돼요. 같은 Wi-Fi의 친구들이 바로 들어올 수 있어요."), BorderLayout.CENTER);
        Btn open = new Btn("서버 열고 들어가기", Btn.Kind.PRIMARY).icon("play");
        open.addActionListener(e -> app.hostLocal());
        JPanel b = UI.panel(new BorderLayout());
        b.add(open, BorderLayout.CENTER);
        card.add(b, BorderLayout.SOUTH);
        card.setMaximumSize(new Dimension(470, 400));
        card.setPreferredSize(new Dimension(470, 190));
        card.setAlignmentX(0f);
        return card;
    }

    private JComponent addressCard() {
        Controls.Card card = new Controls.Card(new BorderLayout(0, 14));
        card.setBorder(UI.pad(24, 24, 24, 24));
        card.add(header("server", "주소로 접속", "서버 주소(IP 또는 도메인)와 포트를 알고 있다면 직접 입력하세요."), BorderLayout.NORTH);
        JPanel form = UI.panel(new BorderLayout(10, 10));
        port.setPreferredSize(new Dimension(100, 46));
        JPanel line = UI.panel(new BorderLayout(10, 0));
        line.add(host, BorderLayout.CENTER);
        line.add(port, BorderLayout.EAST);
        form.add(line, BorderLayout.NORTH);
        form.add(recentRow, BorderLayout.CENTER);
        Btn go = new Btn("접속하기", Btn.Kind.SECONDARY).icon("next");
        go.addActionListener(e -> submit());
        host.addActionListener(e -> submit());
        port.addActionListener(e -> submit());
        form.add(go, BorderLayout.SOUTH);
        card.add(form, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(470, 400));
        card.setPreferredSize(new Dimension(470, 270));
        card.setAlignmentX(0f);
        return card;
    }

    private void submit() {
        String h = host.getText().trim().replaceFirst("^[a-zA-Z]+://", "");
        if (h.contains("/")) h = h.substring(0, h.indexOf('/'));
        String p = port.getText().trim();
        if (h.contains(":") && (p.isEmpty() || p.equals(String.valueOf(Proto.DEFAULT_PORT)))) {
            p = h.substring(h.lastIndexOf(':') + 1);
            h = h.substring(0, h.lastIndexOf(':'));
        }
        if (h.isEmpty()) {
            host.setError(true);
            app.toast("서버 주소를 입력해 주세요.", Toast.Kind.WARN);
            return;
        }
        host.setError(false);
        int portNum;
        try {
            portNum = p.isEmpty() ? Proto.DEFAULT_PORT : Integer.parseInt(p);
            if (portNum < 1 || portNum > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            app.toast("포트는 1~65535 사이 숫자여야 해요.", Toast.Kind.WARN);
            return;
        }
        app.connectOnline(h, portNum);
    }

    private void fillRecent() {
        recentRow.removeAll();
        for (String r : app.prefs.recent()) {
            Btn b = new Btn(r, Btn.Kind.OUTLINE).small().accent(Theme.SKY);
            b.addActionListener(e -> {
                int i = r.lastIndexOf(':');
                host.setText(r.substring(0, i));
                port.setText(r.substring(i + 1));
                submit();
            });
            recentRow.add(b);
            if (recentRow.getComponentCount() >= 3) break;
        }
        recentRow.revalidate();
        recentRow.repaint();
    }

    private void fillOfficial() {
        officialSlot.removeAll();
        String off = app.officialServer();
        if (off != null) {
            Controls.Card card = new Controls.Card(new BorderLayout(14, 0));
            card.setBorder(UI.pad(20, 24, 20, 24));
            card.add(header("star", "공식 서버", "언제든 접속해서 다른 사람들과 대전해요."), BorderLayout.CENTER);
            Btn go = new Btn("접속", Btn.Kind.PRIMARY);
            go.addActionListener(e -> {
                int i = off.lastIndexOf(':');
                if (i > 0) app.connectOnline(off.substring(0, i), Integer.parseInt(off.substring(i + 1)));
                else app.connectOnline(off, Proto.DEFAULT_PORT);
            });
            JPanel gp = UI.panel(new GridBagLayout());
            gp.add(go);
            card.add(gp, BorderLayout.EAST);
            officialSlot.add(card, BorderLayout.CENTER);
            officialSlot.add(Box.createVerticalStrut(18), BorderLayout.SOUTH);
        }
        officialSlot.setMaximumSize(new Dimension(470, officialSlot.getPreferredSize().height));
        officialSlot.setAlignmentX(0f);
        officialSlot.revalidate();
    }

    @Override
    public void onShow() {
        fillRecent();
        fillOfficial();
        if (host.getText().isEmpty() && !app.prefs.recent().isEmpty()) {
            String r = app.prefs.recent().get(0);
            int i = r.lastIndexOf(':');
            host.setText(r.substring(0, i));
            port.setText(r.substring(i + 1));
        }
        scan();
        scanTimer = new Timer(4000, e -> scan());
        scanTimer.start();
    }

    @Override
    public void onHide() {
        if (scanTimer != null) scanTimer.stop();
    }

    private void showScanning() {
        lanList.removeAll();
        JPanel p = UI.panel(new FlowLayout(FlowLayout.LEFT, 12, 18));
        p.add(new Spinner(26));
        p.add(UI.label("같은 네트워크의 서버를 찾는 중…", Theme.font(14), Theme.TEXT_2));
        p.setAlignmentX(0f);
        lanList.add(p);
        lanList.revalidate();
    }

    private void scan() {
        if (scanning) return;
        scanning = true;
        new Thread(() -> {
            List<LanFinder.Found> list = LanFinder.scan(900);
            SwingUtilities.invokeLater(() -> {
                scanning = false;
                firstScanDone = true;
                showResults(list);
            });
        }, "dc-lan-scan").start();
    }

    private void showResults(List<LanFinder.Found> list) {
        lanList.removeAll();
        if (list.isEmpty()) {
            JPanel p = UI.panel(new BorderLayout(0, 8));
            p.setBorder(UI.pad(18, 4, 0, 4));
            p.add(UI.label(firstScanDone ? "아직 찾은 서버가 없어요" : "찾는 중…", Theme.bold(15), Theme.TEXT), BorderLayout.NORTH);
            p.add(UI.paragraph("친구가 서버를 열었는지, 같은 Wi-Fi에 연결돼 있는지 확인해 보세요. "
                    + "학교·회사 네트워크는 기기끼리 찾기를 막아 둘 수 있어요. 그럴 땐 '주소로 접속'에 친구 PC의 IP를 입력하세요.",
                    Theme.font(13), Theme.TEXT_3, 400), BorderLayout.CENTER);
            p.setAlignmentX(0f);
            lanList.add(p);
        }
        for (LanFinder.Found f : list) {
            lanList.add(new ServerRow(f));
            lanList.add(Box.createVerticalStrut(10));
        }
        lanList.revalidate();
        lanList.repaint();
    }

    private final class ServerRow extends JPanel {
        private static final long serialVersionUID = 1L;

        ServerRow(LanFinder.Found f) {
            super(new BorderLayout(12, 0));
            setOpaque(false);
            setBorder(UI.pad(12, 14, 12, 12));
            setAlignmentX(0f);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            JPanel t = UI.panel(null);
            t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
            t.add(UI.label(UI.ellipsize(f.name, Theme.bold(15), 230), Theme.bold(15), Theme.TEXT));
            t.add(Box.createVerticalStrut(3));
            String sub = (f.local ? "이 PC" : f.host) + ":" + f.port + "  ·  " + f.users + "명 접속 중";
            if (!f.compatible) sub = "버전이 달라요 (서버 " + f.version + ")";
            t.add(UI.label(sub, Theme.font(13), f.compatible ? Theme.TEXT_3 : Theme.RED));
            add(t, BorderLayout.CENTER);
            Btn join = new Btn("들어가기", Btn.Kind.PRIMARY).small();
            join.setEnabled(f.compatible);
            join.addActionListener(e -> app.connectOnline(f.host, f.port));
            JPanel jp = UI.panel(new GridBagLayout());
            jp.add(join);
            add(jp, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            g.setColor(Theme.SURFACE_LO);
            g.fill(UI.rr(0, 0, getWidth(), getHeight(), 14));
            g.setColor(Theme.STROKE);
            g.draw(UI.rr(0.5, 0.5, getWidth() - 1, getHeight() - 1, 14));
            g.dispose();
        }
    }
}
