package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.BackendAppointment;
import clyvr.model.ClinicStore;
import clyvr.ui.Theme;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;

public class PatientApprovalsView extends JPanel {
    public PatientApprovalsView(AppController app) {
        super(new BorderLayout());
        JPanel page = ViewSupport.page(
                "Doctor proposals",
                "Approve, choose another time, or decline follow-up and procedure proposals.");
        JPanel rows = Theme.vertical();
        rows.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        rows.add(ViewSupport.empty("Loading doctor proposals…"));
        page.add(rows);
        add(ViewSupport.wrap(page));
        load(app, rows);
    }

    private static void load(AppController app, JPanel rows) {
        rows.removeAll();
        app.loadAppointments(appointments -> {
            List<BackendAppointment> proposals = appointments.stream()
                    .filter(appointment -> "PENDING_PATIENT".equals(appointment.status()))
                    .filter(appointment -> "FOLLOW_UP".equals(appointment.appointmentType())
                            || "PROCEDURE".equals(appointment.appointmentType()))
                    .toList();
            for (BackendAppointment proposal : proposals) {
                JButton approve = Theme.button("Approve");
                JButton alternative = Theme.secondary("Choose another time");
                JButton decline = Theme.danger("Decline proposal");
                approve.addActionListener(event -> {
                    approve.setEnabled(false);
                    alternative.setEnabled(false);
                    decline.setEnabled(false);
                    app.decideDoctorProposal(proposal.appointmentId(), true, null, null,
                            () -> {
                                app.info("Proposal confirmed. Your appointment list was refreshed.");
                                app.navigate("Patient Approvals");
                            }, failure -> {
                                approve.setEnabled(true);
                                alternative.setEnabled(true);
                                decline.setEnabled(true);
                                app.error(failure.getMessage());
                            });
                });
                alternative.addActionListener(event -> chooseAlternative(
                        app, proposal, approve, alternative));
                decline.addActionListener(event -> {
                    if (!app.confirm("Decline this proposal? It will no longer reserve its proposed timeslot.")) {
                        return;
                    }
                    approve.setEnabled(false);
                    alternative.setEnabled(false);
                    decline.setEnabled(false);
                    app.decideDoctorProposal(proposal.appointmentId(), false, null, null,
                            () -> {
                                app.info("Proposal declined. The doctor has been notified.");
                                app.navigate("Patient Approvals");
                            }, failure -> {
                                approve.setEnabled(true);
                                alternative.setEnabled(true);
                                decline.setEnabled(true);
                                app.error(failure.getMessage());
                            });
                });
                ViewSupport.line(rows, proposal.doctorName(), proposal.summary(),
                        approve, alternative, decline);
            }
            if (proposals.isEmpty()) {
                rows.add(ViewSupport.empty("No doctor proposals are waiting for your response."));
            }
            rows.revalidate();
            rows.repaint();
        }, failure -> {
            rows.removeAll();
            rows.add(ViewSupport.empty("Doctor proposals could not be loaded from the backend."));
            rows.revalidate();
            rows.repaint();
        });
    }

    private static void chooseAlternative(
            AppController app, BackendAppointment proposal,
            JButton approve, JButton alternative) {
        JPanel form = new JPanel(new GridLayout(0, 1, 5, 6));
        form.add(Theme.label("Select an alternative date", 14, true));
        JSpinner day = new JSpinner(new SpinnerDateModel());
        day.setEditor(new JSpinner.DateEditor(day, "EEEE, MMMM d, yyyy"));
        day.setValue(java.sql.Date.valueOf(proposal.appointmentDate()));
        form.add(day);
        form.add(Theme.label("Select a matching timeslot", 14, true));
        JComboBox<String> times = new JComboBox<>();
        List<Integer> slots = new ArrayList<>();
        boolean procedure = "PROCEDURE".equals(proposal.appointmentType());
        for (int slot = 0; slot < ClinicStore.TIMES.length; slot++) {
            if ((slot % 2 == 1) == procedure) {
                slots.add(slot);
                times.addItem(ClinicStore.TIMES[slot]);
            }
        }
        form.add(times);
        if (JOptionPane.showConfirmDialog(null, form, "Approve with a different timeslot",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        int selectedSlot = slots.get(times.getSelectedIndex());
        LocalDate replacement = Instant.ofEpochMilli(
                ((java.util.Date) day.getValue()).getTime())
                .atZone(ZoneId.systemDefault()).toLocalDate();
        approve.setEnabled(false);
        alternative.setEnabled(false);
        app.decideDoctorProposal(proposal.appointmentId(), true, replacement, selectedSlot,
                () -> {
                    app.info("Alternative timeslot confirmed. Your appointment list was refreshed.");
                    app.navigate("Patient Approvals");
                }, failure -> {
                    approve.setEnabled(true);
                    alternative.setEnabled(true);
                    app.error(failure.getMessage());
                });
    }
}
