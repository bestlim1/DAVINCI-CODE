package stim.davincicode.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import stim.davincicode.client.ui.Anim;
import stim.davincicode.client.ui.Art;
import stim.davincicode.client.ui.Btn;
import stim.davincicode.client.ui.Theme;
import stim.davincicode.client.ui.UI;
import stim.davincicode.common.Proto;

/** 첫 화면: 떠다니는 타일 배경, 로고, 시작 버튼들 */
final class TitleScreen extends JPanel implements App.Screen {
    private static final long serialVersionUID = 1L;

    private static final class Floater {
        double x, y, size, speed, rot, spin, alpha;
        char color;
        String face;
        boolean back;
    }

    private final App app;
    private final List<Floater> floaters = new ArrayList<>();
    private final ProfileChip chip;

    TitleScreen(App app) {
        super(new BorderLayout());
        this.app = app;
        setOpaque(false);
        Random r = new Random(11);
        for (int i = 0; i < 22; i++) {
            Floater f = new Floater();
            f.x = r.nextDouble();
            f.y = r.nextDouble();
            f.size = 26 + r.nextDouble() * 34;
            f.speed = 0.006 + r.nextDouble() * 0.012;
            f.rot = r.nextDouble() * Math.PI;
            f.spin = (r.nextDouble() - 0.5) * 0.25;
            f.alpha = 0.10 + r.nextDouble() * 0.16;
            f.color = r.nextBoolean() ? 'B' : 'W';
            f.back = r.nextInt(3) > 0;
            f.face = r.nextInt(9) == 0 ? "-" : String.valueOf(r.nextInt(12));
            floaters.add(f);
        }

        JPanel center = UI.panel(new GridBagLayout());
        JPanel col = UI.panel(null);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JComponent logo = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(560, 168);
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = UI.g(g0);
                Art.logo(g, getWidth() / 2.0, 6, 1.25);
                g.dispose();
            }
        };
        col.add(logo);
        JLabel tag = UI.label("상대의 숨겨진 숫자를 먼저 밝혀내는 추리 대결", Theme.font(17), Theme.TEXT_2);
        tag.setHorizontalAlignment(SwingConstants.CENTER);
        tag.setBorder(UI.pad(0, 10, 0, 10));
        col.add(Box.createVerticalStrut(6));
        col.add(tag);
        col.add(Box.createVerticalStrut(40));

        Btn online = new Btn("온라인 대전", Btn.Kind.PRIMARY).large().icon("users");
        Btn host = new Btn("내 PC에서 서버 열기", Btn.Kind.SECONDARY).large().icon("server");
        Btn how = new Btn("게임 방법", Btn.Kind.GHOST).icon("help");
        Btn settings = new Btn("설정", Btn.Kind.GHOST).icon("gear");
        online.addActionListener(e -> app.requireProfile(() -> app.show("connect")));
        host.addActionListener(e -> app.requireProfile(app::hostLocal));
        how.addActionListener(e -> Dialogs.howTo(app));
        settings.addActionListener(e -> Dialogs.settings(app));
        for (Btn b : new Btn[] { online, host }) {
            b.setMaximumSize(new Dimension(340, 54));
            b.setPreferredSize(new Dimension(340, 54));
            col.add(b);
            col.add(Box.createVerticalStrut(12));
        }
        JPanel small = UI.panel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 8, 0));
        small.add(how);
        small.add(settings);
        small.setMaximumSize(new Dimension(340, 46));
        col.add(Box.createVerticalStrut(4));
        col.add(small);
        for (Component c : col.getComponents()) ((JComponent) c).setAlignmentX(0.5f);
        center.add(col);
        add(center, BorderLayout.CENTER);

        JPanel foot = UI.panel(new BorderLayout());
        foot.setBorder(UI.pad(0, 28, 22, 28));
        JLabel ver = UI.label("v" + Proto.APP_VERSION + "  ·  Team STIM", Theme.font(13), Theme.TEXT_3);
        UI.hand(ver);
        ver.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Dialogs.about(app);
            }
        });
        foot.add(ver, BorderLayout.WEST);
        chip = new ProfileChip(app);
        foot.add(chip, BorderLayout.EAST);
        add(foot, BorderLayout.SOUTH);
    }

    @Override
    public void onShow() {
        chip.repaint();
        if (!Anim.reduced) Anim.forever(this);
        if (!app.prefs.seenTutorial()) {
            app.prefs.seenTutorial(true);
            app.prefs.save();
            javax.swing.SwingUtilities.invokeLater(() -> Dialogs.howTo(app));
        }
    }

    @Override
    public void onHide() {
        Anim.stop(this);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = UI.g(g0);
        double t = Anim.now() / 1000.0;
        int w = getWidth(), h = getHeight();
        for (Floater f : floaters) {
            double y = ((f.y - t * f.speed) % 1 + 1) % 1;
            double px = f.x * w + Math.sin(t * 0.4 + f.rot * 3) * 18;
            double py = y * (h + 120) - 60;
            AffineTransform old = g.getTransform();
            g.rotate(f.rot + t * f.spin, px, py);
            Art.Look l = new Art.Look();
            l.color = f.color;
            l.back = f.back;
            l.face = f.face;
            l.alpha = f.alpha;
            Art.tile(g, px - f.size * 0.35, py - f.size / 2, f.size * 0.7, f.size, l);
            g.setTransform(old);
        }
        // 가운데를 살짝 어둡게 해서 글자를 잘 보이게
        g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(w / 2.0, h / 2.0), Math.max(w, h) * 0.5f,
                new float[] { 0f, 1f }, new Color[] { new Color(11, 16, 32, 200), new Color(11, 16, 32, 0) }));
        g.fillRect(0, 0, w, h);
        g.dispose();
    }

    /** 오른쪽 아래 내 캐릭터와 닉네임. 누르면 프로필 수정 */
    static final class ProfileChip extends JComponent {
        private static final long serialVersionUID = 1L;
        private final App app;
        private boolean hover;

        ProfileChip(App app) {
            this.app = app;
            UI.hand(this);
            setToolTipText("프로필 바꾸기");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Dialogs.profile(app, false, () -> repaint());
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

        private String label() {
            String n = app.prefs.name();
            return n.isEmpty() ? "닉네임 정하기" : n;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UI.width(Theme.bold(14), label()) + 82, 46);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            int w = getWidth(), h = 46;
            g.setColor(hover ? Theme.SURFACE_HI : UI.a(Theme.SURFACE, 220));
            g.fill(UI.rr(0, 0, w, h, h / 2.0));
            g.setColor(Theme.STROKE);
            g.draw(UI.rr(0.5, 0.5, w - 1, h - 1, h / 2.0));
            Art.avatar(g, 6, 6, 34, app.prefs.avatar(), false);
            UI.text(g, label(), Theme.bold(14), Theme.TEXT, 50, (h - UI.fm(Theme.bold(14)).getHeight()) / 2.0);
            stim.davincicode.client.ui.Icons.paint(g, "pencil", w - 28, h / 2.0 - 7, 14, Theme.TEXT_3);
            g.dispose();
        }
    }
}
