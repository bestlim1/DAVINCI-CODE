package stim.davincicode.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** 작은 공용 컨트롤들: 스위치, 여러 개 중 하나 고르기, 슬라이더, 카드, 스크롤, 칩 */
public final class Controls {
    private Controls() { }

    // ------------------------------------------------------------------ 스위치

    public static final class Toggle extends JComponent {
        private static final long serialVersionUID = 1L;
        private boolean on;
        private final Anim.Tween knob = new Anim.Tween(0).dur(160);
        private final List<Runnable> listeners = new ArrayList<>();

        public Toggle(boolean on) {
            this.on = on;
            knob.jump(on ? 1 : 0);
            UI.hand(this);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!isEnabled()) return;
                    setOn(!Toggle.this.on);
                    for (Runnable r : listeners) r.run();
                }
            });
        }

        public boolean isOn() {
            return on;
        }

        public void setOn(boolean v) {
            on = v;
            knob.set(v ? 1 : 0, this);
            repaint();
        }

        /** 사용자가 바꿨을 때만 불린다 */
        public void onChange(Runnable r) {
            listeners.add(r);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(48, 28);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            if (!isEnabled()) UI.alpha(g, 0.45f);
            double k = knob.get();
            int h = 28, w = 48;
            double y = (getHeight() - h) / 2.0;
            g.setColor(UI.mix(Theme.SURFACE_HI, Theme.MINT, k));
            g.fill(UI.rr(0, y, w, h, h / 2.0));
            g.setColor(Theme.STROKE);
            g.draw(UI.rr(0.5, y + 0.5, w - 1, h - 1, h / 2.0));
            double kx = 3 + k * (w - h);
            UI.shadow(g, kx, y + 3, h - 6, h - 6, (h - 6) / 2.0, 3, 90, 1);
            g.setColor(Color.WHITE);
            g.fill(new java.awt.geom.Ellipse2D.Double(kx, y + 3, h - 6, h - 6));
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ 하나 고르기

    public static final class Segmented extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String[] options;
        private int selected;
        private int hoverIdx = -1;
        private final Anim.Tween pos = new Anim.Tween(0).dur(180);
        private final List<IntConsumer> listeners = new ArrayList<>();
        private int height = 40;

        public Segmented(int selected, String... options) {
            this.options = options;
            this.selected = selected;
            pos.jump(selected);
            UI.hand(this);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) return;
                    int i = indexAt(e.getX());
                    if (i >= 0 && i != Segmented.this.selected) {
                        setSelected(i);
                        for (IntConsumer l : listeners) l.accept(i);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i != hoverIdx) {
                        hoverIdx = i;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverIdx = -1;
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        public Segmented compact() {
            height = 34;
            return this;
        }

        private int indexAt(int x) {
            if (getWidth() <= 0) return -1;
            int i = (int) ((x - 3) / ((getWidth() - 6) / (double) options.length));
            return i >= 0 && i < options.length ? i : -1;
        }

        public int getSelected() {
            return selected;
        }

        public void setSelected(int i) {
            selected = i;
            pos.set(i, this);
            repaint();
        }

        public void onChange(IntConsumer l) {
            listeners.add(l);
        }

        @Override
        public Dimension getPreferredSize() {
            int w = 6;
            for (String o : options) w += Math.max(56, UI.width(Theme.bold(13), o) + 28);
            return new Dimension(w, height);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            if (!isEnabled()) UI.alpha(g, 0.5f);
            int w = getWidth(), h = height;
            double y0 = (getHeight() - h) / 2.0;
            g.setColor(Theme.SURFACE_LO);
            g.fill(UI.rr(0, y0, w, h, 11));
            g.setColor(Theme.STROKE);
            g.draw(UI.rr(0.5, y0 + 0.5, w - 1, h - 1, 11));
            double cw = (w - 6) / (double) options.length;
            double px = 3 + pos.get() * cw;
            g.setColor(Theme.SURFACE_HI);
            g.fill(UI.rr(px, y0 + 3, cw, h - 6, 8));
            g.setColor(UI.a(Theme.CORAL, 150));
            g.draw(UI.rr(px + 0.5, y0 + 3.5, cw - 1, h - 7, 8));
            for (int i = 0; i < options.length; i++) {
                Color c = i == selected ? Theme.TEXT : i == hoverIdx ? Theme.TEXT : Theme.TEXT_2;
                UI.center(g, options[i], Theme.bold(13), c, 3 + cw * i + cw / 2, y0 + h / 2.0);
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ 슬라이더

    public static final class Slider extends JComponent {
        private static final long serialVersionUID = 1L;
        private int value;
        private final List<IntConsumer> listeners = new ArrayList<>();

        public Slider(int value) {
            this.value = value;
            UI.hand(this);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    update(e.getX());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    update(e.getX());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    for (IntConsumer l : listeners) l.accept(Slider.this.value);
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private void update(int x) {
            value = (int) Math.round(Math.max(0, Math.min(100, (x - 10) * 100.0 / Math.max(1, getWidth() - 20))));
            repaint();
        }

        public int getValue() {
            return value;
        }

        public void onRelease(IntConsumer l) {
            listeners.add(l);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, 28);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            double cy = getHeight() / 2.0;
            double x0 = 10, x1 = getWidth() - 10;
            g.setColor(Theme.SURFACE_HI);
            g.fill(UI.rr(x0, cy - 3, x1 - x0, 6, 3));
            double kx = x0 + (x1 - x0) * value / 100.0;
            g.setColor(Theme.CORAL);
            g.fill(UI.rr(x0, cy - 3, kx - x0, 6, 3));
            UI.shadow(g, kx - 9, cy - 9, 18, 18, 9, 4, 90, 1);
            g.setColor(Color.WHITE);
            g.fill(new java.awt.geom.Ellipse2D.Double(kx - 9, cy - 9, 18, 18));
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ 카드

    public static class Card extends JPanel {
        private static final long serialVersionUID = 1L;
        private Color fill = Theme.SURFACE;
        private int radius = 18;
        private boolean shadow = true;

        public Card(LayoutManager lm) {
            super(lm);
            setOpaque(false);
            setBorder(UI.pad(20, 20, 20, 20));
        }

        public Card fill(Color c) {
            fill = c;
            return this;
        }

        public Card radius(int r) {
            radius = r;
            return this;
        }

        public Card flat() {
            shadow = false;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            // 그림자가 컴포넌트 밖으로 나가 잘리지 않도록 카드 몸체를 조금 안쪽에 그린다
            double ix = shadow ? 6 : 0, top = shadow ? 3 : 0, bottom = shadow ? 10 : 0;
            double w = getWidth() - ix * 2, h = getHeight() - top - bottom;
            if (shadow) UI.shadow(g, ix, top, w, h, radius, 6, 150, 3);
            g.setColor(fill);
            g.fill(UI.rr(ix, top, w, h, radius));
            g.setPaint(new java.awt.GradientPaint(0, (float) top, UI.a(Color.WHITE, 14), 0, (float) top + 60, UI.a(Color.WHITE, 0)));
            g.fill(UI.rr(ix, top, w, Math.min(h, 60), radius));
            g.setColor(Theme.STROKE);
            g.draw(UI.rr(ix + 0.5, top + 0.5, w - 1, h - 1, radius));
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ 스크롤

    public static JScrollPane scroll(Component view) {
        JScrollPane sp = new JScrollPane(view, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setViewportBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUI(new ThinBar());
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 10));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    static final class ThinBar extends BasicScrollBarUI {
        private boolean over;

        @Override
        protected void installListeners() {
            super.installListeners();
            scrollbar.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    over = true;
                    scrollbar.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    over = false;
                    scrollbar.repaint();
                }
            });
        }

        @Override
        protected javax.swing.JButton createDecreaseButton(int o) {
            return zero();
        }

        @Override
        protected javax.swing.JButton createIncreaseButton(int o) {
            return zero();
        }

        private static javax.swing.JButton zero() {
            javax.swing.JButton b = new javax.swing.JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
            // 트랙은 그리지 않는다
        }

        @Override
        protected void paintThumb(Graphics g0, JComponent c, java.awt.Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g = UI.g(g0);
            g.setColor(UI.a(Theme.TEXT, over || isDragging ? 90 : 46));
            g.fill(UI.rr(r.x + 2, r.y + 2, r.width - 4, r.height - 4, (r.width - 4) / 2.0));
            g.dispose();
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(8, 36);
        }
    }

    public static void scrollToBottom(JScrollPane sp) {
        JScrollBar bar = sp.getVerticalScrollBar();
        javax.swing.SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
    }

    // ------------------------------------------------------------------ 칩 (작은 태그)

    public static final class Chip extends JComponent {
        private static final long serialVersionUID = 1L;
        private String text;
        private Color color;
        private String iconName;

        public Chip(String text, Color color) {
            this.text = text;
            this.color = color;
        }

        public Chip icon(String n) {
            iconName = n;
            return this;
        }

        public void set(String t, Color c) {
            text = t;
            color = c;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UI.width(Theme.bold(12), text) + 20 + (iconName != null ? 18 : 0), 26);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            paint(g, text, color, iconName, 0, (getHeight() - 26) / 2.0);
            g.dispose();
        }

        /** 다른 컴포넌트 안에서 칩 모양만 그릴 때 쓴다. 그린 폭을 돌려준다. */
        public static double paint(Graphics2D g, String text, Color color, String icon, double x, double y) {
            double w = UI.width(Theme.bold(12), text) + 20 + (icon != null ? 18 : 0);
            g.setColor(UI.a(color, 34));
            g.fill(UI.rr(x, y, w, 26, 13));
            g.setColor(UI.a(color, 90));
            g.draw(UI.rr(x + 0.5, y + 0.5, w - 1, 25, 12.5));
            double tx = x + 10;
            if (icon != null) {
                Icons.paint(g, icon, tx - 1, y + 6, 14, color);
                tx += 18;
            }
            UI.text(g, text, Theme.bold(12), UI.mix(color, Color.WHITE, 0.35), tx, y + (26 - UI.fm(Theme.bold(12)).getHeight()) / 2.0);
            return w;
        }
    }

    /** 제목 + 오른쪽 컨트롤 한 줄 (설정 화면 등) */
    public static JPanel row(String title, String desc, JComponent right) {
        JPanel p = UI.panel(new BorderLayout(16, 0));
        p.setBorder(UI.pad(8, 0, 8, 0));
        JPanel left = UI.panel(null);
        left.setLayout(new javax.swing.BoxLayout(left, javax.swing.BoxLayout.Y_AXIS));
        left.add(UI.label(title, Theme.bold(15), Theme.TEXT));
        if (desc != null) {
            left.add(javax.swing.Box.createVerticalStrut(3));
            left.add(UI.label(desc, Theme.font(13), Theme.TEXT_3));
        }
        p.add(left, BorderLayout.CENTER);
        JPanel r = UI.panel(new java.awt.GridBagLayout());
        r.add(right);
        p.add(r, BorderLayout.EAST);
        return p;
    }
}
