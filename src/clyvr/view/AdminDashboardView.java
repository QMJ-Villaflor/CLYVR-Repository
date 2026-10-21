package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.Entities.*;
import clyvr.ui.Theme;
import javax.swing.*;
import java.awt.*;

public class AdminDashboardView extends JPanel {
    public AdminDashboardView(AppController app){
        super(new BorderLayout());
        JPanel p = ViewSupport.page("Good day, Admin " + app.current.firstName, "Doctor account administration.");

        JLabel header = Theme.label("Doctors in the Clinic", 21, true);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel itemsContainer = DashboardLayout.stack();

        // Metric summary card
        itemsContainer.add(DashboardLayout.itemCard(
            app.store.doctors.size() + " registered doctors",
            "Administrator tools are limited to doctor account management."
        ));
        itemsContainer.add(Box.createVerticalStrut(12));

        // Individual doctor cards
        for (Doctor d : app.store.doctors) {
            itemsContainer.add(DashboardLayout.itemCard(app.store.doctorName(d.id), d.specialty));
            itemsContainer.add(Box.createVerticalStrut(12));
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
        DashboardLayout.action(actions, "+   Add or remove doctor", () -> app.navigate("Doctor Management"));
        DashboardLayout.action(actions, "▤   View doctors directory", () -> app.navigate("Doctors"));
        DashboardLayout.cardBody(right, actions);

        p.add(DashboardLayout.columns(leftCol, right));
        add(ViewSupport.wrap(p));
    }
}