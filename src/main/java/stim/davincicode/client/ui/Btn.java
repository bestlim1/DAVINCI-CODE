package stim.davincicode.client.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.JButton;

/** 앱 전체에서 쓰는 버튼. JButton의 동작(클릭, 키보드, 비활성)은 그대로 쓰고 모양만 직접 그린다. */
public class Btn extends JButton {
    private static final long serialVersionUID = 1L;

    public enum Kind { PRIMARY, SECONDARY, GHOST, DANGER, MINT, OUTLINE }

    private Kind kind;
    private String iconName;
    private int height = 44;
    private int radius = 12;
    private int padX = 20;
    private boolean iconOnly;
    private final Anim.Tween hover = new Anim.Tween(0).dur(140);
    private final Anim.Tween press = new Anim.Tween(0).dur(90);
    private Color accent;

    public Btn(String text, Kind kind) {
        super(text);
        this.kind = kind;
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setFocusable(false);
        setFont(Theme.bold(15));
        setMargin(new Insets(0, 0, 0, 0));
        UI.hand(this);
        getModel().addChangeListener(e -> {
            hover.set(getModel().isRollover() && isEnabled() ? 1 : 0, this);
            press.set(getModel().isArmed() && getModel().isPressed() ? 1 : 0, this);
        });
    }

    /** 아이콘만 있는 동그란/네모 버튼 */
    public static Btn icon(String icon, String tooltip) {
        Btn b = new Btn("", Kind.GHOST);
        b.iconName = icon;
        b.iconOnly = true;
        b.height = 40;
        b.radius = 12;
        b.setToolTipText(tooltip);
        return b;
    }

    public Btn icon(String name) {
        iconName = name;
        repaint();
        return this;
    }

    public Btn small() {
        height = 34;
        radius = 10;
        padX = 14;
        setFont(Theme.bold(13));
        return this;
    }

    /** 아주 작은 버튼 (리액션 등) */
    public Btn tiny() {
        height = 28;
        radius = 9;
        padX = 9;
        setFont(Theme.bold(12));
        return this;
    }

    public Btn large() {
        height = 54;
        radius = 16;
        padX = 28;
        setFont(Theme.bold(17));
        return this;
    }

    public Btn h(int h) {
        height = h;
        return this;
    }

    public Btn kind(Kind k) {
        kind = k;
        repaint();
        return this;
    }

    public Btn accent(Color c) {
        accent = c;
        return this;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public Dimension getPreferredSize() {
        if (iconOnly) return new Dimension(height, height);
        Font f = getFont();
        int w = UI.width(f, getText()) + padX * 2;
        if (iconName != null) w += 18 + (getText().isEmpty() ? 0 : 8);
        return new Dimension(Math.max(w, height), height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, height);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = UI.g(g0);
        double hv = hover.get();
        double pr = press.get();
        int w = getWidth(), h = getHeight();
        double inset = pr * 1.5;
        double x = inset, y = inset + (h - height) / 2.0, bw = w - inset * 2, bh = height - inset * 2;
        if (!isEnabled()) UI.alpha(g, 0.42f);

        Color text;
        switch (kind) {
            case PRIMARY:
            case DANGER:
            case MINT: {
                Color base = accent != null ? accent : kind == Kind.PRIMARY ? Theme.CORAL : kind == Kind.DANGER ? Theme.RED : Theme.MINT;
                Color top = UI.mix(base, Color.WHITE, 0.10 + hv * 0.10);
                Color bot = UI.mix(base, Color.BLACK, 0.12 - hv * 0.06);
                if (isEnabled()) UI.shadow(g, x, y, bw, bh, radius, 8, (int) (70 + 50 * hv), 3);
                g.setPaint(new GradientPaint(0, (float) y, top, 0, (float) (y + bh), bot));
                g.fill(UI.rr(x, y, bw, bh, radius));
                g.setColor(UI.a(Color.WHITE, 46));
                g.draw(UI.rr(x + 0.5, y + 0.5, bw - 1, bh - 1, radius));
                text = kind == Kind.MINT ? new Color(0x062A22) : Color.WHITE;
                break;
            }
            case SECONDARY:
                g.setColor(UI.mix(Theme.SURFACE_HI, new Color(0x2C3860), hv));
                g.fill(UI.rr(x, y, bw, bh, radius));
                g.setColor(UI.mix(Theme.STROKE, Theme.STROKE_HI, hv));
                g.draw(UI.rr(x + 0.5, y + 0.5, bw - 1, bh - 1, radius));
                text = Theme.TEXT;
                break;
            case OUTLINE: {
                Color c = accent != null ? accent : Theme.CORAL;
                g.setColor(UI.a(c, (int) (18 + hv * 30)));
                g.fill(UI.rr(x, y, bw, bh, radius));
                g.setColor(UI.a(c, 170));
                g.draw(UI.rr(x + 0.5, y + 0.5, bw - 1, bh - 1, radius));
                text = c;
                break;
            }
            default:
                if (hv > 0.01) {
                    g.setColor(UI.a(Color.WHITE, (int) (hv * 20)));
                    g.fill(UI.rr(x, y, bw, bh, radius));
                }
                text = accent != null ? accent : UI.mix(Theme.TEXT_2, Theme.TEXT, hv);
        }

        Font f = getFont();
        String label = getText();
        int iconSize = iconOnly ? 20 : 18;
        int tw = label.isEmpty() ? 0 : UI.width(f, label);
        int total = tw + (iconName != null ? iconSize + (label.isEmpty() ? 0 : 8) : 0);
        double cx = x + (bw - total) / 2.0;
        double cy = y + bh / 2.0;
        if (iconName != null) {
            Icons.paint(g, iconName, cx, cy - iconSize / 2.0, iconSize, text);
            cx += iconSize + 8;
        }
        if (!label.isEmpty()) {
            g.setFont(f);
            g.setColor(text);
            java.awt.FontMetrics m = g.getFontMetrics();
            g.drawString(label, (float) cx, (float) (cy - (m.getAscent() + m.getDescent()) / 2.0 + m.getAscent()));
        }
        g.dispose();
    }
}
