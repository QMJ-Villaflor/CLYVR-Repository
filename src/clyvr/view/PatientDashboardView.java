package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.BackendAppointment;
import clyvr.model.Entities.Status;
import clyvr.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.time.LocalDate;
import java.util.List;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class PatientDashboardView extends JPanel {
    public PatientDashboardView(AppController app) {
        super(new BorderLayout());
        JPanel page = ViewSupport.page(
                "Good day, " + app.current.firstName,
                "Here's what's happening with your appointments.");

        JLabel header = Theme.label("Upcoming Appointments", 21, true);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel items = DashboardLayout.stack();
        JLabel status = Theme.muted("Loading appointments from the database…");
        status.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane scroll = new JScrollPane(items);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        left.add(header, BorderLayout.NORTH);
        left.add(scroll, BorderLayout.CENTER);
        left.add(status, BorderLayout.SOUTH);

        JPanel right = DashboardLayout.section("Quick Actions", true);
        JPanel actions = DashboardLayout.stack();
        DashboardLayout.action(actions, "+   Book consultation", () -> app.navigate("Schedule"));
        DashboardLayout.action(actions, "↻   Request reschedule", () -> app.navigate("My Appointments"));
        DashboardLayout.action(actions, "×   Cancel appointment", () -> app.navigate("My Appointments"));
        DashboardLayout.action(actions, "✓   Review doctor proposals", () -> app.navigate("Patient Approvals"));
        DashboardLayout.cardBody(right, actions);

        page.add(DashboardLayout.columns(left, right));
        add(ViewSupport.wrap(page));
        load(app, items, status);
    }

    private static void load(AppController app, JPanel items, JLabel status) {
        items.removeAll();
        app.loadAppointments(appointments -> {
            List<BackendAppointment> upcoming = appointments.stream()
                    .filter(PatientDashboardView::occupies)
                    .filter(appointment -> !appointment.appointmentDate().isBefore(LocalDate.now()))
                    .toList();
            for (BackendAppointment appointment : upcoming) {
                items.add(DashboardLayout.itemCard(
                        appointment.doctorName(), appointment.summary()));
                items.add(Box.createVerticalStrut(12));
            }
            if (upcoming.isEmpty()) {
                items.add(ViewSupport.empty("You have no upcoming appointments."));
            }
            status.setText("Loaded " + upcoming.size()
                    + " upcoming appointment(s) from the database.");
            items.revalidate();
            items.repaint();
        }, failure -> {
            items.removeAll();
            items.add(ViewSupport.empty("Database appointments are unavailable."));
            status.setText("Could not load appointments. Check the backend connection and refresh.");
            items.revalidate();
            items.repaint();
        });
    }

    private static boolean occupies(BackendAppointment appointment) {
        return Status.PENDING_RECEPTIONIST.name().equals(appointment.status())
                || Status.PENDING_PATIENT.name().equals(appointment.status())
                || Status.CONFIRMED.name().equals(appointment.status());
    }
}
