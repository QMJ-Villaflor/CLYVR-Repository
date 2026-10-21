package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.Entities.*;
import clyvr.ui.Theme;
import javax.swing.*;
import java.awt.*;

public class ReceptionistDashboardView extends JPanel {
    public ReceptionistDashboardView(AppController app){
        super(new BorderLayout());
        app.store.expire();
        JPanel p = ViewSupport.page("Good day, " + app.current.firstName, "Review appointment and doctor schedule requests.");

        JLabel header = Theme.label("Pending Requests", 21, true);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel itemsContainer = DashboardLayout.stack();
        int count = 0;

        for (Appointment a : app.store.appointments) {
            if (a.status == Status.PENDING_RECEPTIONIST) {
                count++;
                itemsContainer.add(DashboardLayout.itemCard(
                    app.store.patientName(a.patientId),
                    app.store.doctorName(a.doctorId) + "  ·  " + ViewSupport.detail(a)
                ));
                itemsContainer.add(Box.createVerticalStrut(12));
            }
        }

        for (TimeOff t : app.store.timeOffs) {
            if (t.status == TimeOffStatus.PENDING) {
                count++;
                itemsContainer.add(DashboardLayout.itemCard(
                    "Doctor time off",
                    app.store.doctorName(t.doctorId) + "  ·  " + t.day
                ));
                itemsContainer.add(Box.createVerticalStrut(12));
            }
        }

        for (ChangeRequest r : app.store.changeRequests) {
            if (r.pending) {
                count++;
                itemsContainer.add(DashboardLayout.itemCard(
                    r.kind + " request",
                    r.appointmentId + "  ·  " + r.details
                ));
                itemsContainer.add(Box.createVerticalStrut(12));
            }
        }

        if (count == 0) {
            itemsContainer.add(ViewSupport.empty("No requests are waiting for review."));
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
        DashboardLayout.action(actions, "✓   Review patient requests", () -> app.navigate("Requests"));
        DashboardLayout.action(actions, "▦   Review doctor requests", () -> app.navigate("Requests"));
        DashboardLayout.action(actions, "▤   Browse appointments", () -> app.navigate("My Appointments"));
        DashboardLayout.cardBody(right, actions);

        p.add(DashboardLayout.columns(leftCol, right));
        add(ViewSupport.wrap(p));
    }
}