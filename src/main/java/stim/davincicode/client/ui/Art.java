package stim.davincicode.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/** 앱의 그림 요소: 캐릭터 얼굴, 타일, 로고, 앱 아이콘. 모두 코드로 그린 원본 디자인이다. */
public final class Art {
    private Art() { }

    // ================================================================ 캐릭터 (아바타)

    public static void avatar(Graphics2D g0, double x, double y, double d, int index, boolean bot) {
        Graphics2D g = (Graphics2D) g0.create();
        Color c = Theme.avatar(index);
        Color top = UI.mix(c, Color.WHITE, 0.28);
        Color bottom = UI.mix(c, Color.BLACK, 0.12);
        g.setPaint(new GradientPaint((float) x, (float) y, top, (float) x, (float) (y + d), bottom));
        Shape head = bot ? UI.rr(x + d * 0.06, y + d * 0.12, d * 0.88, d * 0.82, d * 0.26) : new Ellipse2D.Double(x, y, d, d);
        g.fill(head);
        g.setColor(UI.a(Color.WHITE, 60));
        g.setStroke(new BasicStroke((float) Math.max(1, d / 30)));
        g.draw(head);
        Color ink = new Color(0x1B1F2C);
        double ex1 = x + d * 0.35, ex2 = x + d * 0.65, ey = y + d * 0.47;
        double er = d * 0.065;
        g.setColor(ink);
        g.setStroke(new BasicStroke((float) Math.max(1.3, d / 22), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (bot) {
            // 안테나와 화면 같은 눈
            g.draw(new Line2D.Double(x + d * 0.5, y + d * 0.12, x + d * 0.5, y + d * 0.02));
            g.fill(new Ellipse2D.Double(x + d * 0.44, y - d * 0.03, d * 0.12, d * 0.12));
            g.setColor(UI.a(ink, 230));
            g.fill(UI.rr(x + d * 0.2, y + d * 0.36, d * 0.6, d * 0.26, d * 0.12));
            g.setColor(UI.mix(c, Color.WHITE, 0.55));
            g.fill(UI.rr(ex1 - er, ey - er * 0.9, er * 2, er * 1.6, er * 0.7));
            g.fill(UI.rr(ex2 - er, ey - er * 0.9, er * 2, er * 1.6, er * 0.7));
            g.dispose();
            return;
        }
        int v = Math.floorMod(index, 8);
        // 눈
        switch (v) {
            case 1: // 웃는 눈
                g.draw(new Arc2D.Double(ex1 - er * 1.4, ey - er, er * 2.8, er * 2.6, 20, 140, Arc2D.OPEN));
                g.draw(new Arc2D.Double(ex2 - er * 1.4, ey - er, er * 2.8, er * 2.6, 20, 140, Arc2D.OPEN));
                break;
            case 3: // 윙크
                g.fill(new Ellipse2D.Double(ex1 - er, ey - er * 1.2, er * 2, er * 2.4));
                g.draw(new Arc2D.Double(ex2 - er * 1.4, ey - er, er * 2.8, er * 2.6, 20, 140, Arc2D.OPEN));
                break;
            case 5: // 반짝이는 큰 눈
                g.fill(new Ellipse2D.Double(ex1 - er * 1.4, ey - er * 1.6, er * 2.8, er * 3.2));
                g.fill(new Ellipse2D.Double(ex2 - er * 1.4, ey - er * 1.6, er * 2.8, er * 3.2));
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(ex1 - er * 0.6, ey - er * 1.1, er * 1.1, er * 1.1));
                g.fill(new Ellipse2D.Double(ex2 - er * 0.6, ey - er * 1.1, er * 1.1, er * 1.1));
                g.setColor(ink);
                break;
            case 6: // 졸린 눈
                g.draw(new Line2D.Double(ex1 - er * 1.3, ey, ex1 + er * 1.3, ey));
                g.draw(new Line2D.Double(ex2 - er * 1.3, ey, ex2 + er * 1.3, ey));
                break;
            default:
                g.fill(new Ellipse2D.Double(ex1 - er, ey - er * 1.2, er * 2, er * 2.4));
                g.fill(new Ellipse2D.Double(ex2 - er, ey - er * 1.2, er * 2, er * 2.4));
        }
        // 볼
        g.setColor(new Color(255, 120, 140, 90));
        g.fill(new Ellipse2D.Double(x + d * 0.17, y + d * 0.56, d * 0.15, d * 0.09));
        g.fill(new Ellipse2D.Double(x + d * 0.68, y + d * 0.56, d * 0.15, d * 0.09));
        // 입
        g.setColor(ink);
        double mx = x + d * 0.5, my = y + d * 0.64;
        switch (v) {
            case 2:
                g.draw(new Ellipse2D.Double(mx - d * 0.05, my - d * 0.03, d * 0.1, d * 0.1));
                break;
            case 4: { // 고양이 입
                Path2D p = new Path2D.Double();
                p.moveTo(mx - d * 0.11, my);
                p.quadTo(mx - d * 0.055, my + d * 0.07, mx, my);
                p.quadTo(mx + d * 0.055, my + d * 0.07, mx + d * 0.11, my);
                g.draw(p);
                break;
            }
            case 7: // 메롱
                g.draw(new Arc2D.Double(mx - d * 0.12, my - d * 0.08, d * 0.24, d * 0.14, 200, 140, Arc2D.OPEN));
                g.setColor(new Color(0xFF6F8E));
                g.fill(UI.rr(mx - d * 0.035, my + d * 0.03, d * 0.07, d * 0.07, d * 0.035));
                break;
            default:
                g.draw(new Arc2D.Double(mx - d * 0.1, my - d * 0.08, d * 0.2, d * 0.13, 200, 140, Arc2D.OPEN));
        }
        g.dispose();
    }

    // ================================================================ 타일

    /** 타일을 어떻게 그릴지 */
    public static final class Look {
        public char color = 'B';
        public String face = "?";     // 보여 줄 숫자 ("-"는 조커)
        public boolean back;          // 뒷면 (숫자를 모름)
        public boolean mineOpen;      // 내 타일인데 공개됨
        public boolean glow;          // 고를 수 있음 (마우스 올림)
        public Color glowColor = Theme.CORAL;
        public boolean selectable;
        public double alpha = 1;
        public String note;           // 메모
        public double flip = 1;       // 0~1 뒤집기 진행도 (0.5 이전은 뒷면)
    }

    public static void tile(Graphics2D g0, double x, double y, double w, double h, Look l) {
        Graphics2D g = (Graphics2D) g0.create();
        if (l.alpha < 1) UI.alpha(g, (float) l.alpha);
        boolean showBack = l.back;
        if (l.flip < 1) {
            double sx = Math.abs(Math.cos(l.flip * Math.PI));
            showBack = l.flip < 0.5;
            g.translate(x + w / 2, 0);
            g.scale(Math.max(0.02, sx), 1);
            g.translate(-(x + w / 2), 0);
        }
        boolean black = l.color == 'B';
        double r = Math.max(4, w * 0.16);
        if (l.glow) {
            for (int i = 6; i >= 1; i--) {
                g.setColor(UI.a(l.glowColor, 26 - i * 3));
                g.fill(UI.rr(x - i * 1.4, y - i * 1.4, w + i * 2.8, h + i * 2.8, r + i * 1.4));
            }
        }
        UI.shadow(g, x, y, w, h, r, 5, 150, 3);
        Color top = black ? Theme.TILE_B_TOP : Theme.TILE_W_TOP;
        Color bottom = black ? Theme.TILE_B_BOTTOM : Theme.TILE_W_BOTTOM;
        g.setPaint(new GradientPaint(0, (float) y, top, 0, (float) (y + h), bottom));
        g.fill(UI.rr(x, y, w, h, r));
        // 옆면 두께감
        g.setColor(black ? new Color(0, 0, 0, 120) : new Color(150, 130, 100, 110));
        g.fill(UI.rr(x, y + h - h * 0.07, w, h * 0.07, r * 0.6));
        g.setColor(black ? UI.a(Color.WHITE, 38) : UI.a(Color.WHITE, 220));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(UI.rr(x + 1, y + 1, w - 2, h - 2, r - 1));
        if (l.glow) {
            g.setColor(UI.a(l.glowColor, 230));
            g.setStroke(new BasicStroke(2f));
            g.draw(UI.rr(x - 1, y - 1, w + 2, h + 2, r + 1));
        }

        if (showBack) {
            // 뒷면 무늬: 마름모 안에 점
            Color ink = black ? UI.a(Color.WHITE, 46) : UI.a(new Color(0x5A4A30), 60);
            g.setColor(ink);
            double cx = x + w / 2, cy = y + h * 0.47, s = Math.min(w, h) * 0.26;
            Path2D dia = new Path2D.Double();
            dia.moveTo(cx, cy - s);
            dia.lineTo(cx + s * 0.8, cy);
            dia.lineTo(cx, cy + s);
            dia.lineTo(cx - s * 0.8, cy);
            dia.closePath();
            g.setStroke(new BasicStroke((float) Math.max(1.2, w / 34)));
            g.draw(dia);
            g.fill(new Ellipse2D.Double(cx - s * 0.18, cy - s * 0.18, s * 0.36, s * 0.36));
            g.setStroke(new BasicStroke(1f));
            g.draw(UI.rr(x + w * 0.14, y + h * 0.1, w * 0.72, h * 0.76, r * 0.6));
        } else {
            Color ink = black ? Theme.TILE_B_TEXT : Theme.TILE_W_TEXT;
            double cy = y + h * 0.46;
            if (l.face.equals("-")) {
                // 조커: 굵은 막대와 작은 별
                g.setColor(ink);
                g.fill(UI.rr(x + w * 0.24, cy - h * 0.045, w * 0.52, h * 0.09, h * 0.045));
                star(g, x + w * 0.73, y + h * 0.2, w * 0.11, Theme.AMBER);
                star(g, x + w * 0.27, y + h * 0.72, w * 0.08, Theme.AMBER);
            } else {
                java.awt.Font f = Theme.num((float) (h * 0.5));
                UI.center(g, l.face, f, ink, x + w / 2, cy);
                if (l.face.equals("6") || l.face.equals("9")) {
                    g.fill(UI.rr(x + w * 0.36, y + h * 0.72, w * 0.28, h * 0.035, h * 0.02));
                }
            }
            if (l.mineOpen) badge(g, x, y, w, h, "공개", Theme.CORAL);
        }
        if (l.note != null && !l.note.isEmpty()) {
            double s = Math.max(16, w * 0.38);
            g.setColor(new Color(0xFFD86B));
            g.fill(UI.rr(x - s * 0.25, y - s * 0.3, s, s * 0.8, 4));
            UI.center(g, l.note, Theme.bold((float) (s * 0.48)), new Color(0x4A3A10), x - s * 0.25 + s / 2, y - s * 0.3 + s * 0.4);
        }
        g.dispose();
    }

    private static void badge(Graphics2D g, double x, double y, double w, double h, String text, Color c) {
        java.awt.Font f = Theme.bold((float) Math.max(9, w * 0.17));
        double bw = Math.min(w - 6, UI.width(f, text) + 8), bh = UI.fm(f).getHeight() + 1;
        double bx = x + (w - bw) / 2, by = y + h - bh - h * 0.09;
        g.setColor(c);
        g.fill(UI.rr(bx, by, bw, bh, bh / 2));
        UI.center(g, text, f, Color.WHITE, bx + bw / 2, by + bh / 2);
    }

    public static void star(Graphics2D g, double cx, double cy, double r, Color c) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double rr = i % 2 == 0 ? r : r * 0.38;
            double a = -Math.PI / 2 + Math.PI / 4 * i;
            if (i == 0) p.moveTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
            else p.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
        }
        p.closePath();
        g.setColor(c);
        g.fill(p);
    }

    // ================================================================ 로고

    /** 위에 타일 4개, 아래에 DAVINCI CODE 글자. (cx, top) 기준으로 가운데 정렬, 높이를 돌려준다 */
    public static double logo(Graphics2D g0, double cx, double top, double scale) {
        Graphics2D g = (Graphics2D) g0.create();
        double tw = 34 * scale, th = 48 * scale, gap = 9 * scale;
        double total = tw * 4 + gap * 3;
        String[] faces = { "?", "7", "?", "-" };
        char[] colors = { 'B', 'W', 'W', 'B' };
        double[] rot = { -0.10, -0.03, 0.04, 0.11 };
        double[] lift = { 4, 0, 0, 4 };
        for (int i = 0; i < 4; i++) {
            double x = cx - total / 2 + i * (tw + gap);
            AffineTransform old = g.getTransform();
            g.rotate(rot[i], x + tw / 2, top + th / 2);
            Look l = new Look();
            l.color = colors[i];
            l.face = faces[i];
            l.back = faces[i].equals("?");
            tile(g, x, top + lift[i] * scale, tw, th, l);
            g.setTransform(old);
        }
        double y = top + th + 20 * scale;
        java.awt.Font f = Theme.num((float) (44 * scale));
        String a = "DAVINCI", b = " CODE";
        double wa = f.getStringBounds(a, g.getFontRenderContext()).getWidth();
        double wb = f.getStringBounds(b, g.getFontRenderContext()).getWidth();
        double x0 = cx - (wa + wb) / 2;
        UI.text(g, a, f, Theme.TEXT, x0, y);
        UI.text(g, b, f, Theme.CORAL, x0 + wa, y);
        g.dispose();
        return th + 20 * scale + UI.fm(f).getHeight();
    }

    // ================================================================ 배경

    public static void backdrop(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, Theme.BG_TOP, 0, h, Theme.BG_BOTTOM));
        g.fillRect(0, 0, w, h);
        float rad = Math.max(w, h) * 0.6f;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.12, h * 0.05), rad,
                new float[] { 0f, 1f }, new Color[] { UI.a(Theme.CORAL, 26), UI.a(Theme.CORAL, 0) }));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.92, h * 0.95), rad,
                new float[] { 0f, 1f }, new Color[] { UI.a(Theme.MINT, 20), UI.a(Theme.MINT, 0) }));
        g.fillRect(0, 0, w, h);
    }

    public static void felt(Graphics2D g, Rectangle2D r, double radius) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(r.getCenterX(), r.getCenterY()),
                (float) Math.max(r.getWidth(), r.getHeight()) * 0.7f,
                new float[] { 0f, 1f }, new Color[] { Theme.FELT_CENTER, Theme.FELT_EDGE }));
        g.fill(UI.rr(r.getX(), r.getY(), r.getWidth(), r.getHeight(), radius));
        // 은은한 대각선 결
        Graphics2D t = (Graphics2D) g.create();
        t.clip(UI.rr(r.getX(), r.getY(), r.getWidth(), r.getHeight(), radius));
        t.setColor(new Color(255, 255, 255, 6));
        t.setStroke(new BasicStroke(1f));
        for (double i = -r.getHeight(); i < r.getWidth(); i += 14) {
            t.draw(new Line2D.Double(r.getX() + i, r.getY(), r.getX() + i + r.getHeight(), r.getY() + r.getHeight()));
        }
        t.dispose();
        g.setColor(UI.a(Color.WHITE, 18));
        g.draw(UI.rr(r.getX() + 0.5, r.getY() + 0.5, r.getWidth() - 1, r.getHeight() - 1, radius));
    }

    // ================================================================ 앱 아이콘

    public static BufferedImage appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        UI.hints(g);
        double s = size;
        double pad = s * 0.04;
        g.setPaint(new GradientPaint(0, 0, new Color(0x1C2546), 0, (float) s, new Color(0x0B1020)));
        g.fill(UI.rr(pad, pad, s - pad * 2, s - pad * 2, s * 0.22));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(s * 0.3, s * 0.2), (float) (s * 0.8),
                new float[] { 0f, 1f }, new Color[] { UI.a(Theme.CORAL, 70), UI.a(Theme.CORAL, 0) }));
        g.fill(UI.rr(pad, pad, s - pad * 2, s - pad * 2, s * 0.22));
        double tw = s * 0.34, th = s * 0.48;
        AffineTransform old = g.getTransform();
        g.rotate(-0.16, s * 0.37, s * 0.52);
        Look b = new Look();
        b.color = 'B';
        b.back = true;
        tile(g, s * 0.37 - tw / 2, s * 0.52 - th / 2, tw, th, b);
        g.setTransform(old);
        g.rotate(0.12, s * 0.62, s * 0.5);
        Look w = new Look();
        w.color = 'W';
        w.face = "7";
        tile(g, s * 0.62 - tw / 2, s * 0.5 - th / 2, tw, th, w);
        g.setTransform(old);
        // 물음표 배지
        double bd = s * 0.24;
        double bx = s * 0.66, by = s * 0.12;
        g.setColor(Theme.CORAL);
        g.fill(new Ellipse2D.Double(bx, by, bd, bd));
        UI.center(g, "?", Theme.num((float) (bd * 0.72)), Color.WHITE, bx + bd / 2, by + bd / 2);
        g.dispose();
        return img;
    }
}
