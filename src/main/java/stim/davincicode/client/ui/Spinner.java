package stim.davincicode.client.ui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;

import javax.swing.JComponent;

/** 기다리는 동안 도는 원 */
public final class Spinner extends JComponent {
    private static final long serialVersionUID = 1L;
    private final int size;

    public Spinner(int size) {
        this.size = size;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Anim.forever(this);
    }

    @Override
    public void removeNotify() {
        Anim.stop(this);
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(size, size);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = UI.g(g0);
        double cx = getWidth() / 2.0, cy = getHeight() / 2.0, r = size / 2.0 - 4;
        g.setStroke(new BasicStroke(size / 11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(UI.a(Theme.TEXT, 30));
        g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 0, 360, Arc2D.OPEN));
        double t = (Anim.now() % 1100) / 1100.0;
        double start = -t * 360;
        double extent = 70 + 140 * Math.abs(Math.sin(t * Math.PI));
        g.setColor(Theme.CORAL);
        g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, start, extent, Arc2D.OPEN));
        g.dispose();
    }
}
