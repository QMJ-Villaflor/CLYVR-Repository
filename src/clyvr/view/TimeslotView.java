package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.ClinicStore;
import clyvr.model.Entities.*;
import clyvr.ui.Theme;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Separate day/timeslot view, laid out as a 4 × 2 grid (no slot list scrolling). */
public class TimeslotView extends JPanel {
    private final AppController app;private final JPanel slots=new JPanel(new GridLayout(2,4,12,12));
    private final JComboBox<DoctorChoice> selector=new JComboBox<>();
    private final LocalDate day;
    public TimeslotView(AppController app){
        super(new BorderLayout());this.app=app;this.day=app.chosenDate();
        JPanel p=ViewSupport.page("Schedule  >  "+day.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))+" Timeslots","Select a time to view its appointment or request a new booking.");p.setBorder(new EmptyBorder(14,28,12,28));
        JPanel header=ViewSupport.row();JButton back=Theme.secondary("← Back to calendar");back.addActionListener(e->app.navigate("Schedule"));header.add(back);
        header.add(Theme.label("Doctor",14,true));for(Doctor d:new ArrayList<>(app.store.doctors)){
            if(app.current.role==Role.DOCTOR&&!d.id.equals(app.currentDoctorId()))continue;
            if(!app.chosenDoctor().isBlank()&&!d.id.equals(app.chosenDoctor()))continue;
            String selected=app.chosenService();if("Unsure — see a physician".equals(selected)&&!d.specialty.equals("General Medicine"))continue;
            if(!"All services".equals(selected)&&!selected.startsWith("Unsure")&&!d.specialty.equals(selected))continue;
            selector.addItem(new DoctorChoice(d.id,app.store.doctorName(d.id),d.specialty));
        }
        selector.setPreferredSize(new Dimension(310,39));header.add(selector);p.add(header);Theme.space(p,10);
        JPanel card=Theme.card(new BorderLayout(0,14));card.setAlignmentX(Component.LEFT_ALIGNMENT);card.setBorder(new EmptyBorder(14,20,14,20));
        JLabel doctorName=Theme.label("",21,true);card.add(doctorName,BorderLayout.NORTH);
        slots.setOpaque(false);card.add(slots,BorderLayout.CENTER);p.add(card);
        if(selector.getItemCount()==0){doctorName.setText("No doctors match this filter");slots.add(Theme.muted("Go back and choose a different filter."));}
        else {selector.addActionListener(e->{DoctorChoice d=(DoctorChoice)selector.getSelectedItem();if(d!=null){doctorName.setText(d.name+"  ·  "+d.specialty);drawSlots(d.id);}});DoctorChoice first=(DoctorChoice)selector.getSelectedItem();doctorName.setText(first.name+"  ·  "+first.specialty);drawSlots(first.id);}
        Theme.space(p,12);p.add(Theme.muted("White: available   ·   Cyan: reserved / booked   ·   Closed: approved time off"));
        add(ViewSupport.wrap(p));
    }
    private void drawSlots(String doctorId){
        slots.removeAll();
        for(int index=0;index<ClinicStore.TIMES.length;index++){
            final int slot=index;Appointment appt=app.store.at(doctorId,day,slot);
            boolean timeOff=app.store.timeOffs.stream().anyMatch(t->t.doctorId.equals(doctorId)&&t.day.equals(day)&&t.slot==slot&&t.status==TimeOffStatus.APPROVED);
            boolean past=appt==null&&!LocalDateTime.now().isBefore(ClinicStore.slotStart(day,slot));
            Type type=index%2==0?Type.CONSULTATION:Type.PROCEDURE;
            String status=timeOff?"UNAVAILABLE":past?"PAST":appt!=null?(app.current.role==Role.PATIENT&&!appt.patientId.equals(app.current.id)?"RESERVED":appt.status.toString().replace('_',' ')):"AVAILABLE";
            String name=type==Type.CONSULTATION?"Consultation / Follow-up":"Procedure / Treatment";
            String html="<html><center><font size='4'><b>"+ClinicStore.TIMES[slot]+"</b></font><br><br>"+name+"<br><b>"+status+"</b></center></html>";
            JButton btn=new Theme.AquaButton(html,appt!=null?Theme.CARD_DARK:(timeOff||past)?new Color(190,217,221):Theme.WHITE,Theme.INK);
            btn.setPreferredSize(new Dimension(145,130));btn.setFont(Theme.font(14,true));btn.setEnabled(!timeOff);
            btn.setToolTipText(timeOff?"This doctor has approved time off":appt!=null?"View booking details":"Open timeslot actions");
            btn.addActionListener(e->{if(past){app.info("This timeslot has already started.");return;}click(doctorId,day,slot,type,appt);});slots.add(btn);
        }
        slots.revalidate();slots.repaint();
    }
    private void click(String did,LocalDate date,int slot,Type type,Appointment a){
        if(a!=null){if(app.current.role==Role.PATIENT&&!a.patientId.equals(app.current.id)){app.info("This timeslot is already reserved.");return;}appointmentDialog(app,a);return;}
        if(app.current.role==Role.PATIENT){
            if(type!=Type.CONSULTATION){app.info("Procedures are proposed by doctors after a consultation.");return;}
            app.showBooking(did,slot);return;
        }
        if(app.current.role==Role.DOCTOR){
            String[] options={"Request time off","Propose "+(type==Type.CONSULTATION?"follow-up":"procedure"),"Back"};
            int result=JOptionPane.showOptionDialog(this,"What would you like to do with "+ClinicStore.TIMES[slot]+"?","Doctor timeslot",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,options,options[0]);
            if(result==0){try{app.store.requestTimeOff(did,date,slot);app.info("Time-off request sent to reception.");app.refresh();}catch(Exception ex){app.error(ex.getMessage());}}
            if(result==1)propose(did,date,slot,type==Type.CONSULTATION?Type.FOLLOW_UP:Type.PROCEDURE);
        }else app.info("This timeslot has no appointment.");
    }
    private void propose(String did,LocalDate date,int slot,Type type){
        ArrayList<User> eligible=new ArrayList<>();for(User u:app.store.users)if(u.role==Role.PATIENT&&app.store.eligible(did,u.id))eligible.add(u);
        if(eligible.isEmpty()){app.error("No patients have a completed consultation history with this doctor.");return;}
        JComboBox<PatientChoice> patients=new JComboBox<>();for(User u:eligible)patients.addItem(new PatientChoice(u.id,u.name()));
        JPanel form=new JPanel(new BorderLayout(7,7));form.add(Theme.label("Choose the patient",14,true),BorderLayout.NORTH);form.add(patients,BorderLayout.CENTER);
        if(JOptionPane.showConfirmDialog(this,form,"Propose "+type.toString().replace('_',' ').toLowerCase(),JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        PatientChoice chosen=(PatientChoice)patients.getSelectedItem();if(chosen!=null)app.propose(chosen.id,did,type,date,slot);
    }
    public static void appointmentDialog(AppController app,Appointment a){
        String text=ViewSupport.display(app,a)+"\n"+ViewSupport.detail(a)+(a.reason.isBlank()?"":"\nNotes: "+a.reason);
        if(app.current.role!=Role.DOCTOR||!app.currentDoctorId().equals(a.doctorId)){app.info(text);return;}
        boolean finishedWindow=LocalDateTime.now().isAfter(ClinicStore.slotEnd(a.day,a.slot));
        if(a.status!=Status.CONFIRMED){app.info(text);return;}
        String[] options=finishedWindow?new String[]{"Mark completed","Mark no-show","Patient medical info","Close"}:new String[]{"Request cancellation","Notify patient to reschedule","Patient medical info","Close"};
        int c=JOptionPane.showOptionDialog(null,text,"Doctor appointment actions",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,options,options[0]);
        try {
            if(finishedWindow&&(c==0||c==1)){app.store.finish(a,c==0);app.refresh();}
            else if(!finishedWindow&&c==0){app.store.requestChange(a,app.current.id,"CANCELLATION","Doctor requests cancellation");app.info("Cancellation request sent to reception.");app.refresh();}
            else if(!finishedWindow&&c==1){app.store.notify(a.patientId,"Dr. requested rescheduling for "+a.id+". Open My Appointments to request a new time.");app.info("The patient was notified to submit a reschedule request.");}
            else if(c==2){User u=app.store.patient(a.patientId);if(u!=null)app.info("Patient: "+u.name()+"\nBlood type: "+u.bloodType+"\nAllergies: "+u.allergies+"\nMedical conditions: "+u.conditions+"\nCurrent medications: "+u.medications+"\nMedical history: "+u.medicalHistory);}
        } catch(Exception ex){app.error(ex.getMessage());}
    }
    private static class DoctorChoice {final String id,name,specialty;DoctorChoice(String id,String name,String specialty){this.id=id;this.name=name;this.specialty=specialty;}public String toString(){return name+"  ·  "+specialty;}}
    private static class PatientChoice {final String id,name;PatientChoice(String id,String name){this.id=id;this.name=name;}public String toString(){return name;}}
}
