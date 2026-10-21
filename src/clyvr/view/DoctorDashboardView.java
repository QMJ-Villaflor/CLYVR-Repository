package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.Entities.*;
import clyvr.ui.Theme;
import javax.swing.*;
import java.awt.*;

public class DoctorDashboardView extends JPanel {
    public DoctorDashboardView(AppController app){
        super(new BorderLayout());
        JPanel p = ViewSupport.page("Good day, Dr. " + app.current.lastName, "Here's what's happening with your appointments.");

        // Left section header and stacked cards
        JLabel header = Theme.label("Scheduled Patients", 21, true);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel itemsContainer = DashboardLayout.stack();
        int count = 0;
        for (Appointment a : app.store.appointments) {
            if (!a.doctorId.equals(app.currentDoctorId()) || !a.occupies()) continue;
            count++;
            itemsContainer.add(DashboardLayout.itemCard(app.store.patientName(a.patientId), ViewSupport.detail(a)));
            itemsContainer.add(Box.createVerticalStrut(12));
        }

        if (count == 0) {
            itemsContainer.add(ViewSupport.empty("No upcoming appointments."));
        }

        JScrollPane leftScroll = new JScrollPane(itemsContainer);
        leftScroll.setBorder(null);
        leftScroll.setOpaque(false);
        leftScroll.getViewport().setOpaque(false);
        leftScroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel leftCol = new JPanel(new BorderLayout(0, 12));
        leftCol.setOpaque(false);
        leftCol.add(header, BorderLayout.NORTH);
        leftCol.add(leftScroll, BorderLayout.CENTER);

        // Right section
        JPanel right = DashboardLayout.section("Quick Actions", true);
        JPanel actions = DashboardLayout.stack();
        DashboardLayout.action(actions, "+   Manage my schedule", () -> app.navigate("Schedule"));
        DashboardLayout.action(actions, "↻   Reschedule / cancel", () -> app.navigate("My Appointments"));
        DashboardLayout.action(actions, "✓   Mark visit completed", () -> app.navigate("My Appointments"));
        DashboardLayout.action(actions, "−   Request time off", () -> app.navigate("Schedule"));
        DashboardLayout.cardBody(right, actions);

        p.add(DashboardLayout.columns(leftCol, right));
        add(ViewSupport.wrap(p));
    }
}