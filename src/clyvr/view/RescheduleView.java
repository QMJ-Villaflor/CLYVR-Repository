package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.BackendAppointment;
import clyvr.model.ClinicStore;
import clyvr.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerDateModel;

public class RescheduleView extends JPanel {

    public RescheduleView(AppController app) {
        super(new BorderLayout());
        BackendAppointment appointment = app.databaseReschedulingAppointment();
        JPanel page = ViewSupport.page(
                "Request a reschedule",
                "The existing appointment remains unchanged until reception approves the replacement.");
        if (appointment == null) {
            page.add(ViewSupport.empty("Select a database-backed confirmed appointment to request a reschedule."));
            add(ViewSupport.wrap(page));
            return;
        }

        JPanel panel = Theme.card(new BorderLayout(0, 18));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel content = ViewSupport.stackForm();
        content.add(Theme.label("Current appointment", 18, true));
        Theme.space(content, 8);
        content.add(Theme.muted(appointment.doctorName() + " · " + appointment.summary()));
        Theme.space(content, 20);
        content.add(Theme.label("Choose replacement date", 15, true));
        Theme.space(content, 7);

        LocalDate initial = nextWeekday(appointment.appointmentDate().plusDays(1));
        JSpinner date = new JSpinner(new SpinnerDateModel());
        date.setEditor(new JSpinner.DateEditor(date, "EEEE, MMMM d, yyyy"));
        date.setValue(java.sql.Date.valueOf(initial));
        date.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        date.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(date);
        Theme.space(content, 16);
        content.add(Theme.label("Timeslot (matching visit category)", 15, true));
        Theme.space(content, 7);

        JComboBox<String> slots = new JComboBox<>();
        ArrayList<Integer> indexes = new ArrayList<>();
        for (int slot = 0; slot < ClinicStore.TIMES.length; slot++) {
            if ((slot % 2 == 0) == !"PROCEDURE".equals(appointment.appointmentType())) {
                slots.addItem(ClinicStore.TIMES[slot]);
                indexes.add(slot);
            }
        }
        slots.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        slots.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(slots);
        Theme.space(content, 16);
        content.add(Theme.label("Reason for rescheduling *", 15, true));
        Theme.space(content, 7);
        JTextArea reason = Theme.area(4);
        content.add(new JScrollPane(reason));
        panel.add(content, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        controls.setOpaque(false);
        JButton cancel = Theme.secondary("Cancel");
        cancel.addActionListener(event -> app.navigate("My Appointments"));
        controls.add(cancel);
        JButton submit = Theme.button("Submit reschedule request");
        submit.addActionListener(event -> {
            if (reason.getText().isBlank()) {
                app.error("Please explain why you need a different appointment time.");
                return;
            }
            LocalDate newDay = Instant.ofEpochMilli(
                    ((java.util.Date) date.getValue()).getTime())
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            int slot = indexes.get(slots.getSelectedIndex());
            submit.setEnabled(false);
            app.submitAppointmentChange(
                    appointment.appointmentId(), "reschedule", reason.getText().trim(),
                    newDay, slot, () -> {
                        app.info("Reschedule request saved. The appointment will move only after approval.");
                        app.navigate("My Appointments");
                    }, failure -> {
                        submit.setEnabled(true);
                        app.error("The request was not saved. Check the selected date and slot, then retry.");
                    });
        });
        controls.add(submit);
        panel.add(controls, BorderLayout.SOUTH);
        page.add(panel);
        add(ViewSupport.wrap(page));
    }

    private static LocalDate nextWeekday(LocalDate day) {
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY
                || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }
}
