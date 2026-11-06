package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.BackendAppointment;
import clyvr.database.BackendAppointmentChangeRequest;
import clyvr.database.BackendTimeOffRequest;
import clyvr.model.ClinicStore;
import clyvr.model.Entities.Appointment;
import clyvr.model.Entities.ChangeRequest;
import clyvr.model.Entities.Status;
import clyvr.model.Entities.TimeOff;
import clyvr.model.Entities.TimeOffStatus;
import clyvr.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class RequestQueueView extends JPanel {
    private final List<SectionBinding> sections = new ArrayList<>();

    public RequestQueueView(AppController app) {
        super(new BorderLayout());
        JPanel page = ViewSupport.page(
                "Request review",
                "Database requests are persisted through the backend. The legacy fixture section is session-only.");
        JButton refresh = Theme.secondary("Refresh all requests");
        refresh.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(refresh);
        Theme.space(page, 12);

        addSection(page, "Consultation requests", app, refresh,
                (rows, status, reload) -> loadConsultations(app, rows, status, reload));
        addSection(page, "Doctor time-off requests", app, refresh,
                (rows, status, reload) -> loadTimeOff(app, rows, status, reload));
        addSection(page, "Appointment cancellation and rescheduling requests", app, refresh,
                (rows, status, reload) -> loadChanges(app, rows, status, reload));
        addLegacyFixtures(page, app);
        refresh.addActionListener(event -> {
            reloadAll();
        });
        add(ViewSupport.wrap(page));
    }

    private void addSection(
            JPanel page, String title, AppController app, JButton refresh, SectionLoader loader) {
        page.add(Theme.label(title, 18, true));
        Theme.space(page, 7);
        JPanel section = new JPanel(new BorderLayout(0, 5));
        section.setOpaque(false);
        JPanel rows = Theme.vertical();
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel status = Theme.muted("Loading…");
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(rows, BorderLayout.CENTER);
        section.add(status, BorderLayout.SOUTH);
        sections.add(new SectionBinding(rows, status, loader));
        page.add(section);
        Theme.space(page, 16);
        loader.load(rows, status, refresh);
    }

    private void reloadAll() {
        for (SectionBinding section : sections) {
            section.loader().load(section.rows(), section.status(), null);
        }
    }

    private void loadConsultations(
            AppController app, JPanel rows, JLabel status, JButton refresh) {
        status.setText("Loading consultation requests…");
        rows.removeAll();
        app.loadAppointments(appointments -> {
            List<BackendAppointment> pending = appointments.stream()
                    .filter(appointment -> "PENDING_RECEPTIONIST".equals(appointment.status()))
                    .filter(appointment -> "CONSULTATION".equals(appointment.appointmentType()))
                    .toList();
            for (BackendAppointment appointment : pending) {
                JButton approve = Theme.button("Approve");
                JButton reject = Theme.secondary("Reject");
                approve.addActionListener(event -> decideConsultation(
                        app, appointment, true, "", rows, status, refresh));
                reject.addActionListener(event -> {
                    String reason = ViewSupport.reasonDialog(this);
                    if (reason != null) {
                        decideConsultation(app, appointment, false, reason, rows, status, refresh);
                    }
                });
                ViewSupport.line(rows,
                        appointment.patientName() + " · " + appointment.doctorName(),
                        appointment.summary(), approve, reject);
            }
            if (pending.isEmpty()) {
                rows.add(ViewSupport.empty("No consultation requests are waiting for review."));
            }
            status.setText("Consultation requests loaded from the database.");
            rows.revalidate();
            rows.repaint();
        }, failure -> showFailure(rows, status, "Consultation requests are unavailable."));
    }

    private void decideConsultation(
            AppController app, BackendAppointment appointment, boolean approved, String reason,
            JPanel rows, JLabel status, JButton refresh) {
        status.setText("Saving consultation decision…");
        app.decideAppointment(appointment.appointmentId(), approved, reason, result -> {
            status.setText("Decision saved.");
            reloadAll();
        }, failure -> {
            status.setText("Decision was not saved. Refresh and retry.");
            app.error("The consultation decision was not saved.");
        });
    }

    private void loadTimeOff(
            AppController app, JPanel rows, JLabel status, JButton refresh) {
        status.setText("Loading pending time-off requests…");
        rows.removeAll();
        app.loadPendingTimeOff(requests -> {
            for (BackendTimeOffRequest request : requests) {
                JButton approve = Theme.button("Approve");
                JButton reject = Theme.secondary("Reject");
                approve.addActionListener(event ->
                        decideTimeOff(app, request, true, "", rows, status, refresh));
                reject.addActionListener(event -> {
                    String reason = ViewSupport.reasonDialog(this);
                    if (reason != null) {
                        decideTimeOff(app, request, false, reason, rows, status, refresh);
                    }
                });
                ViewSupport.line(rows,
                        "Time off · " + request.requestDate(),
                        ClinicStore.TIMES[request.slotNumber()] + " · " + request.reason(),
                        approve, reject);
            }
            if (requests.isEmpty()) {
                rows.add(ViewSupport.empty("No time-off requests are waiting for review."));
            }
            status.setText("Time-off requests loaded from the database.");
            rows.revalidate();
            rows.repaint();
        }, failure -> showFailure(rows, status, "Time-off requests are unavailable."));
    }

    private void decideTimeOff(
            AppController app, BackendTimeOffRequest request, boolean approved, String reason,
            JPanel rows, JLabel status, JButton refresh) {
        status.setText("Saving time-off decision…");
        app.decideTimeOff(request.timeOffId(), approved, reason, () -> {
            status.setText("Decision saved.");
            reloadAll();
        }, failure -> {
            status.setText("Decision was not saved. The slot may now conflict with an appointment.");
            app.error("The time-off decision was not saved.");
        });
    }

    private void loadChanges(
            AppController app, JPanel rows, JLabel status, JButton refresh) {
        status.setText("Loading pending appointment-change requests…");
        rows.removeAll();
        app.loadPendingAppointmentChanges(requests -> {
            for (BackendAppointmentChangeRequest request : requests) {
                JButton approve = Theme.button("Approve");
                JButton reject = Theme.secondary("Reject");
                approve.addActionListener(event ->
                        decideChange(app, request, true, "", rows, status));
                reject.addActionListener(event -> {
                    String reason = ViewSupport.reasonDialog(this);
                    if (reason != null) {
                        decideChange(app, request, false, reason, rows, status);
                    }
                });
                String detail = request.kind() + " · " + request.appointmentDate()
                        + " · " + request.details();
                if (request.proposedDate() != null && request.proposedSlot() != null) {
                    detail += " · replacement " + request.proposedDate()
                            + " · " + ClinicStore.TIMES[request.proposedSlot()];
                }
                ViewSupport.line(rows,
                        request.requesterName() + " · Appointment #" + request.appointmentId()
                                + " · " + request.patientName() + " / " + request.doctorName(),
                        detail, approve, reject);
            }
            if (requests.isEmpty()) {
                rows.add(ViewSupport.empty("No appointment-change requests are waiting for review."));
            }
            status.setText("Appointment-change requests loaded from the database.");
            rows.revalidate();
            rows.repaint();
        }, failure -> showFailure(rows, status,
                "Appointment-change requests are unavailable."));
    }

    private void decideChange(
            AppController app, BackendAppointmentChangeRequest request,
            boolean approved, String resolution, JPanel rows, JLabel status) {
        status.setText("Saving appointment-change decision…");
        app.decideAppointmentChange(request.requestId(), approved, resolution, () -> {
            status.setText("Decision and appointment update saved.");
            reloadAll();
        }, failure -> {
            status.setText("Decision was not saved. The appointment may have changed or the slot is occupied.");
            app.error("The appointment-change decision was not saved.");
        });
    }

    private void addLegacyFixtures(JPanel page, AppController app) {
        page.add(Theme.label("Legacy session fixtures (not persisted)", 18, true));
        Theme.space(page, 7);
        int count = 0;
        app.store.expire();
        for (Appointment appointment : app.store.appointments) {
            if (appointment.status == Status.PENDING_RECEPTIONIST && !appointment.persisted) {
                count++;
                ViewSupport.line(page, ViewSupport.display(app, appointment),
                        ViewSupport.detail(appointment) + " · Session fixture only");
            }
        }
        for (TimeOff request : app.store.timeOffs) {
            if (request.status == TimeOffStatus.PENDING) {
                count++;
                ViewSupport.line(page, app.store.doctorName(request.doctorId) + " · Time off",
                        request.day + " · " + ClinicStore.TIMES[request.slot] + " · Session fixture only");
            }
        }
        for (ChangeRequest request : app.store.changeRequests) {
            if (request.pending) {
                count++;
                ViewSupport.line(page, request.kind + " · " + request.appointmentId,
                        request.details + " · Session fixture only");
            }
        }
        if (count == 0) {
            page.add(ViewSupport.empty("No legacy session fixtures are awaiting review."));
        }
    }

    private static void showFailure(JPanel rows, JLabel status, String message) {
        rows.removeAll();
        rows.add(ViewSupport.empty(message + " Refresh to retry."));
        status.setText(message);
        rows.revalidate();
        rows.repaint();
    }

    @FunctionalInterface
    private interface SectionLoader {
        void load(JPanel rows, JLabel status, JButton refresh);
    }

    private record SectionBinding(JPanel rows, JLabel status, SectionLoader loader) {
    }
}
