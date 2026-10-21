package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.Entities.*;
import clyvr.ui.Theme;
import javax.swing.*;
import java.awt.*;

public class PatientDashboardView extends JPanel {
    public PatientDashboardView(AppController app){
        super(new BorderLayout());
        JPanel p = ViewSupport.page("Good day, " + app.current.firstName, "Here's what's happening with your appointments.");

        // Left section: Header title + stack of individual item cards
        JPanel left = DashboardLayout.stack();
        JLabel header = Theme.label("Upcoming Appointments", 21, true);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(header);
        left.add(Box.createVerticalStrut(14));

        JPanel itemsContainer = DashboardLayout.stack();
        int count = 0;
        for (Appointment a : app.store.appointments) {
            if (!a.patientId.equals(app.current.id) || !a.occupies()) continue;
            count++;
            itemsContainer.add(DashboardLayout.itemCard(app.store.doctorName(a.doctorId), ViewSupport.detail(a)));
            itemsContainer.add(Box.createVerticalStrut(12));
        }

        if (count == 0) {
            itemsContainer.add(ViewSupport.empty("You have no upcoming appointments."));
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
        DashboardLayout.action(actions, "+   Book consultation", () -> app.navigate("Schedule"));
        DashboardLayout.action(actions, "↻   Request reschedule", () -> app.navigate("My Appointments"));
        DashboardLayout.action(actions, "×   Cancel appointment", () -> app.navigate("My Appointments"));
        DashboardLayout.action(actions, "✓   Review doctor proposals", () -> app.navigate("Patient Approvals"));
        DashboardLayout.cardBody(right, actions);

        p.add(DashboardLayout.columns(leftCol, right));
        add(ViewSupport.wrap(p));
    }
}