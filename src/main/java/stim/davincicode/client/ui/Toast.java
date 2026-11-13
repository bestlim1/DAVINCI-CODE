package stim.davincicode.client.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;

/** 화면 위쪽에 잠깐 떴다 사라지는 알림. 마우스 입력을 받지 않아서 아래 화면을 가리지 않는다. */
public final class Toast extends JComponent {
    private static final long serialVersionUID = 1L;

    public enum Kind { INFO, SUCCESS, ERROR, WARN }

    private static final long LIFE = 3200;
    private static final int W = 460;

    private static final class Item {
        final String text;
        final Kind kind;
        final long start = Anim.now();
        List<String> lines;

        Item(String text, Kind kind) {
            this.text = text;
            this.kind = kind;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final JLayeredPane layer;

    private Toast(JLayeredPane layer) {
        this.layer = layer;
        setOpaque(false);
    }

    private static Toast instance;

    public static void show(JFrame frame, String text, Kind kind) {
        if (text == null || text.isEmpty()) return;
        if (instance == null || instance.layer != frame.getLayeredPane()) {
            instance = new Toast(frame.getLayeredPane());
            frame.getLayeredPane().add(instance, Integer.valueOf(JLayeredPane.DRAG_LAYER + 10));
        }
        instance.add(new Item(text, kind));
    }

    private void add(Item it) {
        for (Item o : items) if (o.text.equals(it.text) && Anim.now() - o.start < 1500) return; // 같은 알림 반복 방지
        items.add(it);
        if (items.size() > 3) items.remove(0);
        relayout();
        Anim.run(this, LIFE + 100);
    }

    private void relayout() {
        int h = 12;
        for (Item it : items) {
            it.lines = UI.wrap(it.text, Theme.bold(14), W - 70);
            h += height(it) + 10;
        }
        int x = (layer.getWidth() - W) / 2;
        setBounds(x, 14, W, h);
    }

    private static int height(Item it) {
        return 26 + it.lines.size() * 20;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        long now = Anim.now();
        items.removeIf(it -> now - it.start > LIFE);
        if (items.isEmpty()) {
            setBounds(0, 0, 0, 0);
            return;
        }
        if (getX() != (layer.getWidth() - W) / 2) relayout();
        Graphics2D g = UI.g(g0);
        int y = 4;
        for (Item it : items) {
            long age = now - it.start;
            double in = Anim.easeOut(Math.min(1, age / 220.0));
            double out = age > LIFE - 300 ? (LIFE - age) / 300.0 : 1;
            float alpha = (float) Math.max(0, Math.min(in, out));
            UI.alpha(g, alpha);
            int h = height(it);
            double dy = (1 - in) * -12;
            Color accent;
            String icon;
            switch (it.kind) {
                case SUCCESS: accent = Theme.MINT; icon = "check"; break;
                case ERROR: accent = Theme.RED; icon = "info"; break;
                case WARN: accent = Theme.AMBER; icon = "info"; break;
                default: accent = Theme.SKY; icon = "info";
            }
            UI.shadow(g, 8, y + dy, W - 16, h, 14, 8, 150, 4);
            g.setColor(new Color(0x1E2742));
            g.fill(UI.rr(8, y + dy, W - 16, h, 14));
            g.setColor(UI.a(accent, 120));
            g.draw(UI.rr(8.5, y + dy + 0.5, W - 17, h - 1, 14));
            g.setColor(UI.a(accent, 40));
            g.fill(new java.awt.geom.Ellipse2D.Double(22, y + dy + h / 2.0 - 13, 26, 26));
            Icons.paint(g, icon, 27, y + dy + h / 2.0 - 8, 16, accent);
            double ty = y + dy + 13;
            for (String line : it.lines) {
                UI.text(g, line, Theme.bold(14), Theme.TEXT, 60, ty);
                ty += 20;
            }
            y += h + 10;
        }
        g.dispose();
    }
}
