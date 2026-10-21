package clyvr.view;

import clyvr.controller.AppController;import clyvr.model.ClinicStore;import clyvr.model.Entities.*;import clyvr.ui.Theme;
import javax.swing.*;import java.awt.*;import java.time.*;import java.util.*;

public class PatientApprovalsView extends JPanel {
 public PatientApprovalsView(AppController app){super(new BorderLayout());JPanel p=ViewSupport.page("Doctor proposals","Approve a follow-up/procedure, select another timeslot, or decline entirely.");int count=0;
  for(Appointment a:app.store.appointments)if(a.patientId.equals(app.current.id)&&a.status==Status.PENDING_PATIENT){
   count++;JButton approve=Theme.button("Approve");approve.addActionListener(e->{try{app.store.decide(a,true,"");app.refresh();}catch(Exception ex){app.error(ex.getMessage());}});
   JButton alternative=Theme.secondary("Choose another time");alternative.addActionListener(e->alternate(app,a));
   JButton decline=Theme.danger("Decline");decline.addActionListener(e->{if(app.confirm("Decline this appointment proposal entirely?")){app.store.decide(a,false,"Patient declined proposed visit");app.refresh();}});
   ViewSupport.line(p,app.store.doctorName(a.doctorId),ViewSupport.detail(a),approve,alternative,decline);
  }
  if(count==0)p.add(ViewSupport.empty("No doctor proposals are waiting for your response."));add(ViewSupport.wrap(p));
 }
 private void alternate(AppController app,Appointment a){
  JPanel form=new JPanel(new GridLayout(0,1,5,6));form.add(Theme.label("Select an alternative date",14,true));
  JSpinner day=new JSpinner(new SpinnerDateModel());day.setEditor(new JSpinner.DateEditor(day,"EEEE, MMMM d, yyyy"));day.setValue(java.sql.Date.valueOf(a.day));form.add(day);
  form.add(Theme.label("Select an available matching timeslot",14,true));JComboBox<String> times=new JComboBox<>();ArrayList<Integer> slots=new ArrayList<>();
  for(int i=0;i<8;i++)if(app.store.rightType(a.type,i)){slots.add(i);times.addItem(ClinicStore.TIMES[i]);}form.add(times);
  if(JOptionPane.showConfirmDialog(this,form,"Approve with a different timeslot",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
  LocalDate replacement=Instant.ofEpochMilli(((java.util.Date)day.getValue()).getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
  try{app.store.approveProposalAt(a,replacement,slots.get(times.getSelectedIndex()));app.info("New timeslot confirmed. Your doctor has been notified.");app.refresh();}catch(Exception ex){app.error(ex.getMessage());}
 }
}
