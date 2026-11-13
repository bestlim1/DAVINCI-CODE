package stim.davincicode.client.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;

/** 그리기 도우미 모음 */
public final class UI {
    private UI() { }

    private static final Graphics2D MEASURE;

    static {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        MEASURE = img.createGraphics();
        hints(MEASURE);
    }

    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    /** 복사본 Graphics2D에 고품질 설정을 켜서 돌려준다. 다 쓰면 dispose() 해야 한다. */
    public static Graphics2D g(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        hints(g2);
        return g2;
    }

    public static FontMetrics fm(Font f) {
        return MEASURE.getFontMetrics(f);
    }

    public static int width(Font f, String s) {
        return (int) Math.ceil(f.getStringBounds(s, MEASURE.getFontRenderContext()).getWidth());
    }

    public static Color a(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    /** 부드러운 그림자: 조금씩 커지는 반투명 모양을 겹쳐 그린다. */
    public static void shadow(Graphics2D g, double x, double y, double w, double h, double r, int spread, int alpha, double dy) {
        Composite old = g.getComposite();
        for (int i = spread; i >= 1; i--) {
            float t = (float) i / spread;
            int al = (int) (alpha * (1 - t) * (1 - t) / 2.2) + 1;
            g.setColor(new Color(0, 0, 0, Math.min(255, al)));
            g.fill(rr(x - i, y - i + dy, w + i * 2, h + i * 2, r + i));
        }
        g.setComposite(old);
    }

    public static void alpha(Graphics2D g, float a) {
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, a))));
    }

    public static void stroke(Graphics2D g, float w) {
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    public static void fillStroke(Graphics2D g, Shape s, Color fill, Color line) {
        if (fill != null) {
            g.setColor(fill);
            g.fill(s);
        }
        if (line != null) {
            g.setColor(line);
            g.setStroke(new BasicStroke(1f));
            g.draw(s);
        }
    }

    /** (x, y)를 글자 상자의 왼쪽 위로 보고 그린다. */
    public static void text(Graphics2D g, String s, Font f, Color c, double x, double y) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics m = g.getFontMetrics();
        g.drawString(s, (float) x, (float) (y + m.getAscent()));
    }

    /** 상자의 가운데에 그린다. */
    public static void center(Graphics2D g, String s, Font f, Color c, double cx, double cy) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics m = g.getFontMetrics();
        double w = f.getStringBounds(s, g.getFontRenderContext()).getWidth();
        g.drawString(s, (float) (cx - w / 2), (float) (cy - (m.getAscent() + m.getDescent()) / 2.0 + m.getAscent()));
    }

    public static String ellipsize(String s, Font f, int maxW) {
        if (width(f, s) <= maxW) return s;
        String e = "…";
        for (int i = s.length() - 1; i > 0; i--) {
            String t = s.substring(0, i) + e;
            if (width(f, t) <= maxW) return t;
        }
        return e;
    }

    /** 글자 단위로 줄바꿈 (한글은 띄어쓰기가 적어서 글자 단위가 자연스럽다). 띄어쓰기가 있으면 그 자리를 우선한다. */
    public static List<String> wrap(String text, Font f, int maxW) {
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            String rest = para;
            while (!rest.isEmpty()) {
                if (width(f, rest) <= maxW) {
                    lines.add(rest);
                    break;
                }
                int cut = 1;
                while (cut < rest.length() && width(f, rest.substring(0, cut + 1)) <= maxW) cut++;
                int space = rest.lastIndexOf(' ', cut);
                if (space > cut / 2) cut = space + 1;
                lines.add(rest.substring(0, cut).trim());
                rest = rest.substring(cut).trim();
            }
            if (para.isEmpty()) lines.add("");
        }
        return lines;
    }

    public static JPanel panel(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    public static Border pad(int t, int l, int b, int r) {
        return BorderFactory.createEmptyBorder(t, l, b, r);
    }

    public static JLabel label(String text, Font f, Color c) {
        JLabel l = new Text(text);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    /**
     * 글자 폭을 앱의 그리기 방식(부드러운 글꼴, 소수점 폭)과 똑같이 계산하는 라벨.
     * 기본 JLabel은 폭을 다르게 재서 마지막 글자가 잘릴 때가 있다. 공간이 모자라면 "…"로 줄인다.
     */
    static final class Text extends JLabel {
        private static final long serialVersionUID = 1L;

        Text(String text) {
            super(text);
        }

        private boolean plain() {
            String t = getText();
            return t != null && !t.startsWith("<html>") && getIcon() == null;
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            if (!plain() || isPreferredSizeSet()) return super.getPreferredSize();
            java.awt.Insets in = getInsets();
            return new java.awt.Dimension(width(getFont(), getText()) + in.left + in.right + 1,
                    fm(getFont()).getHeight() + in.top + in.bottom);
        }

        @Override
        public java.awt.Dimension getMinimumSize() {
            return plain() ? new java.awt.Dimension(Math.min(40, getPreferredSize().width), getPreferredSize().height) : super.getMinimumSize();
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return plain() && !isMaximumSizeSet() ? getPreferredSize() : super.getMaximumSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (!plain()) {
                super.paintComponent(g0);
                return;
            }
            Graphics2D g = g(g0);
            java.awt.Insets in = getInsets();
            double avail = getWidth() - in.left - in.right;
            String t = getText();
            double tw = width(getFont(), t);
            if (tw > avail) {
                t = ellipsize(t, getFont(), (int) avail);
                tw = width(getFont(), t);
            }
            double x = in.left;
            int align = getHorizontalAlignment();
            if (align == CENTER) x = in.left + (avail - tw) / 2;
            else if (align == RIGHT || align == TRAILING) x = getWidth() - in.right - tw;
            double y = in.top + (getHeight() - in.top - in.bottom - fm(getFont()).getHeight()) / 2.0;
            text(g, t, getFont(), getForeground(), x, y);
            g.dispose();
        }
    }

    /** 여러 줄 설명용 라벨 (HTML 없이 줄바꿈되도록 직접 그린다) */
    public static JComponent paragraph(String text, Font f, Color c, int width) {
        return new Paragraph(text, f, c, width);
    }

    public static void hand(Component c) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static final class Paragraph extends JComponent {
        private static final long serialVersionUID = 1L;
        private String text;
        private final Font font;
        private final Color color;
        private final int prefW;

        Paragraph(String text, Font font, Color color, int prefW) {
            this.text = text;
            this.font = font;
            this.color = color;
            this.prefW = prefW;
        }

        public void setText(String t) {
            text = t;
            revalidate();
            repaint();
        }

        private int lineH() {
            return (int) Math.ceil(fm(font).getHeight() * 1.18);
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            int w = getWidth() > 0 ? getWidth() : prefW;
            return new java.awt.Dimension(prefW, wrap(text, font, Math.max(40, w)).size() * lineH());
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = g(g0);
            int y = 0;
            for (String line : wrap(text, font, getWidth())) {
                text(g, line, font, color, 0, y + (lineH() - fm(font).getHeight()) / 2.0);
                y += lineH();
            }
            g.dispose();
        }
    }
}
