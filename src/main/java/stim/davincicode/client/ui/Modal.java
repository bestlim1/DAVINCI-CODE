package stim.davincicode.client.ui;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * 창 안에 띄우는 대화상자. 운영체제 기본 대화상자(JOptionPane) 대신 앱 디자인에 맞춘 카드를
 * 반투명 배경 위에 띄운다. 열려 있는 동안 아래 화면은 클릭되지 않는다.
 */
public final class Modal extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final List<Modal> open = new ArrayList<>();

    private final JLayeredPane layer;
    private final JComponent content;
    private final boolean dismissable;
    private final long openedAt = Anim.now();
    private final List<Runnable> onClose = new ArrayList<>();
    private final ComponentAdapter resizer;
    private boolean closed;

    private Modal(JLayeredPane layer, JComponent content, boolean dismissable) {
        super(new GridBagLayout());
        this.layer = layer;
        this.content = content;
        this.dismissable = dismissable;
        setOpaque(false);
        setFocusable(true);
        setFocusCycleRoot(true);
        add(content);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (Modal.this.dismissable && !content.getBounds().contains(e.getPoint())) close();
            }
        });
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        getActionMap().put("esc", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (Modal.this.dismissable) close();
            }
        });
        resizer = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                setBounds(0, 0, layer.getWidth(), layer.getHeight());
                revalidate();
            }
        };
    }

    public static Modal open(Component anyInWindow, JComponent content, boolean dismissable) {
        JRootPane rp = SwingUtilities.getRootPane(anyInWindow);
        JLayeredPane lp = rp.getLayeredPane();
        Modal m = new Modal(lp, content, dismissable);
        m.setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.add(m, Integer.valueOf(JLayeredPane.MODAL_LAYER + open.size()));
        lp.addComponentListener(m.resizer);
        open.add(m);
        m.revalidate();
        Anim.run(m, 220);
        SwingUtilities.invokeLater(() -> {
            JTextComponent t = firstText(content);
            if (t != null) t.requestFocusInWindow();
            else m.requestFocusInWindow();
        });
        return m;
    }

    private static JTextComponent firstText(Container c) {
        for (Component ch : c.getComponents()) {
            if (ch instanceof JTextComponent && ch.isEnabled() && ((JTextComponent) ch).isEditable()) return (JTextComponent) ch;
            if (ch instanceof Container) {
                JTextComponent t = firstText((Container) ch);
                if (t != null) return t;
            }
        }
        return null;
    }

    public static boolean anyOpen() {
        return !open.isEmpty();
    }

    public static void closeAll() {
        for (Modal m : new ArrayList<>(open)) m.close();
    }

    public Modal onClose(Runnable r) {
        onClose.add(r);
        return this;
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        if (closed) return;
        closed = true;
        open.remove(this);
        layer.removeComponentListener(resizer);
        layer.remove(this);
        layer.repaint();
        for (Runnable r : onClose) r.run();
    }

    @Override
    public void paint(Graphics g0) {
        double p = Anim.easeOut(Anim.progress(openedAt, 200));
        Graphics2D g = (Graphics2D) g0.create();
        UI.alpha(g, (float) p);
        g.setColor(new Color(4, 7, 15, 175));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
        Graphics2D g2 = (Graphics2D) g0.create();
        UI.alpha(g2, (float) p);
        g2.translate(0, (1 - p) * 14);
        super.paint(g2);
        g2.dispose();
    }

    // ================================================================ 대화상자 카드

    /** 제목, 설명, 본문, 아래쪽 버튼으로 된 기본 대화상자 */
    public static final class Dialog {
        public final Controls.Card card;
        public Modal modal;

        Dialog(Controls.Card card) {
            this.card = card;
        }

        public void close() {
            if (modal != null) modal.close();
        }
    }

    public static Dialog dialog(Component parent, String title, String subtitle, JComponent body, int width,
            boolean closable, JComponent... actions) {
        Controls.Card card = new Controls.Card(new BorderLayout(0, 16)) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(width, d.height);
            }
        };
        card.setBorder(UI.pad(24, 26, 22, 26));
        Dialog dlg = new Dialog(card);

        JPanel head = UI.panel(new BorderLayout(12, 0));
        JPanel titles = UI.panel(null);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(UI.label(title, Theme.bold(21), Theme.TEXT));
        if (subtitle != null) {
            titles.add(Box.createVerticalStrut(6));
            titles.add(UI.paragraph(subtitle, Theme.font(14), Theme.TEXT_2, width - 110));
        }
        for (Component c : titles.getComponents()) ((JComponent) c).setAlignmentX(0f);
        head.add(titles, BorderLayout.CENTER);
        if (closable) {
            Btn x = Btn.icon("close", "닫기");
            x.addActionListener(e -> dlg.close());
            JPanel xp = UI.panel(new BorderLayout());
            xp.add(x, BorderLayout.NORTH);
            head.add(xp, BorderLayout.EAST);
        }
        card.add(head, BorderLayout.NORTH);
        if (body != null) card.add(body, BorderLayout.CENTER);
        if (actions.length > 0) {
            JPanel foot = UI.panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            for (JComponent a : actions) foot.add(a);
            card.add(foot, BorderLayout.SOUTH);
        }
        dlg.modal = open(parent, card, closable);
        return dlg;
    }

    public static void alert(Component parent, String title, String message) {
        Btn ok = new Btn("확인", Btn.Kind.PRIMARY);
        Dialog d = dialog(parent, title, message, null, 420, true, ok);
        ok.addActionListener(e -> d.close());
    }

    public static Dialog confirm(Component parent, String title, String message, String okText, boolean danger, Runnable onOk) {
        Btn cancel = new Btn("취소", Btn.Kind.SECONDARY);
        Btn ok = new Btn(okText, danger ? Btn.Kind.DANGER : Btn.Kind.PRIMARY);
        Dialog d = dialog(parent, title, message, null, 440, true, cancel, ok);
        cancel.addActionListener(e -> d.close());
        ok.addActionListener(e -> {
            d.close();
            onOk.run();
        });
        return d;
    }

    /** 한 줄 입력 (비밀번호 등) */
    public static void prompt(Component parent, String title, String message, boolean password,
            java.util.function.Consumer<String> onOk) {
        JTextComponent field = password ? new Field.Pass("비밀번호").limit(12) : new Field("").limit(40);
        Btn cancel = new Btn("취소", Btn.Kind.SECONDARY);
        Btn ok = new Btn("확인", Btn.Kind.PRIMARY);
        JPanel body = UI.panel(new BorderLayout());
        body.add(field, BorderLayout.CENTER);
        Dialog d = dialog(parent, title, message, body, 420, true, cancel, ok);
        Runnable submit = () -> {
            String v = password ? new String(((javax.swing.JPasswordField) field).getPassword()) : field.getText();
            d.close();
            onOk.accept(v);
        };
        cancel.addActionListener(e -> d.close());
        ok.addActionListener(e -> submit.run());
        ((javax.swing.JTextField) field).addActionListener(e -> submit.run());
    }

    // ================================================================ 말풍선 메뉴 (팝오버)

    /** 버튼 옆에 잠깐 뜨는 작은 메뉴. 바깥을 누르거나 Esc를 누르면 닫힌다. */
    public static final class Popover extends JPanel {
        private static final long serialVersionUID = 1L;
        private static Popover current;
        private final JLayeredPane layer;
        private final AWTEventListener outside;

        private Popover(JLayeredPane layer, JComponent content) {
            super(new BorderLayout());
            this.layer = layer;
            setOpaque(false);
            setBorder(UI.pad(8, 8, 8, 8));
            add(content);
            outside = ev -> {
                if (ev.getID() != MouseEvent.MOUSE_PRESSED) return;
                Component src = ((MouseEvent) ev).getComponent();
                if (src == null || !SwingUtilities.isDescendingFrom(src, this)) {
                    SwingUtilities.invokeLater(this::close);
                }
            };
        }

        public static Popover show(Component anchor, JComponent content, boolean above) {
            if (current != null) current.close();
            JLayeredPane lp = SwingUtilities.getRootPane(anchor).getLayeredPane();
            Popover p = new Popover(lp, content);
            Dimension d = p.getPreferredSize();
            Point at = SwingUtilities.convertPoint(anchor, 0, 0, lp);
            int x = Math.max(8, Math.min(at.x, lp.getWidth() - d.width - 8));
            int y = above ? at.y - d.height - 4 : at.y + anchor.getHeight() + 4;
            y = Math.max(8, Math.min(y, lp.getHeight() - d.height - 8));
            p.setBounds(x, y, d.width, d.height);
            lp.add(p, JLayeredPane.POPUP_LAYER);
            lp.repaint();
            Toolkit.getDefaultToolkit().addAWTEventListener(p.outside, AWTEvent.MOUSE_EVENT_MASK);
            current = p;
            return p;
        }

        public static Popover showAt(Component inWindow, Point screenPoint, JComponent content) {
            if (current != null) current.close();
            JLayeredPane lp = SwingUtilities.getRootPane(inWindow).getLayeredPane();
            Popover p = new Popover(lp, content);
            Dimension d = p.getPreferredSize();
            Point at = new Point(screenPoint);
            SwingUtilities.convertPointFromScreen(at, lp);
            int x = Math.max(8, Math.min(at.x, lp.getWidth() - d.width - 8));
            int y = Math.max(8, Math.min(at.y, lp.getHeight() - d.height - 8));
            p.setBounds(x, y, d.width, d.height);
            lp.add(p, JLayeredPane.POPUP_LAYER);
            lp.repaint();
            Toolkit.getDefaultToolkit().addAWTEventListener(p.outside, AWTEvent.MOUSE_EVENT_MASK);
            current = p;
            return p;
        }

        public static void closeCurrent() {
            if (current != null) current.close();
        }

        public void close() {
            Toolkit.getDefaultToolkit().removeAWTEventListener(outside);
            Rectangle r = getBounds();
            layer.remove(this);
            layer.repaint(r);
            if (current == this) current = null;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            int w = getWidth(), h = getHeight();
            UI.shadow(g, 4, 4, w - 8, h - 8, 14, 4, 160, 3);
            g.setColor(Theme.SURFACE_HI);
            g.fill(UI.rr(4, 4, w - 8, h - 8, 14));
            g.setColor(Theme.STROKE_HI);
            g.draw(UI.rr(4.5, 4.5, w - 9, h - 9, 14));
            g.dispose();
        }

        /** 아이콘 + 글자 메뉴 줄 목록. items = { 아이콘, 글자, Runnable, ... } */
        public static JComponent menu(Object... items) {
            JPanel p = UI.panel(null);
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBorder(UI.pad(4, 4, 4, 4));
            for (int i = 0; i + 2 < items.length; i += 3) {
                Runnable action = (Runnable) items[i + 2];
                Btn b = new Btn((String) items[i + 1], Btn.Kind.GHOST).small().icon((String) items[i]);
                b.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                b.addActionListener(e -> {
                    closeCurrent();
                    action.run();
                });
                b.setAlignmentX(0f);
                p.add(b);
            }
            return p;
        }
    }
}
