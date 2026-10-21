package clyvr.view;

import clyvr.controller.AppController;
import clyvr.ui.Theme;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.*;
import javax.swing.border.*;

/** Consistent layout wrapper supporting floating AI chat overlay. */
public class ShellView extends JPanel {
    private final AppController app;
    private final String page;
    private final FloatingChatWidget chatWidget;
    private final JLayeredPane layeredPane = new JLayeredPane();
    private final JPanel mainLayout = new JPanel(new BorderLayout());

    public ShellView(AppController app, String page, JPanel content) {
        super(new BorderLayout());
        this.app = app;
        this.page = page;
        setBackground(Theme.BG);

        chatWidget = new FloatingChatWidget(app);

        mainLayout.setBackground(Theme.BG);
        mainLayout.add(header(), BorderLayout.NORTH);
        mainLayout.add(sidebar(), BorderLayout.WEST);

        // Body section without the old bottom rectangular helper button panel
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Theme.BG);
        body.add(content, BorderLayout.CENTER);

        mainLayout.add(body, BorderLayout.CENTER);

        layeredPane.add(mainLayout, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(chatWidget, JLayeredPane.PALETTE_LAYER);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                repositionLayers();
            }
        });

        add(layeredPane, BorderLayout.CENTER);
        repositionLayers();
    }

    private void repositionLayers() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        mainLayout.setBounds(0, 0, w, h);

        int widgetW = 360;
        int widgetH = 490;
        int margin = 20;

        chatWidget.setBounds(w - widgetW - margin, h - widgetH - margin, widgetW, widgetH);
        layeredPane.revalidate();
        layeredPane.repaint();
    }

    private JPanel header() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Theme.HEADER);
        top.setPreferredSize(new Dimension(0, 108));
        top.setBorder(new EmptyBorder(17, 27, 16, 26));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));

        JLabel word = Theme.label("CLYVR", 36, true);
        word.setForeground(Theme.WHITE);
        brand.add(word);

        JLabel tag = Theme.label("Clinic Appointment Management System", 17, true);
        tag.setForeground(Theme.WHITE);
        brand.add(tag);
        top.add(brand, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 15));
        actions.setOpaque(false);

        long unread = app.store.notices.stream().filter(n -> n.userId.equals(app.current.id) && !n.read).count();
        JButton bell = Theme.secondary("Notifications" + (unread > 0 ? "  (" + unread + ")" : ""));
        bell.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            int count = 0;
            for (clyvr.model.Entities.Notice notice : app.store.notices) {
                if (!notice.userId.equals(app.current.id)) continue;
                if (count++ == 5) break;
                String txt = notice.text.length() > 62 ? notice.text.substring(0, 61) + "…" : notice.text;
                JMenuItem item = new JMenuItem((notice.read ? "" : "●  ") + txt);
                item.setFont(Theme.font(12, false));
                item.addActionListener(x -> app.navigate("Notifications"));
                popup.add(item);
            }
            if (count == 0) {
                JMenuItem no = new JMenuItem("No notifications yet");
                no.setEnabled(false);
                popup.add(no);
            }
            popup.addSeparator();
            JMenuItem all = new JMenuItem("View all notifications");
            all.addActionListener(x -> app.navigate("Notifications"));
            popup.add(all);
            popup.show(bell, 0, bell.getHeight());
        });
        actions.add(bell);

        // Direct navigation button to User Information without a dropdown menu
        JButton profile = Theme.secondary("●  " + app.current.firstName);
        profile.addActionListener(e -> app.navigate("My Information"));
        actions.add(profile);

        top.add(actions, BorderLayout.EAST);
        return top;
    }

    private JPanel sidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(219, 0));

        JPanel menu = new JPanel();
        menu.setOpaque(false);
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(new EmptyBorder(22, 13, 10, 13));

        menu(menu, "Dashboard");
        menu(menu, "Schedule");
        menu(menu, "Doctors");
        menu(menu, "Services");
        menu(menu, "About Us");

        sidebar.add(menu, BorderLayout.NORTH);

        // Dedicated bottom panel containing the Log out button
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(new EmptyBorder(10, 13, 20, 13));

        JButton logout = Theme.secondary("⇦   Log out");
        logout.setAlignmentX(Component.LEFT_ALIGNMENT);
        logout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        logout.setPreferredSize(new Dimension(190, 46));
        logout.setHorizontalAlignment(SwingConstants.CENTER);
        logout.addActionListener(e -> app.logout());

        bottom.add(logout);
        sidebar.add(bottom, BorderLayout.SOUTH);

        return sidebar;
    }

    private void menu(JPanel p, String name) {
        JButton b = nav(name, name.equals(page) || name.equals("Schedule") && (
            page.equals("My Appointments") || page.equals("Patient Approvals") || page.equals("Requests") ||
            page.equals("Timeslots") || page.equals("Reschedule") || page.equals("Book Appointment")));

        if (name.equals("Doctors") && page.equals("Doctor Management")) {
            b.setBackground(Theme.CARD_DARK);
        }
        b.addActionListener(e -> app.navigate(name));
        p.add(b);
        p.add(Box.createVerticalStrut(12)); // Spacing between navigation links
    }

    private JButton nav(String name, boolean active) {
        JButton b = new JButton(name);
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBackground(active ? Theme.CARD_DARK : Theme.WHITE);
        b.setForeground(Theme.INK);
        b.setFont(Theme.font(16, true));
        b.setFocusPainted(false);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        b.setPreferredSize(new Dimension(190, 46));
        b.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(10, 6, 10, 6)));
        return b;
    }
}