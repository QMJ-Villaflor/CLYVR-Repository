package clyvr.view;
import clyvr.controller.AppController;import clyvr.model.Entities.*;import clyvr.ui.Theme;
import javax.swing.*;import java.awt.*;
public class DoctorDashboardView extends JPanel {
 public DoctorDashboardView(AppController app){super(new BorderLayout());
  JPanel p=ViewSupport.page("Good day, Dr. "+app.current.lastName,"Here's what's happening with your appointments.");
  JPanel left=DashboardLayout.section("Scheduled Patients",false),right=DashboardLayout.section("Quick Actions",true);
  JPanel visits=DashboardLayout.stack();int n=0;for(Appointment a:app.store.appointments){if(!a.doctorId.equals(app.currentDoctorId())||!a.occupies())continue;n++;DashboardLayout.entry(visits,app.store.patientName(a.patientId),ViewSupport.detail(a));}
  if(n==0)visits.add(Theme.muted("No upcoming appointments."));DashboardLayout.cardBody(left,visits);
  JPanel actions=DashboardLayout.stack();DashboardLayout.action(actions,"+   Manage my schedule",()->app.navigate("Schedule"));DashboardLayout.action(actions,"↻   Reschedule / cancel",()->app.navigate("My Appointments"));DashboardLayout.action(actions,"✓   Mark visit completed",()->app.navigate("My Appointments"));DashboardLayout.action(actions,"−   Request time off",()->app.navigate("Schedule"));DashboardLayout.cardBody(right,actions);
  p.add(DashboardLayout.columns(left,right));add(ViewSupport.wrap(p));
 }
}
