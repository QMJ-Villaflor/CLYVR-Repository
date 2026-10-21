package clyvr.view;
import clyvr.controller.AppController;import clyvr.model.Entities.*;import clyvr.ui.Theme;
import javax.swing.*;import java.awt.*;
public class ReceptionistDashboardView extends JPanel {
 public ReceptionistDashboardView(AppController app){super(new BorderLayout());app.store.expire();
  JPanel p=ViewSupport.page("Good day, "+app.current.firstName,"Review appointment and doctor schedule requests.");
  JPanel left=DashboardLayout.section("Pending Requests",false),right=DashboardLayout.section("Quick Actions",true);
  JPanel entries=DashboardLayout.stack();int n=0;
  for(Appointment a:app.store.appointments)if(a.status==Status.PENDING_RECEPTIONIST){n++;DashboardLayout.entry(entries,app.store.patientName(a.patientId),app.store.doctorName(a.doctorId)+"  ·  "+ViewSupport.detail(a));}
  for(TimeOff t:app.store.timeOffs)if(t.status==TimeOffStatus.PENDING){n++;DashboardLayout.entry(entries,"Doctor time off",app.store.doctorName(t.doctorId)+"  ·  "+t.day);}
  for(ChangeRequest r:app.store.changeRequests)if(r.pending){n++;DashboardLayout.entry(entries,r.kind+" request",r.appointmentId+"  ·  "+r.details);}
  if(n==0)entries.add(Theme.muted("No requests are waiting for review."));DashboardLayout.cardBody(left,entries);
  JPanel actions=DashboardLayout.stack();DashboardLayout.action(actions,"✓   Review patient requests",()->app.navigate("Requests"));DashboardLayout.action(actions,"▦   Review doctor requests",()->app.navigate("Requests"));DashboardLayout.action(actions,"▤   Browse appointments",()->app.navigate("My Appointments"));DashboardLayout.cardBody(right,actions);
  p.add(DashboardLayout.columns(left,right));add(ViewSupport.wrap(p));
 }
}
