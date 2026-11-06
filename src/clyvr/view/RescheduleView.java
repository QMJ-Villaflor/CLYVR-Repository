package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.ClinicStore;
import clyvr.model.Entities.*;
import clyvr.ui.Theme;
import java.awt.*;
import java.time.*;
import java.util.*;
import javax.swing.*;

/** Requests an actual replacement time; reception approval moves the appointment. */
public class RescheduleView extends JPanel {
 public RescheduleView(AppController app){super(new BorderLayout());Appointment a=app.reschedulingAppointment();
  JPanel p=ViewSupport.page("Request a reschedule","The original appointment stays reserved until reception approves the replacement.");
  JPanel panel=Theme.card(new BorderLayout(0,18));panel.setAlignmentX(Component.LEFT_ALIGNMENT);
  JPanel content=ViewSupport.stackForm();content.add(Theme.label("Current appointment",18,true));content.add(Box.createVerticalStrut(10));content.add(Theme.muted(app.store.doctorName(a.doctorId)+"  ·  "+ViewSupport.detail(a)));content.add(Box.createVerticalStrut(25));
  content.add(Theme.label("Choose replacement date",15,true));content.add(Box.createVerticalStrut(7));
  JSpinner date=new JSpinner(new SpinnerDateModel());date.setEditor(new JSpinner.DateEditor(date,"EEEE, MMMM d, yyyy"));
  LocalDate initial=a.day.plusDays(1);if(initial.isAfter(LocalDate.now().plusDays(30)))initial=LocalDate.now().plusDays(1);
  date.setValue(java.sql.Date.valueOf(initial));date.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));date.setAlignmentX(Component.LEFT_ALIGNMENT);content.add(date);content.add(Box.createVerticalStrut(16));
  content.add(Theme.label("Timeslot (matching visit category)",15,true));content.add(Box.createVerticalStrut(7));
  JComboBox<String> slots=new JComboBox<>();ArrayList<Integer> indexes=new ArrayList<>();for(int i=0;i<8;i++)if(app.store.rightType(a.type,i)){slots.addItem(ClinicStore.TIMES[i]);indexes.add(i);}slots.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));slots.setAlignmentX(Component.LEFT_ALIGNMENT);content.add(slots);content.add(Box.createVerticalStrut(16));
  content.add(Theme.label("Reason for rescheduling *",15,true));content.add(Box.createVerticalStrut(7));JTextArea reason=Theme.area(4);content.add(new JScrollPane(reason));panel.add(content,BorderLayout.CENTER);
  JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,0));controls.setOpaque(false);JButton cancel=Theme.secondary("Cancel");cancel.addActionListener(e->app.navigate("My Appointments"));controls.add(cancel);
  JButton send=Theme.button("Submit reschedule request");send.addActionListener(e->{
   String reasonError=clyvr.security.Validator.longText("Reason for rescheduling",reason.getText());if(reasonError!=null){app.error(reasonError);return;}
   LocalDate day=Instant.ofEpochMilli(((java.util.Date)date.getValue()).getTime()).atZone(ZoneId.systemDefault()).toLocalDate();int slot=indexes.get(slots.getSelectedIndex());
   try{app.store.requestReschedule(a,app.current.id,day,slot,reason.getText().trim());app.info("Replacement timeslot reserved pending receptionist approval.");app.navigate("My Appointments");}
   catch(Exception ex){app.error(ex.getMessage());}
  });controls.add(send);panel.add(controls,BorderLayout.SOUTH);p.add(panel);add(ViewSupport.wrap(p));
 }
}
