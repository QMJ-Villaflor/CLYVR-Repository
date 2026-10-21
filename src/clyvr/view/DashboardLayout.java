package clyvr.view;

import clyvr.ui.Theme;
import java.awt.*;
import javax.swing.*;

/** Shared layout utilities for role-specific dashboards. */
public final class DashboardLayout {
    private DashboardLayout(){}

    public static JPanel columns(JPanel left, JPanel right){
        JPanel grid = new JPanel(new GridLayout(1, 2, 22, 0));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.add(left);
        grid.add(right);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 620));
        return grid;
    }

    public static JPanel section(String name, boolean darker){
        JPanel p = darker ? Theme.cardDark(new BorderLayout(0, 16)) : Theme.card(new BorderLayout(0, 16));
        JLabel h = Theme.label(name, 21, true);
        if (darker) h.setForeground(Theme.WHITE);
        p.add(h, BorderLayout.NORTH);
        return p;
    }

    public static JPanel stack(){
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    /** Standalone item card for left-column entries */
    public static JPanel itemCard(String title, String info) {
        JPanel card = Theme.card(new BorderLayout(0, 6));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 105));

        JLabel heading = Theme.label(title, 16, true);
        card.add(heading, BorderLayout.NORTH);

        JTextArea details = new JTextArea(info, 2, 18);
        details.setOpaque(false);
        details.setEditable(false);
        details.setFocusable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(Theme.font(13, false));
        details.setForeground(Theme.INK);
        card.add(details, BorderLayout.CENTER);

        return card;
    }

    public static void entry(JPanel column, String title, String info){
        JPanel item = new JPanel(new BorderLayout(0, 4));
        item.setOpaque(false);
        item.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
        JLabel heading = Theme.label(title, 16, true);
        item.add(heading, BorderLayout.NORTH);
        JTextArea details = new JTextArea(info, 2, 18);
        details.setOpaque(false);
        details.setEditable(false);
        details.setFocusable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(Theme.font(13, false));
        details.setForeground(Theme.INK);
        item.add(details, BorderLayout.CENTER);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 98));
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(item);
        column.add(Box.createVerticalStrut(13));
    }

    /** Renders quick action items with white backgrounds (Theme.secondary) to pop off the right card */
    public static void action(JPanel stack, String title, Runnable fn){
        JButton b = Theme.secondary(title); // Uses white background with dark teal text
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        b.addActionListener(e -> fn.run());
        stack.add(b);
        stack.add(Box.createVerticalStrut(12));
    }

    public static JPanel cardBody(JPanel parent, JPanel contents){
        contents.setOpaque(false);
        JScrollPane scroll = new JScrollPane(contents);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        parent.add(scroll, BorderLayout.CENTER);
        return parent;
    }
}