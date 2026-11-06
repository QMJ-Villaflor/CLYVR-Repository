package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.BackendAppointment;
import clyvr.database.BackendAppointmentChangeRequest;
import clyvr.database.BackendTimeOffRequest;
import clyvr.model.ClinicStore;
import clyvr.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.time.LocalDate;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class ReceptionistDashboardView extends JPanel {
    public ReceptionistDashboardView(AppController app) {
        super(new BorderLayout());
        JPanel page = ViewSupport.page(
                "Good day, " + app.current.firstName,
                "Review appointment and doctor schedule requests.");

        JLabel header = Theme.label("Pending Requests", 21, true);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel requests = DashboardLayout.stack();
        RequestSection consultations = section(requests, "Consultation requests");
        RequestSection timeOff = section(requests, "Doctor time-off requests");
        RequestSection changes = section(requests, "Appointment cancellation and rescheduling");
        JLabel fixtureNote = Theme.muted(
                "Legacy fixture requests remain session-only and are listed in Request review.");
        fixtureNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        requests.add(fixtureNote);

        JScrollPane scroll = new JScrollPane(requests);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        left.add(header, BorderLayout.NORTH);
        left.add(scroll, BorderLayout.CENTER);

        JButton refresh = Theme.secondary("Refresh");
        JPanel right = DashboardLayout.section("Quick Actions", true);
        JPanel actions = DashboardLayout.stack();
        DashboardLayout.action(actions, "✓   Review patient requests", () -> app.navigate("Requests"));
        DashboardLayout.action(actions, "▦   Review doctor requests", () -> app.navigate("Requests"));
        DashboardLayout.action(actions, "▤   Browse appointments", () -> app.navigate("My Appointments"));
        actions.add(refresh);
        DashboardLayout.cardBody(right, actions);

        page.add(DashboardLayout.columns(left, right));
        add(ViewSupport.wrap(page));
        refresh.addActionListener(event ->
                load(app, consultations, timeOff, changes));
        load(app, consultations, timeOff, changes);
    }

    private static RequestSection section(JPanel parent, String title) {
        JPanel group = DashboardLayout.stack();
        JLabel header = Theme.label(title, 17, true);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel rows = DashboardLayout.stack();
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel status = Theme.muted("Loading…");
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(header);
        group.add(status);
        group.add(rows);
        group.add(Box.createVerticalStrut(12));
        parent.add(group);
        parent.add(Box.createVerticalStrut(8));
        return new RequestSection(rows, status);
    }

    private static void load(
            AppController app,
            RequestSection consultations, RequestSection timeOff, RequestSection changes) {
        loadConsultations(app, consultations.rows(), consultations.status());
        loadTimeOff(app, timeOff.rows(), timeOff.status());
        loadChanges(app, changes.rows(), changes.status());
    }

    private static void loadConsultations(AppController app, JPanel rows, JLabel status) {
        rows.removeAll();
        app.loadAppointments(appointments -> {
            List<BackendAppointment> pending = appointments.stream()
                    .filter(appointment -> "PENDING_RECEPTIONIST".equals(appointment.status()))
                    .filter(appointment -> "CONSULTATION".equals(appointment.appointmentType()))
                    .toList();
            for (BackendAppointment appointment : pending) {
                rows.add(DashboardLayout.itemCard(
                        appointment.patientName() + " · " + appointment.doctorName(),
                        appointment.summary()));
                rows.add(Box.createVerticalStrut(8));
            }
            if (pending.isEmpty()) {
                rows.add(ViewSupport.empty("No consultation requests are waiting for review."));
            }
            status.setText("Loaded " + pending.size() + " database request(s).");
            rows.revalidate();
            rows.repaint();
        }, failure -> showFailure(rows, status, "Consultation requests are unavailable."));
    }

    private static void loadTimeOff(AppController app, JPanel rows, JLabel status) {
        rows.removeAll();
        app.loadPendingTimeOff(requests -> {
            for (BackendTimeOffRequest request : requests) {
                rows.add(DashboardLayout.itemCard(
                        "Time off · " + request.requestDate(),
                        ClinicStore.TIMES[request.slotNumber()] + " · " + request.reason()));
                rows.add(Box.createVerticalStrut(8));
            }
            if (requests.isEmpty()) {
                rows.add(ViewSupport.empty("No time-off requests are waiting for review."));
            }
            status.setText("Loaded " + requests.size() + " database request(s).");
            rows.revalidate();
            rows.repaint();
        }, failure -> showFailure(rows, status, "Time-off requests are unavailable."));
    }

    private static void loadChanges(AppController app, JPanel rows, JLabel status) {
        rows.removeAll();
        app.loadPendingAppointmentChanges(requests -> {
            for (BackendAppointmentChangeRequest request : requests) {
                String date = request.proposedDate() == null
                        ? request.appointmentDate().toString()
                        : request.proposedDate().toString();
                rows.add(DashboardLayout.itemCard(
                        request.kind() + " · " + request.patientName()
                                + " / " + request.doctorName(),
                        "Appointment #" + request.appointmentId() + " · " + date
                                + " · " + request.details()));
                rows.add(Box.createVerticalStrut(8));
            }
            if (requests.isEmpty()) {
                rows.add(ViewSupport.empty(
                        "No appointment-change requests are waiting for review."));
            }
            status.setText("Loaded " + requests.size() + " database request(s).");
            rows.revalidate();
            rows.repaint();
        }, failure -> showFailure(rows, status,
                "Appointment-change requests are unavailable."));
    }

    private static void showFailure(JPanel rows, JLabel status, String message) {
        rows.removeAll();
        rows.add(ViewSupport.empty(message + " Refresh to retry."));
        status.setText(message);
        rows.revalidate();
        rows.repaint();
    }

    private record RequestSection(JPanel rows, JLabel status) {
    }
}
