package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.BackendAppointment;
import clyvr.model.ClinicStore;
import clyvr.model.Entities.Role;
import clyvr.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.time.LocalDateTime;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Appointment records on this screen are read from the backend. */
public class AppointmentsView extends JPanel {

    public AppointmentsView(AppController app) {
        super(new BorderLayout());
        JPanel page = ViewSupport.page(
                "My Appointments",
                "Appointments and change requests are managed through the database backend.");
        JLabel status = Theme.muted("Loading appointments…");
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel rows = Theme.vertical();
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton refresh = Theme.secondary("Refresh");
        refresh.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(status);
        Theme.space(page, 8);
        page.add(rows);
        Theme.space(page, 8);
        page.add(refresh);
        add(ViewSupport.wrap(page));
        refresh.addActionListener(event -> load(app, rows, status, refresh));
        load(app, rows, status, refresh);
    }

    private static void load(
            AppController app, JPanel rows, JLabel status, JButton refresh) {
        refresh.setEnabled(false);
        status.setText("Loading appointments from the database…");
        rows.removeAll();
        rows.revalidate();
        rows.repaint();
        app.loadAppointments(appointments -> {
            rows.removeAll();
            for (BackendAppointment appointment : appointments) {
                addAppointment(app, rows, appointment, status, refresh);
            }
            if (appointments.isEmpty()) {
                rows.add(ViewSupport.empty("There are no appointments for this account."));
                status.setText("Appointment history loaded. No records were found.");
            } else {
                status.setText("Loaded " + appointments.size() + " appointment(s) from the database.");
            }
            refresh.setEnabled(true);
            rows.revalidate();
            rows.repaint();
        }, failure -> {
            status.setText("Could not load appointments. Check the backend connection and refresh.");
            rows.removeAll();
            rows.add(ViewSupport.empty("Appointment data is unavailable; local fixture records are not shown here."));
            refresh.setEnabled(true);
            rows.revalidate();
            rows.repaint();
        });
    }

    private static void addAppointment(
            AppController app,
            JPanel rows,
            BackendAppointment appointment,
            JLabel status,
            JButton refresh) {
        JButton details = Theme.secondary("View details");
        details.addActionListener(event -> {
            details.setEnabled(false);
            app.loadAppointment(appointment.appointmentId(), latest -> {
                details.setEnabled(true);
                showDetails(app, latest);
            }, failure -> {
                details.setEnabled(true);
                app.error("Appointment details could not be loaded.");
            });
        });

        if (app.current.role == Role.PATIENT && "CONFIRMED".equals(appointment.status())) {
            JButton cancel = Theme.danger("Request cancellation");
            JButton reschedule = Theme.secondary("Request reschedule");
            cancel.addActionListener(event -> requestCancellation(
                    app, appointment, cancel, rows, status, refresh));
            reschedule.addActionListener(event -> app.showReschedule(appointment));
            ViewSupport.line(rows, appointment.doctorName(), appointment.summary(),
                    details, cancel, reschedule);
        } else if (app.current.role == Role.DOCTOR
                && "CONFIRMED".equals(appointment.status())
                && !LocalDateTime.now().isBefore(
                        ClinicStore.slotEnd(appointment.appointmentDate(), appointment.slotNumber()))) {
            JButton complete = Theme.button("Mark completed");
            JButton noShow = Theme.secondary("Mark no-show");
            complete.addActionListener(event -> finish(
                    app, appointment, "COMPLETED", complete, noShow, rows, status, refresh));
            noShow.addActionListener(event -> finish(
                    app, appointment, "NO_SHOW", complete, noShow, rows, status, refresh));
            ViewSupport.line(rows, appointment.patientName(), appointment.summary(),
                    details, complete, noShow);
        } else if (app.current.role == Role.DOCTOR
                && "CONFIRMED".equals(appointment.status())) {
            JButton cancellation = Theme.secondary("Request cancellation");
            JButton notify = Theme.secondary("Notify patient to discuss rescheduling");
            cancellation.addActionListener(event -> requestCancellation(
                    app, appointment, cancellation, rows, status, refresh));
            notify.addActionListener(event -> {
                notify.setEnabled(false);
                app.notifyAppointmentPatient(appointment.appointmentId(),
                        "Please contact the clinic to discuss rescheduling appointment #"
                                + appointment.appointmentId() + ".", () -> {
                            notify.setEnabled(true);
                            app.info("Notification saved for the patient.");
                        }, failure -> {
                            notify.setEnabled(true);
                            app.error("The patient notification was not saved.");
                        });
            });
            ViewSupport.line(rows, appointment.patientName(), appointment.summary(),
                    details, cancellation, notify);
        } else {
            String title = app.current.role == Role.DOCTOR
                    ? appointment.patientName() : appointment.doctorName();
            ViewSupport.line(rows, title, appointment.summary(), details);
        }
    }

    private static void requestCancellation(
            AppController app, BackendAppointment appointment, JButton button,
            JPanel rows, JLabel status, JButton refresh) {
        String details = app.input("Reason for requesting cancellation");
        if (details == null) {
            return;
        }
        if (details.isBlank()) {
            app.error("Please provide a reason for the cancellation request.");
            return;
        }
        button.setEnabled(false);
        app.submitAppointmentChange(appointment.appointmentId(), "cancellation",
                details.trim(), null, null, () -> {
                    app.info("Cancellation request saved. The appointment remains active until reception decides.");
                    load(app, rows, status, refresh);
                }, failure -> {
                    button.setEnabled(true);
                    app.error("The cancellation request was not saved. It may already be pending.");
                });
    }

    private static void finish(
            AppController app,
            BackendAppointment appointment,
            String newStatus,
            JButton complete,
            JButton noShow,
            JPanel rows,
            JLabel status,
            JButton refresh) {
        complete.setEnabled(false);
        noShow.setEnabled(false);
        app.finishAppointment(appointment.appointmentId(), newStatus, () -> {
            app.info("Appointment status saved to the database.");
            load(app, rows, status, refresh);
        }, failure -> {
            complete.setEnabled(true);
            noShow.setEnabled(true);
            app.error("The status was not saved. The appointment may not be eligible yet.");
        });
    }

    private static void showDetails(AppController app, BackendAppointment appointment) {
        String person = app.current.role == Role.DOCTOR
                ? "Patient: " + appointment.patientName()
                : "Doctor: " + appointment.doctorName();
        String text = person
                + "\n" + appointment.summary()
                + (appointment.reason().isBlank()
                        ? "" : "\nReason: " + appointment.reason());
        app.info(text);
    }

}
