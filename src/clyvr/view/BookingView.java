package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.DoctorDirectoryEntry;
import clyvr.model.ClinicStore;
import clyvr.model.Entities.Doctor;
import clyvr.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/** Patient confirmation form for a database-available consultation slot. */
public class BookingView extends JPanel {
    public BookingView(AppController app) {
        super(new BorderLayout());
        JPanel page = ViewSupport.page(
                "Book an appointment",
                "Submit a consultation request. The backend checks the slot again before saving.");
        page.setBorder(new EmptyBorder(14, 28, 12, 28));
        JPanel form = Theme.card(new BorderLayout(0, 18));
        form.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.setBorder(new EmptyBorder(12, 18, 12, 18));
        form.add(Theme.label("Appointment Information", 24, true), BorderLayout.NORTH);

        DoctorDirectoryEntry selected = app.bookingDoctorEntry();
        JPanel details = ViewSupport.stackForm();
        JPanel notes = ViewSupport.stackForm();
        if (selected != null) {
            ViewSupport.formValue(details, "Service", selected.specialty());
            ViewSupport.formValue(details, "Doctor", selected.displayName());
        } else {
            Doctor fixtureDoctor = app.store.doctor(app.bookingDoctor());
            String specialty = fixtureDoctor == null ? "Consultation" : fixtureDoctor.specialty;
            String doctorName = fixtureDoctor == null
                    ? "Configured doctor" : app.store.doctorName(app.bookingDoctor());
            ViewSupport.formValue(details, "Service", specialty);
            ViewSupport.formValue(details, "Doctor", doctorName);
        }
        ViewSupport.formValue(details, "Date",
                app.chosenDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        ViewSupport.formValue(details, "Timeslot", ClinicStore.TIMES[app.bookingSlot()]);
        javax.swing.JLabel holdStatus = Theme.muted("");
        details.add(holdStatus);
        notes.add(Theme.label("Reason for visit *", 16, true));
        notes.add(Box.createVerticalStrut(8));
        JTextArea reason = Theme.area(7);
        notes.add(new JScrollPane(reason));

        JPanel columns = new JPanel(new GridLayout(1, 2, 30, 10));
        columns.setOpaque(false);
        columns.add(details);
        columns.add(notes);
        form.add(columns, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        controls.setOpaque(false);
        JButton cancel = Theme.secondary("Cancel");
        cancel.addActionListener(event -> app.navigate("Timeslots"));
        controls.add(cancel);
        JButton submit = Theme.button("Send appointment request");
        clyvr.database.AppointmentHold hold = app.bookingHold();
        if (hold == null) {
            holdStatus.setText("No active reservation. Return to the schedule and select a slot.");
            submit.setEnabled(false);
        } else {
            Timer countdown = new Timer(1000, event -> {
                long seconds = Duration.between(OffsetDateTime.now(), hold.expiresAt()).getSeconds();
                if (seconds <= 0) {
                    ((Timer) event.getSource()).stop();
                    holdStatus.setText("Reservation expired. Return to the schedule and select again.");
                    submit.setEnabled(false);
                } else {
                    holdStatus.setText("Temporarily reserved · " + (seconds / 60) + ":"
                            + String.format("%02d", seconds % 60) + " remaining");
                }
            });
            long seconds = Duration.between(OffsetDateTime.now(), hold.expiresAt()).getSeconds();
            if (seconds <= 0) {
                holdStatus.setText("Reservation expired. Return to the schedule and select again.");
                submit.setEnabled(false);
            } else {
                holdStatus.setText("Temporarily reserved · " + (seconds / 60) + ":"
                        + String.format("%02d", seconds % 60) + " remaining");
            }
            if (seconds > 0) countdown.start();
        }
        submit.addActionListener(event -> {
            if (hold == null || !OffsetDateTime.now().isBefore(hold.expiresAt())) {
                app.error("The temporary reservation expired. Please choose the slot again.");
                submit.setEnabled(false);
                return;
            }
            if (reason.getText().isBlank()) {
                app.error("Please provide the reason for your visit.");
                return;
            }
            app.requestConsultation(hold.holdId(), reason.getText().trim(), submit);
        });
        controls.add(submit);
        form.add(controls, BorderLayout.SOUTH);
        page.add(form);
        add(ViewSupport.wrap(page));
    }
}
