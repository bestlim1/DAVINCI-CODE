package stim.davincicode.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JComponent;
import javax.swing.JPanel;

import stim.davincicode.client.ui.Theme;
import stim.davincicode.client.ui.UI;

/** 화면 위쪽 막대: 왼쪽 / 가운데(제목) / 오른쪽 영역 */
final class TopBar extends JPanel {
    private static final long serialVersionUID = 1L;
    final JPanel left = row(java.awt.FlowLayout.LEFT, 10);
    final JPanel right = row(java.awt.FlowLayout.RIGHT, 8);
    final JPanel center = row(java.awt.FlowLayout.LEFT, 12);

    /**
     * 가로 한 줄. FlowLayout은 한 줄 안에서 부품을 세로 가운데에 놓으므로,
     * 높이 42짜리 보이지 않는 기둥을 하나 넣어 줄 높이를 막대 높이에 맞춘다.
     */
    private static JPanel row(int align, int gap) {
        JPanel p = UI.panel(new java.awt.FlowLayout(align, gap, 0));
        p.add(javax.swing.Box.createRigidArea(new Dimension(0, 42)));
        return p;
    }

    TopBar() {
        super(new BorderLayout(12, 0));
        setOpaque(false);
        setBorder(UI.pad(12, 18, 12, 18));
        for (JPanel p : new JPanel[] { left, center, right }) p.setBorder(UI.pad(0, 0, 0, 0));
        JPanel l = UI.panel(new BorderLayout(10, 0));
        l.add(left, BorderLayout.WEST);
        l.add(center, BorderLayout.CENTER);
        add(l, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    void addLeft(JComponent c) {
        left.add(c);
    }

    void addCenter(JComponent c) {
        center.add(c);
    }

    void addRight(JComponent c) {
        right.add(c);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, 66);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = UI.g(g0);
        g.setColor(new Color(10, 15, 30, 120));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Theme.STROKE);
        g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        g.dispose();
    }

    /** 작은 상태 표시 (서버 이름, ping 등) */
    static final class Pill extends JComponent {
        private static final long serialVersionUID = 1L;
        private String text;
        private Color dot;
        private String icon;

        Pill(String text, Color dot, String icon) {
            this.text = text;
            this.dot = dot;
            this.icon = icon;
        }

        void set(String t, Color c) {
            if (t.equals(text) && c.equals(dot)) return;
            text = t;
            dot = c;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UI.width(Theme.bold(13), text) + (icon != null ? 66 : 46), 34);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            int w = getWidth(), h = 34;
            g.setColor(UI.a(Theme.SURFACE, 200));
            g.fill(UI.rr(0, 0, w, h, h / 2.0));
            g.setColor(Theme.STROKE);
            g.draw(UI.rr(0.5, 0.5, w - 1, h - 1, h / 2.0));
            double x = 14;
            if (icon != null) {
                stim.davincicode.client.ui.Icons.paint(g, icon, x - 2, h / 2.0 - 8, 16, Theme.TEXT_2);
                x += 20;
            }
            g.setColor(dot);
            g.fill(new java.awt.geom.Ellipse2D.Double(x, h / 2.0 - 4, 8, 8));
            UI.text(g, text, Theme.bold(13), Theme.TEXT_2, x + 14, (h - UI.fm(Theme.bold(13)).getHeight()) / 2.0);
            g.dispose();
        }
    }

    static String pingText(long ms) {
        return ms < 0 ? "연결 확인 중" : ms + "ms";
    }

    static Color pingColor(long ms) {
        if (ms < 0) return Theme.TEXT_3;
        if (ms < 80) return Theme.MINT;
        if (ms < 200) return Theme.AMBER;
        return Theme.RED;
    }
}
