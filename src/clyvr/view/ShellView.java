package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.Entities.Role;
import clyvr.ui.Theme;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/** Consistent original-style header/sidebar around individual page classes. */
public class ShellView extends JPanel {
    private final AppController app;
    private final String page;

    public ShellView(AppController app, String page, JPanel content){
        super(new BorderLayout());
        this.app = app;
        this.page = page;
        setBackground(Theme.BG);

        add(header(), BorderLayout.NORTH);
        add(sidebar(), BorderLayout.WEST);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Theme.BG);
        body.add(content, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 6));
        bottom.setBackground(Theme.BG);
        JButton helper = Theme.button("✦  AI Assistant");
        helper.addActionListener(e -> app.navigate("Assistant"));
        bottom.add(helper);
        body.add(bottom, BorderLayout.SOUTH);

        add(body, BorderLayout.CENTER);
    }

    private JPanel header(){
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

        JButton profile = Theme.secondary("●  " + app.current.firstName + "  ▾");
        profile.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            for (String itemName : app.current.role == Role.PATIENT ? new String[]{"Legal Information", "Medical Information", "Contact Information"} : new String[]{"Legal Information", "Contact Information"}) {
                JMenuItem item = new JMenuItem(itemName);
                item.addActionListener(x -> app.navigate("My Information"));
                popup.add(item);
            }
            popup.addSeparator();
            JMenuItem logout = new JMenuItem("Log out");
            logout.addActionListener(x -> app.logout());
            popup.add(logout);
            popup.show(profile, 0, profile.getHeight());
        });
        actions.add(profile);

        top.add(actions, BorderLayout.EAST);
        return top;
    }

    private JPanel sidebar(){
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(219, 0));

        JPanel menu = new JPanel();
        menu.setOpaque(false);
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(new EmptyBorder(20, 13, 10, 13));

        menu(menu, "Dashboard");
        menu(menu, "Schedule");
        menu(menu, "Doctors");
        menu(menu, "Services");
        menu(menu, "About Us");

        sidebar.add(menu, BorderLayout.NORTH);
        return sidebar;
    }

    private void menu(JPanel p, String name){
        JButton b = nav(name, name.equals(page) || name.equals("Schedule") && (
            page.equals("My Appointments") || page.equals("Patient Approvals") || page.equals("Requests") ||
            page.equals("Timeslots") || page.equals("Reschedule") || page.equals("Book Appointment")));

        if (name.equals("Doctors") && page.equals("Doctor Management")) {
            b.setBackground(Theme.CARD_DARK);
        }
        b.addActionListener(e -> app.navigate(name));
        p.add(b);
        p.add(Box.createVerticalStrut(6));
    }

    private JButton nav(String name, boolean active){
        JButton b = new JButton(name);
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        // Sets inactive sidebar buttons to Theme.WHITE so they pop off the sidebar background
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