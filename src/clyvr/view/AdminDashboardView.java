package clyvr.view;
import clyvr.controller.AppController;import clyvr.model.Entities.*;import clyvr.ui.Theme;
import javax.swing.*;import java.awt.*;
public class AdminDashboardView extends JPanel {
 public AdminDashboardView(AppController app){super(new BorderLayout());
  JPanel p=ViewSupport.page("Good day, Admin "+app.current.firstName,"Doctor account administration.");
  JPanel left=DashboardLayout.section("Doctors in the Clinic",false),right=DashboardLayout.section("Quick Actions",true);
  JPanel entries=DashboardLayout.stack();DashboardLayout.entry(entries,app.store.doctors.size()+" registered doctors","Administrator tools are limited to doctor account management.");
  for(Doctor d:app.store.doctors)DashboardLayout.entry(entries,app.store.doctorName(d.id),d.specialty);DashboardLayout.cardBody(left,entries);
  JPanel actions=DashboardLayout.stack();DashboardLayout.action(actions,"+   Add or remove doctor",()->app.navigate("Doctor Management"));DashboardLayout.action(actions,"▤   View doctors directory",()->app.navigate("Doctors"));DashboardLayout.cardBody(right,actions);
  p.add(DashboardLayout.columns(left,right));add(ViewSupport.wrap(p));
 }
}
