package clyvr.view;

import clyvr.ui.Theme;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/** Centered white authentication design matching the original CLYVR reference. */
public final class AuthLayout {
    private AuthLayout() {}
    private static final Color AQUA = new Color(83, 216, 224);
    private static final Color CHARCOAL = new Color(85, 85, 85);
    private static final Color PLACEHOLDER = new Color(158, 158, 158);

    public static JPanel shell(JPanel form) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        JPanel column = new JPanel();
        column.setBackground(Color.WHITE);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setBorder(new EmptyBorder(44, 12, 16, 12));

        JLabel logo = centered("CLYVR", 58, true, AQUA);
        JLabel subtitle = centered("Appointment System", 28, true, AQUA);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(logo);
        column.add(Box.createVerticalStrut(-4));
        column.add(subtitle);
        column.add(Box.createVerticalStrut(24));

        JPanel card = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0, 0, 0, 17));
                g.fillRoundRect(4, 5, getWidth()-8, getHeight()-8, 7, 7);
                g.setColor(Color.WHITE);
                g.fillRoundRect(1, 1, getWidth()-6, getHeight()-7, 7, 7);
                g.setColor(new Color(163, 163, 163));
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(1, 1, getWidth()-6, getHeight()-7, 7, 7);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(596, 537));
        card.setMinimumSize(new Dimension(520, 505));
        card.setMaximumSize(new Dimension(596, 537));
        card.setBorder(new EmptyBorder(43, 65, 45, 65));
        card.add(form, BorderLayout.CENTER);
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(card);
        JPanel centeredHolder = new JPanel(new GridBagLayout());
        centeredHolder.setBackground(Color.WHITE);
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.NORTH;
        g.gridx = 0;g.gridy = 0;g.weightx = 1;g.weighty = 1;
        centeredHolder.add(column, g);
        root.add(centeredHolder, BorderLayout.CENTER);
        return root;
    }

    public static JLabel centered(String text, int size, boolean bold, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(color);
        label.setFont(Theme.font(size, bold));
        return label;
    }
    public static JPanel column() {
        JPanel p = new JPanel();p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;
    }
    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(45,true));label.setForeground(CHARCOAL);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
    public static void space(JPanel p,int pixels){p.add(Box.createVerticalStrut(pixels));}

    private static void configure(JTextField field) {
        field.setFont(Theme.font(18, false));field.setForeground(CHARCOAL);
        field.setCaretColor(CHARCOAL);field.setBackground(Color.WHITE);
        field.setBorder(new CompoundBorder(new LineBorder(new Color(172,172,172),1,true),new EmptyBorder(6,13,6,13)));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension dim = new Dimension(466, 66);
        field.setPreferredSize(dim);field.setMinimumSize(dim);field.setMaximumSize(dim);
    }
    public static JTextField emailField() {
        JTextField field = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) placeholder(g,this,"Email Address");
            }
        };
        configure(field);return field;
    }
    public static JPasswordField passwordField() {
        JPasswordField field = new JPasswordField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getPassword().length==0 && !isFocusOwner()) placeholder(g,this,"Password");
            }
        };
        configure(field);return field;
    }
    private static void placeholder(Graphics g, JTextField field, String hint) {
        Graphics2D gg=(Graphics2D)g.create();gg.setColor(PLACEHOLDER);
        gg.setFont(field.getFont());FontMetrics metrics=gg.getFontMetrics();
        gg.drawString(hint,22,(field.getHeight()+metrics.getAscent()-metrics.getDescent())/2);
        gg.dispose();
    }
    public static JButton submit(String text) {
        JButton button=new JButton(text) {
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g=(Graphics2D)gr.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getModel().isPressed()?AQUA.darker():AQUA);
                g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,7,7);
                g.dispose();super.paintComponent(gr);
            }
        };
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setOpaque(false);button.setContentAreaFilled(false);button.setBorderPainted(false);
        button.setFocusPainted(false);button.setForeground(Color.WHITE);
        button.setFont(Theme.font(17,true));button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setPreferredSize(new Dimension(466,66));
        button.setMaximumSize(new Dimension(466,66));
        button.setMinimumSize(new Dimension(466,66));
        return button;
    }
    public static JPanel footer(String lead, String linkLabel, Runnable onLink) {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER,0,0));footer.setOpaque(false);
        JLabel label = new JLabel(lead+" ");label.setForeground(new Color(139,139,139));label.setFont(Theme.font(15,false));
        JButton link = new JButton("<html><u>"+linkLabel+"</u></html>");
        link.setUI(new javax.swing.plaf.basic.BasicButtonUI());link.setContentAreaFilled(false);
        link.setBorder(new EmptyBorder(0,0,0,0));link.setFocusPainted(false);
        link.setFont(Theme.font(15,false));link.setForeground(new Color(0,128,255));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addActionListener(e->onLink.run());footer.add(label);footer.add(link);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.setMaximumSize(new Dimension(466,32));return footer;
    }
}
