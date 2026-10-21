package clyvr.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicButtonUI;

/** Palette and controls based on the original cyan CLYVR prototype. */
public final class Theme {
    private Theme() {}
    public static final Color HEADER = new Color(102, 196, 212);
    public static final Color SIDEBAR = new Color(126, 220, 238);
    public static final Color BG = new Color(212, 247, 252);
    public static final Color CARD = new Color(123, 223, 239);
    public static final Color CARD_DARK = new Color(103, 195, 211);
    public static final Color INK = new Color(26, 85, 96);
    public static final Color MUTED = new Color(66, 111, 121);
    public static final Color LINE = new Color(85, 160, 173);
    public static final Color WHITE = Color.WHITE;
    public static final Color RED = new Color(158, 55, 65);
    public static final Color GREEN = new Color(21, 119, 87);
    public static final Color WARM = new Color(159, 99, 0);

    public static Font font(int size, boolean bold) {
        return new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size);
    }
    public static void setup() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}
        UIManager.put("OptionPane.messageFont", font(14, false));
        UIManager.put("OptionPane.buttonFont", font(13, true));
        UIManager.put("Label.font", font(14, false));
        UIManager.put("ComboBox.font", font(14, false));
        UIManager.put("ComboBox.foreground", INK);
        UIManager.put("ComboBox.background", WHITE);
        UIManager.put("TextField.font", font(14, false));
        UIManager.put("TextField.foreground", INK);
        UIManager.put("ScrollBar.width", 12);
    }
    public static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout); p.setBackground(BG); return p;
    }
    public static JPanel card(LayoutManager layout) {
        JPanel p = new RoundedPanel(CARD, 16); p.setLayout(layout);
        p.setBorder(new EmptyBorder(18, 22, 18, 22)); return p;
    }
    public static JPanel cardDark(LayoutManager layout) {
        JPanel p = new RoundedPanel(CARD_DARK, 16); p.setLayout(layout);
        p.setBorder(new EmptyBorder(18, 22, 18, 22)); return p;
    }
    public static JLabel label(String text, int size, boolean bold) {
        JLabel l = new JLabel(text); l.setFont(font(size, bold)); l.setForeground(INK);
        l.setAlignmentX(Component.LEFT_ALIGNMENT); return l;
    }
    public static JLabel muted(String s) { JLabel l=label(s,14,false);l.setForeground(MUTED);return l; }

    /** A painted, platform-independent button; avoids white-on-white defaults. */
    public static class AquaButton extends JButton {
        private final Color fill; private final Color fg;
        private boolean hover = false;
        public AquaButton(String s, Color fill, Color fg) {
            super(s); this.fill=fill; this.fg=fg;
            setUI(new BasicButtonUI());setOpaque(false);setContentAreaFilled(false);
            setBorderPainted(false);setFocusPainted(false);
            setRolloverEnabled(true);setForeground(fg);setFont(font(14,true));
            setMargin(new Insets(9,17,9,17));
            setBorder(new EmptyBorder(10,16,10,16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setHorizontalAlignment(SwingConstants.CENTER);
            addMouseListener(new java.awt.event.MouseAdapter(){
                public void mouseEntered(java.awt.event.MouseEvent e){hover=true;repaint();}
                public void mouseExited(java.awt.event.MouseEvent e){hover=false;repaint();}
            });
        }
        @Override protected void paintComponent(Graphics source) {
            Graphics2D g=(Graphics2D)source.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color chosen=isEnabled()?fill: new Color(187, 213, 217);
            if(isEnabled() && (hover||getModel().isPressed())) chosen = chosen.darker();
            g.setColor(chosen);g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,9,9);
            g.setColor(new Color(42, 124, 137, 120));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,9,9);
            g.dispose();super.paintComponent(source);
        }
        @Override public Color getForeground(){return isEnabled()?fg: new Color(91,111,116);}
    }
    public static JButton button(String s){return new AquaButton(s,CARD_DARK,INK);}
    public static JButton secondary(String s){return new AquaButton(s,WHITE,INK);}
    public static JButton danger(String s){return new AquaButton(s,new Color(255,233,233),RED);}
    public static JButton pill(String s){return new AquaButton(s,CARD,INK);}
    public static JTextField field(){JTextField f=new JTextField(18);styleInput(f);return f;}
    public static void styleInput(JTextField f){f.setFont(font(14,false));f.setForeground(INK);f.setBackground(WHITE);f.setCaretColor(INK);f.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(10,12,10,12)));}
    public static JTextArea area(int rows){JTextArea a=new JTextArea(rows,26);a.setLineWrap(true);a.setWrapStyleWord(true);a.setFont(font(14,false));a.setForeground(INK);a.setBackground(WHITE);a.setBorder(new EmptyBorder(10,12,10,12));return a;}
    public static JPanel vertical(){JPanel p=panel(null);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;}
    public static void space(JPanel p,int height){p.add(Box.createVerticalStrut(height));}
    public static JScrollPane scroll(Component content){JScrollPane s=new JScrollPane(content);s.setBorder(null);s.getVerticalScrollBar().setUnitIncrement(20);s.getViewport().setBackground(BG);s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);return s;}
    public static String safe(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    public static class RoundedPanel extends JPanel {
        private final Color fill;private final int arc;
        public RoundedPanel(Color fill,int arc){this.fill=fill;this.arc=arc;setOpaque(false);}
        @Override protected void paintComponent(Graphics source){Graphics2D g=(Graphics2D)source.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(fill);g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,arc,arc);g.dispose();super.paintComponent(source);}
    }
}
