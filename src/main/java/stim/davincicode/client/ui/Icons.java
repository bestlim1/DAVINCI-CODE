package stim.davincicode.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/** 직접 그린 벡터 아이콘 (24×24 좌표 기준, 선 굵기 2). 이미지 파일이 필요 없다. */
public final class Icons {
    private Icons() { }

    public static Icon of(String name, int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = UI.g(g);
                paint(g2, name, x, y, size, color);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    public static void paint(Graphics2D g0, String name, double x, double y, double size, Color color) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(color);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (name) {
            case "back": poly(g, false, 15, 5, 8, 12, 15, 19); break;
            case "next": poly(g, false, 9, 5, 16, 12, 9, 19); break;
            case "close": line(g, 6, 6, 18, 18); line(g, 18, 6, 6, 18); break;
            case "plus": line(g, 12, 5, 12, 19); line(g, 5, 12, 19, 12); break;
            case "minus": line(g, 5, 12, 19, 12); break;
            case "check": poly(g, false, 5, 12.5, 10, 17.5, 19, 7); break;
            case "send": {
                Path2D p = new Path2D.Double();
                p.moveTo(21, 3); p.lineTo(3, 10.5); p.lineTo(10.5, 13.5); p.lineTo(13.5, 21); p.closePath();
                g.draw(p);
                line(g, 21, 3, 10.5, 13.5);
                break;
            }
            case "user":
                g.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
                g.draw(new Arc2D.Double(4, 13.5, 16, 14, 0, 180, Arc2D.OPEN));
                break;
            case "users":
                g.draw(new Ellipse2D.Double(5, 5, 7, 7));
                g.draw(new Arc2D.Double(2, 14, 13, 12, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(13.5, 4.5, 6, 7, -90, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(14, 14, 8, 12, 0, 90, Arc2D.OPEN));
                break;
            case "lock":
                g.draw(new RoundRectangle2D.Double(5, 11, 14, 10, 4, 4));
                g.draw(new Arc2D.Double(8, 4, 8, 12, 0, 180, Arc2D.OPEN));
                line(g, 8, 10, 8, 11);
                line(g, 16, 10, 16, 11);
                break;
            case "crown": {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 8); p.lineTo(7.5, 12.5); p.lineTo(12, 5); p.lineTo(16.5, 12.5); p.lineTo(21, 8);
                p.lineTo(19, 19); p.lineTo(5, 19); p.closePath();
                g.fill(p);
                break;
            }
            case "eye":
            case "eyeoff": {
                Path2D p = new Path2D.Double();
                p.moveTo(2, 12); p.quadTo(12, 2.5, 22, 12); p.quadTo(12, 21.5, 2, 12); p.closePath();
                g.draw(p);
                g.draw(new Ellipse2D.Double(9, 9, 6, 6));
                if (name.equals("eyeoff")) line(g, 4, 4, 20, 20);
                break;
            }
            case "gear": {
                g.draw(new Ellipse2D.Double(9, 9, 6, 6));
                g.draw(new Ellipse2D.Double(5, 5, 14, 14));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    line(g, 12 + Math.cos(a) * 7, 12 + Math.sin(a) * 7, 12 + Math.cos(a) * 10, 12 + Math.sin(a) * 10);
                }
                break;
            }
            case "chat": {
                Path2D p = new Path2D.Double();
                p.append(new RoundRectangle2D.Double(3, 4, 18, 13, 8, 8), false);
                g.draw(p);
                poly(g, false, 8, 17, 7, 21, 12.5, 17);
                break;
            }
            case "trophy": {
                Path2D p = new Path2D.Double();
                p.moveTo(7, 4); p.lineTo(17, 4); p.lineTo(17, 9); p.curveTo(17, 15, 7, 15, 7, 9); p.closePath();
                g.draw(p);
                g.draw(new Arc2D.Double(3, 5, 5, 6, 90, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(16, 5, 5, 6, -90, 180, Arc2D.OPEN));
                line(g, 12, 14, 12, 18);
                line(g, 8, 20, 16, 20);
                break;
            }
            case "bot":
                g.draw(new RoundRectangle2D.Double(4.5, 8, 15, 12, 6, 6));
                line(g, 12, 4.5, 12, 8);
                g.fill(new Ellipse2D.Double(10.5, 2, 3, 3));
                g.fill(new Ellipse2D.Double(8, 12.5, 2.6, 2.6));
                g.fill(new Ellipse2D.Double(13.4, 12.5, 2.6, 2.6));
                line(g, 2, 13, 2, 15);
                line(g, 22, 13, 22, 15);
                break;
            case "wifi":
                g.draw(new Arc2D.Double(2, 8, 20, 20, 40, 100, Arc2D.OPEN));
                g.draw(new Arc2D.Double(6, 12, 12, 12, 40, 100, Arc2D.OPEN));
                g.fill(new Ellipse2D.Double(10.5, 17, 3, 3));
                break;
            case "play": {
                Path2D p = new Path2D.Double();
                p.moveTo(8, 5); p.lineTo(19, 12); p.lineTo(8, 19); p.closePath();
                g.fill(p);
                break;
            }
            case "refresh": {
                g.draw(new Arc2D.Double(4, 4, 16, 16, 30, 280, Arc2D.OPEN));
                poly(g, false, 20, 4, 20, 9, 15, 9);
                break;
            }
            case "volume":
            case "mute": {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 9); p.lineTo(7, 9); p.lineTo(12, 5); p.lineTo(12, 19); p.lineTo(7, 15); p.lineTo(3, 15); p.closePath();
                g.draw(p);
                if (name.equals("volume")) {
                    g.draw(new Arc2D.Double(11, 8, 7, 8, -60, 120, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(11, 5, 10, 14, -60, 120, Arc2D.OPEN));
                } else {
                    line(g, 16, 9, 21, 15);
                    line(g, 21, 9, 16, 15);
                }
                break;
            }
            case "info":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                line(g, 12, 11, 12, 16.5);
                g.fill(new Ellipse2D.Double(10.8, 6.8, 2.4, 2.4));
                break;
            case "help":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.draw(new Arc2D.Double(9, 7, 6, 6, -60, 240, Arc2D.OPEN));
                line(g, 13.3, 12.4, 12, 13.6);
                g.fill(new Ellipse2D.Double(10.8, 15.8, 2.4, 2.4));
                break;
            case "flag":
                line(g, 5, 21, 5, 4);
                poly(g, true, 5, 4, 18, 4, 15, 8, 18, 12, 5, 12);
                break;
            case "block":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                line(g, 6.5, 6.5, 17.5, 17.5);
                break;
            case "timer":
                g.draw(new Ellipse2D.Double(4, 6, 16, 16));
                line(g, 12, 14, 12, 10);
                line(g, 10, 2.5, 14, 2.5);
                line(g, 12, 2.5, 12, 6);
                break;
            case "exit":
                poly(g, false, 13, 4, 5, 4, 5, 20, 13, 20);
                line(g, 10, 12, 21, 12);
                poly(g, false, 17, 8, 21, 12, 17, 16);
                break;
            case "star": {
                Path2D p = new Path2D.Double();
                for (int i = 0; i < 10; i++) {
                    double r = i % 2 == 0 ? 9.5 : 4.2;
                    double a = -Math.PI / 2 + Math.PI / 5 * i;
                    double px = 12 + Math.cos(a) * r, py = 12.5 + Math.sin(a) * r;
                    if (i == 0) p.moveTo(px, py);
                    else p.lineTo(px, py);
                }
                p.closePath();
                g.fill(p);
                break;
            }
            case "home":
                poly(g, false, 3, 11, 12, 3.5, 21, 11);
                poly(g, false, 5.5, 9.5, 5.5, 20, 18.5, 20, 18.5, 9.5);
                break;
            case "server":
                g.draw(new RoundRectangle2D.Double(3, 3.5, 18, 7, 4, 4));
                g.draw(new RoundRectangle2D.Double(3, 13.5, 18, 7, 4, 4));
                g.fill(new Ellipse2D.Double(6, 6, 2.2, 2.2));
                g.fill(new Ellipse2D.Double(6, 16, 2.2, 2.2));
                break;
            case "monitor":
                g.draw(new RoundRectangle2D.Double(2.5, 3.5, 19, 13, 4, 4));
                line(g, 8, 21, 16, 21);
                line(g, 12, 16.5, 12, 21);
                break;
            case "zap": {
                Path2D p = new Path2D.Double();
                p.moveTo(13, 2); p.lineTo(4, 14); p.lineTo(11, 14); p.lineTo(10, 22); p.lineTo(20, 9.5); p.lineTo(13, 9.5); p.closePath();
                g.fill(p);
                break;
            }
            case "copy":
                g.draw(new RoundRectangle2D.Double(8, 8, 13, 13, 4, 4));
                poly(g, false, 16, 5, 16, 3, 3, 3, 3, 16, 5, 16);
                break;
            case "search":
                g.draw(new Ellipse2D.Double(4, 4, 13, 13));
                line(g, 15.5, 15.5, 20.5, 20.5);
                break;
            case "pencil":
                poly(g, true, 15, 4, 20, 9, 9, 20, 4, 20, 4, 15);
                break;
            case "sparkle": {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 2); p.quadTo(13, 11, 22, 12); p.quadTo(13, 13, 12, 22); p.quadTo(11, 13, 2, 12); p.quadTo(11, 11, 12, 2);
                g.fill(p);
                break;
            }
            case "list":
                line(g, 9, 6, 20, 6);
                line(g, 9, 12, 20, 12);
                line(g, 9, 18, 20, 18);
                g.fill(new Ellipse2D.Double(3.5, 4.8, 2.4, 2.4));
                g.fill(new Ellipse2D.Double(3.5, 10.8, 2.4, 2.4));
                g.fill(new Ellipse2D.Double(3.5, 16.8, 2.4, 2.4));
                break;
            case "smile":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.fill(new Ellipse2D.Double(8.3, 8.5, 2.4, 2.4));
                g.fill(new Ellipse2D.Double(13.3, 8.5, 2.4, 2.4));
                g.draw(new Arc2D.Double(7.5, 9, 9, 8, 200, 140, Arc2D.OPEN));
                break;
            case "heart": {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 20.5);
                p.curveTo(2, 14, 2.5, 4.5, 8, 4.5);
                p.curveTo(10.5, 4.5, 12, 6.5, 12, 7.5);
                p.curveTo(12, 6.5, 13.5, 4.5, 16, 4.5);
                p.curveTo(21.5, 4.5, 22, 14, 12, 20.5);
                g.fill(p);
                break;
            }
            default:
                g.draw(new Ellipse2D.Double(5, 5, 14, 14));
        }
        g.dispose();
    }

    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static void poly(Graphics2D g, boolean close, double... pts) {
        Path2D p = new Path2D.Double();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        if (close) p.closePath();
        g.draw(p);
    }

    /** 회전된 아이콘 (스피너 등) */
    public static void paintRotated(Graphics2D g, String name, double cx, double cy, double size, Color c, double angle) {
        AffineTransform old = g.getTransform();
        g.rotate(angle, cx, cy);
        paint(g, name, cx - size / 2, cy - size / 2, size, c);
        g.setTransform(old);
    }
}
