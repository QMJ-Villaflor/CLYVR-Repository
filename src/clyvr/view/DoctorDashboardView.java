package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.BackendAppointment;
import clyvr.ui.Theme;
import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

public class DoctorDashboardView extends JPanel {
    public DoctorDashboardView(AppController app){
        super(new BorderLayout());
        JPanel p = ViewSupport.page("Good day, Dr. " + app.current.lastName, "Here's what's happening with your appointments.");

        JLabel header = Theme.label("Scheduled Patients", 21, true);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel itemsContainer = DashboardLayout.stack();
        JLabel status = Theme.muted("Loading appointments from the database…");
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton refresh = Theme.secondary("Refresh");
        refresh.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane leftScroll = new JScrollPane(itemsContainer);
        leftScroll.setBorder(null);
        leftScroll.setOpaque(false);
        leftScroll.getViewport().setOpaque(false);
        leftScroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel leftCol = new JPanel(new BorderLayout(0, 12));
        leftCol.setOpaque(false);
        leftCol.add(header, BorderLayout.NORTH);
        leftCol.add(leftScroll, BorderLayout.CENTER);
        leftCol.add(status, BorderLayout.SOUTH);

        JPanel right = DashboardLayout.section("Quick Actions", true);
        JPanel actions = DashboardLayout.stack();
        DashboardLayout.action(actions, "+   View my schedule", () -> app.navigate("Schedule"));
        DashboardLayout.action(actions, "↻   Manage appointments", () -> app.navigate("My Appointments"));
        DashboardLayout.action(actions, "−   Request time off", () -> app.navigate("Schedule"));
        DashboardLayout.action(actions, "●   Notifications", () -> app.navigate("Notifications"));
        actions.add(refresh);
        DashboardLayout.cardBody(right, actions);

        p.add(DashboardLayout.columns(leftCol, right));
        add(ViewSupport.wrap(p));
        refresh.addActionListener(event -> load(app, itemsContainer, status, refresh));
        load(app, itemsContainer, status, refresh);
    }

    private static void load(
            AppController app, JPanel items, JLabel status, JButton refresh) {
        refresh.setEnabled(false);
        status.setText("Loading appointments from the database…");
        items.removeAll();
        items.revalidate();
        items.repaint();
        app.loadAppointments(appointments -> {
            List<BackendAppointment> scheduled = appointments.stream()
                    .filter(DoctorDashboardView::occupies)
                    .filter(appointment -> !appointment.appointmentDate().isBefore(LocalDate.now()))
                    .toList();
            for (BackendAppointment appointment : scheduled) {
                items.add(DashboardLayout.itemCard(
                        appointment.patientName(), appointment.summary()));
                items.add(Box.createVerticalStrut(12));
            }
            if (scheduled.isEmpty()) {
                items.add(ViewSupport.empty("No upcoming appointments."));
            }
            status.setText("Loaded " + scheduled.size() + " upcoming appointment(s) from the database.");
            refresh.setEnabled(true);
            items.revalidate();
            items.repaint();
        }, failure -> {
            items.removeAll();
            items.add(ViewSupport.empty("Database appointments are unavailable; demo fixtures are not shown."));
            status.setText("Could not load appointments. Check the backend connection and refresh.");
            refresh.setEnabled(true);
            items.revalidate();
            items.repaint();
        });
    }

    private static boolean occupies(BackendAppointment appointment) {
        return "PENDING_RECEPTIONIST".equals(appointment.status())
                || "PENDING_PATIENT".equals(appointment.status())
                || "CONFIRMED".equals(appointment.status());
    }
}