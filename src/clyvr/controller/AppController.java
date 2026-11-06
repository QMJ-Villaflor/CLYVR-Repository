package clyvr.controller;
import clyvr.model.*;import clyvr.model.Entities.*;import clyvr.ui.*;import clyvr.view.*;
import javax.swing.*;import java.awt.*;import java.time.*;import java.util.*;
public class AppController {
 public final ClinicStore store=new ClinicStore(); public User current;private final JFrame frame=new JFrame("CLYVR • Clinic Appointment Management");
 private String page="Dashboard";private Appointment reschedulingAppointment;private String bookingDoctor="";private int bookingSlot=0;private LocalDate date=LocalDate.now();private String selectedDoctor="";private String service="All";
 public AppController(){Theme.setup();frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);frame.setMinimumSize(new Dimension(1050,700));frame.setSize(1250,800);frame.setLocationRelativeTo(null);showLogin();frame.setVisible(true);}
 public void showLogin(){frame.setContentPane(new LoginView(this));frame.revalidate();frame.repaint();}
 public void showSignUp(){frame.setContentPane(new SignUpView(this));frame.revalidate();frame.repaint();}
 public void login(String username,String password){
  String key=username==null?"":username.trim().toLowerCase();
  if(key.isEmpty()||password==null||password.isEmpty()){error("Enter your email address and password.");return;}
  long wait=clyvr.security.LoginGuard.secondsLocked(key);
  if(wait>0){error("Too many failed attempts. Try again in "+clyvr.security.LoginGuard.describe(wait)+".");return;}
  current=store.users.stream().filter(u->(u.username.equalsIgnoreCase(username.trim())||u.email.equalsIgnoreCase(username.trim()))&&clyvr.security.FirebaseScrypt.verify(password,u.password)).findFirst().orElse(null);if(current==null){int left=clyvr.security.LoginGuard.recordFailure(key);
   error(left>0?"Invalid username or password. "+left+(left==1?" attempt":" attempts")+" remaining before a temporary lock.":"Too many failed attempts. This account is locked for 5 minutes.");return;}
  clyvr.security.LoginGuard.recordSuccess(key);page="Dashboard";navigate("Dashboard");}
 public void logout(){current=null;showLogin();}
 public void navigate(String dest){
  if(current==null){showLogin();return;}
  boolean restricted=switch(dest){case "Requests"->current.role!=Role.RECEPTIONIST;case "Patient Approvals","Book Appointment","Reschedule"->current.role!=Role.PATIENT;case "Doctor Management"->current.role!=Role.ADMIN;case "My Appointments"->current.role==Role.ADMIN;default->false;};
  if(restricted){error("Your role cannot access this screen.");return;}page=dest;JPanel body=switch(dest){case "Dashboard"->switch(current.role){case PATIENT->new PatientDashboardView(this);case DOCTOR->new DoctorDashboardView(this);case RECEPTIONIST->new ReceptionistDashboardView(this);case ADMIN->new AdminDashboardView(this);};case "Schedule"->new ScheduleView(this);case "Timeslots"->new TimeslotView(this);case "Book Appointment"->new BookingView(this);case "Reschedule"->new RescheduleView(this);case "Doctors"->new DoctorsView(this);case "Doctor Management"->new DoctorManagementView(this);case "Services"->new ServicesView(this);case "About Us"->new AboutView(this);case "Notifications"->new NotificationsView(this);case "My Information"->new ProfileView(this);case "Requests"->new RequestQueueView(this);case "Patient Approvals"->new PatientApprovalsView(this);case "My Appointments"->new AppointmentsView(this);case "Assistant"->new AssistantView(this);default->new PatientDashboardView(this);};frame.setContentPane(new ShellView(this,dest,body));frame.revalidate();frame.repaint();}
 public void refresh(){navigate(page);}
 public void showTimeslots(LocalDate d,String doctorId,String serviceName){date=d;selectedDoctor=doctorId;service=serviceName;navigate("Timeslots");}
 public Appointment reschedulingAppointment(){return reschedulingAppointment;} public void showReschedule(Appointment a){reschedulingAppointment=a;navigate("Reschedule");}
 public void showBooking(String did,int slot){bookingDoctor=did;bookingSlot=slot;navigate("Book Appointment");}
 public String bookingDoctor(){return bookingDoctor;} public int bookingSlot(){return bookingSlot;}
 public LocalDate chosenDate(){return date;}public String chosenDoctor(){return selectedDoctor;}public String chosenService(){return service;}
 public void error(String s){JOptionPane.showMessageDialog(frame,s,"CLYVR",JOptionPane.WARNING_MESSAGE);}public void info(String s){JOptionPane.showMessageDialog(frame,s,"CLYVR",JOptionPane.INFORMATION_MESSAGE);}
 public boolean confirm(String s){return JOptionPane.showConfirmDialog(frame,s,"CLYVR",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
 public String input(String prompt){return JOptionPane.showInputDialog(frame,prompt);}
 public void requestConsultation(String doctor,LocalDate day,int slot){requestConsultation(doctor,day,slot,"Consultation requested");}
 public void requestConsultation(String doctor,LocalDate day,int slot,String reason){if(current.role!=Role.PATIENT){error("Only patients can request consultations.");return;}try{Appointment a=store.book(current.id,doctor,Type.CONSULTATION,day,slot,false);a.reason=reason;info("Consultation requested. The receptionist will review it.");navigate("My Appointments");}catch(Exception ex){error(ex.getMessage());}}
 public void propose(String patientId,String doctor,Type type,LocalDate day,int slot){if(current.role!=Role.DOCTOR||!currentDoctorId().equals(doctor)){error("Only the attending doctor may propose a follow-up/procedure.");return;}try{store.book(patientId,doctor,type,day,slot,true);info("Proposal sent to the patient for approval.");refresh();}catch(Exception ex){error(ex.getMessage());}}
 public String currentDoctorId(){if(current==null)return "";return store.doctors.stream().filter(d->d.userId.equals(current.id)).map(d->d.id).findFirst().orElse("");}
 public static void main(String[] a){SwingUtilities.invokeLater(AppController::new);}
 public String getPage() {
    return page != null ? page : "Dashboard";
}
}