package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.BackendNotification;
import clyvr.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.time.format.DateTimeFormatter;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class NotificationsView extends JPanel {

    public NotificationsView(AppController app) {
        super(new BorderLayout());
        JPanel page = ViewSupport.page(
                "Notifications", "Persistent notifications for your signed-in database identity.");
        JLabel status = Theme.muted("Loading notifications…");
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel rows = Theme.vertical();
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton refresh = Theme.secondary("Refresh notifications");
        refresh.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(status);
        Theme.space(page, 8);
        page.add(rows);
        Theme.space(page, 8);
        page.add(refresh);
        refresh.addActionListener(event -> load(app, rows, status, refresh));
        add(ViewSupport.wrap(page));
        load(app, rows, status, refresh);
    }

    private static void load(
            AppController app, JPanel rows, JLabel status, JButton refresh) {
        refresh.setEnabled(false);
        status.setText("Loading notifications from the database…");
        rows.removeAll();
        app.loadNotifications(notifications -> {
            for (BackendNotification notification : notifications) {
                JButton read = notification.isRead()
                        ? null : Theme.secondary("Mark read");
                if (read != null) {
                    read.addActionListener(event -> {
                        read.setEnabled(false);
                        app.markNotificationRead(notification.notificationId(), () -> {
                            status.setText("Notification marked read.");
                            load(app, rows, status, refresh);
                        }, failure -> {
                            read.setEnabled(true);
                            app.error("The notification was not marked read.");
                        });
                    });
                }
                String timestamp = notification.createdAt()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                String summary = timestamp + (notification.isRead() ? " · Read" : " · Unread");
                if (read == null) {
                    ViewSupport.line(rows, notification.message(), summary);
                } else {
                    ViewSupport.line(rows, notification.message(), summary, read);
                }
            }
            if (notifications.isEmpty()) {
                rows.add(ViewSupport.empty("No notifications have been recorded."));
            }
            status.setText("Loaded " + notifications.size() + " notification(s).");
            refresh.setEnabled(true);
            rows.revalidate();
            rows.repaint();
        }, failure -> {
            rows.removeAll();
            rows.add(ViewSupport.empty("Notifications are unavailable. Check the backend connection and refresh."));
            status.setText("Could not load notifications.");
            refresh.setEnabled(true);
            rows.revalidate();
            rows.repaint();
        });
    }
}
