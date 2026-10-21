package clyvr.view;
import clyvr.controller.AppController;import clyvr.model.Entities.*;import clyvr.ui.Theme;
import javax.swing.*;import java.awt.*;
public class AppointmentsView extends JPanel {
 public AppointmentsView(AppController app){super(new BorderLayout());JPanel p=ViewSupport.page("My Appointments","Upcoming, pending, and completed visit history");
  int count=0;for(Appointment a:app.store.appointments){boolean canSee=app.current.role==Role.PATIENT?a.patientId.equals(app.current.id):app.current.role==Role.DOCTOR?a.doctorId.equals(app.currentDoctorId()):true;
   if(!canSee)continue;count++;
   JButton details=Theme.secondary("View details");details.addActionListener(e->TimeslotView.appointmentDialog(app,a));
   String title=app.current.role==Role.PATIENT?app.store.doctorName(a.doctorId):app.current.role==Role.DOCTOR?app.store.patientName(a.patientId):ViewSupport.display(app,a);
   if(app.current.role==Role.PATIENT&&a.status==Status.CONFIRMED){
     JButton res=Theme.button("Reschedule");res.addActionListener(e->app.showReschedule(a));
     JButton cancel=Theme.danger("Cancel");cancel.addActionListener(e->{if(app.confirm("Cancel appointment "+a.id+"?")){try{app.store.cancel(a,"Cancelled by patient");app.refresh();}catch(Exception ex){app.error(ex.getMessage());}}});
     ViewSupport.line(p,title,ViewSupport.detail(a),details,res,cancel);
   }else ViewSupport.line(p,title,ViewSupport.detail(a),details);
  }
  if(count==0)p.add(ViewSupport.empty("There are no appointments for this account."));add(ViewSupport.wrap(p));
 }
}
