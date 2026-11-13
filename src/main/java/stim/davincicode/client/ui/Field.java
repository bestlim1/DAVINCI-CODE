package stim.davincicode.client.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/** 둥근 입력창 (안내 문구, 앞쪽 아이콘, 글자 수 제한) */
public class Field extends JTextField {
    private static final long serialVersionUID = 1L;
    private final String placeholder;
    private String iconName;
    private boolean error;

    public Field(String placeholder) {
        this.placeholder = placeholder;
        style(this);
    }

    public Field icon(String name) {
        iconName = name;
        setBorder(new EmptyBorder(0, 42, 0, 14));
        return this;
    }

    public Field limit(int max) {
        Field.limit(this, max);
        return this;
    }

    public void setError(boolean e) {
        error = e;
        repaint();
    }

    static void style(JTextComponent c) {
        c.setOpaque(false);
        c.setBorder(new EmptyBorder(0, 14, 0, 14));
        c.setFont(Theme.font(15));
        c.setForeground(Theme.TEXT);
        c.setCaretColor(Theme.CORAL);
        c.setSelectionColor(UI.a(Theme.CORAL, 110));
        c.setSelectedTextColor(Color.WHITE);
        c.setDisabledTextColor(Theme.TEXT_3);
        c.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                c.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                c.repaint();
            }
        });
    }

    static void limit(JTextComponent c, int max) {
        ((AbstractDocument) c.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int off, String s, AttributeSet a) throws BadLocationException {
                replace(fb, off, 0, s, a);
            }

            @Override
            public void replace(FilterBypass fb, int off, int len, String s, AttributeSet a) throws BadLocationException {
                if (s == null) s = "";
                s = s.replaceAll("[\\t\\r\\n\\u0000-\\u001F]", " ");
                int room = max - (fb.getDocument().getLength() - len);
                if (room <= 0) return;
                if (s.length() > room) s = s.substring(0, room);
                super.replace(fb, off, len, s, a);
            }
        });
    }

    static void paintBox(JTextComponent c, Graphics g0, String placeholder, String iconName, boolean error) {
        Graphics2D g = UI.g(g0);
        int w = c.getWidth(), h = c.getHeight();
        boolean focus = c.isFocusOwner();
        g.setColor(c.isEnabled() ? Theme.SURFACE_LO : UI.a(Theme.SURFACE_LO, 120));
        g.fill(UI.rr(0, 0, w, h, 12));
        Color line = error ? Theme.RED : focus ? Theme.CORAL : Theme.STROKE_HI;
        g.setColor(focus || error ? UI.a(line, 200) : line);
        g.draw(UI.rr(0.5, 0.5, w - 1, h - 1, 12));
        if (focus && !error) {
            g.setColor(UI.a(Theme.CORAL, 40));
            g.setStroke(new java.awt.BasicStroke(3f));
            g.draw(UI.rr(-1, -1, w + 2, h + 2, 13));
        }
        if (iconName != null) Icons.paint(g, iconName, 14, h / 2.0 - 9, 18, focus ? Theme.CORAL : Theme.TEXT_3);
        g.dispose();
    }

    static void paintPlaceholder(JTextComponent c, Graphics g0, String placeholder) {
        if (placeholder == null || c.getDocument().getLength() > 0) return;
        Graphics2D g = UI.g(g0);
        int left = c.getInsets().left;
        UI.text(g, placeholder, c.getFont(), Theme.TEXT_3, left + 1,
                (c.getHeight() - UI.fm(c.getFont()).getHeight()) / 2.0);
        g.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintBox(this, g, placeholder, iconName, error);
        super.paintComponent(g);
        paintPlaceholder(this, g, placeholder);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, 120), 46);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, 46);
    }

    /** 비밀번호 입력창 */
    public static class Pass extends JPasswordField {
        private static final long serialVersionUID = 1L;
        private final String placeholder;

        public Pass(String placeholder) {
            this.placeholder = placeholder;
            style(this);
            setEchoChar('●');
            setBorder(new EmptyBorder(0, 42, 0, 14));
        }

        public Pass limit(int max) {
            Field.limit(this, max);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintBox(this, g, placeholder, "lock", false);
            super.paintComponent(g);
            paintPlaceholder(this, g, placeholder);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(160, 46);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 46);
        }
    }
}
