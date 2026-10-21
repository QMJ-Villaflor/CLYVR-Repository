package clyvr.view;
import clyvr.controller.AppController;import clyvr.model.Entities.*;import clyvr.ui.Theme;
import javax.swing.*;import java.awt.*;
public class PatientDashboardView extends JPanel {
 public PatientDashboardView(AppController app){super(new BorderLayout());
  JPanel p=ViewSupport.page("Good day, "+app.current.firstName,"Here's what's happening with your appointments.");
  JPanel left=DashboardLayout.section("Upcoming Appointments",false),right=DashboardLayout.section("Quick Actions",true);
  JPanel visits=DashboardLayout.stack();int n=0;for(Appointment a:app.store.appointments){if(!a.patientId.equals(app.current.id)||!a.occupies())continue;n++;DashboardLayout.entry(visits,app.store.doctorName(a.doctorId),ViewSupport.detail(a));}
  if(n==0)visits.add(Theme.muted("You have no upcoming appointments."));DashboardLayout.cardBody(left,visits);
  JPanel actions=DashboardLayout.stack();DashboardLayout.action(actions,"+   Book consultation",()->app.navigate("Schedule"));DashboardLayout.action(actions,"↻   Request reschedule",()->app.navigate("My Appointments"));DashboardLayout.action(actions,"×   Cancel appointment",()->app.navigate("My Appointments"));DashboardLayout.action(actions,"✓   Review doctor proposals",()->app.navigate("Patient Approvals"));DashboardLayout.cardBody(right,actions);
  p.add(DashboardLayout.columns(left,right));add(ViewSupport.wrap(p));
 }
}
