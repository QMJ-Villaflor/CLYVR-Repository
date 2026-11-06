package clyvr.controller;
import clyvr.database.DoctorAvailability;
import clyvr.database.DoctorDirectoryEntry;
import clyvr.database.DoctorScheduleSelection;
import clyvr.database.PendingConsultationRequest;
import clyvr.database.AppointmentDecisionResult;
import clyvr.database.BackendAppointment;
import clyvr.database.BackendAppointmentChangeRequest;
import clyvr.database.BackendNotification;
import clyvr.database.BackendPatientProfile;
import clyvr.database.BackendTimeOffRequest;
import clyvr.database.AppointmentHold;
import clyvr.database.BackendUserSession;
import clyvr.database.PatientProfileUpdate;
import clyvr.service.BackendApiClient;
import clyvr.model.*;import clyvr.model.Entities.*;import clyvr.ui.*;import clyvr.view.*;
import javax.swing.*;import java.awt.*;import java.net.URI;import java.time.*;import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
public class AppController {
 public final ClinicStore store=new ClinicStore(); public User current;private final BackendApiClient backendApiClient=new BackendApiClient(URI.create(System.getenv().getOrDefault("CLYVR_BACKEND_URL","http://127.0.0.1:8080")));private final JFrame frame=new JFrame("CLYVR • Clinic Appointment Management");
 private String page="Dashboard";private Appointment reschedulingAppointment;private BackendAppointment databaseReschedulingAppointment;private String bookingDoctor="";private DoctorDirectoryEntry bookingDoctorEntry;private AppointmentHold bookingHold;private int bookingSlot=0;private long bookingSelectionRequest;private boolean bookingLeaveInProgress;private LocalDate date=LocalDate.now();private String selectedDoctor="";private String service="All";private Long currentDatabaseDoctorId;
 private DoctorScheduleSelection databaseScheduleSelection;private LocalDate databaseDoctorScheduleDate;
 public AppController(){Theme.setup();frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);frame.setMinimumSize(new Dimension(1050,700));frame.setSize(1250,800);frame.setLocationRelativeTo(null);showLogin();frame.setVisible(true);}
 public void showLogin(){frame.setContentPane(new LoginView(this));frame.revalidate();frame.repaint();}
 public void showSignUp(){frame.setContentPane(new SignUpView(this));frame.revalidate();frame.repaint();}
 public void login(String username,String password){
  current=null;
  runBackendTask(()->backendApiClient.login(username,password).get(),account->{
   current=toUser(account);currentDatabaseDoctorId=account.doctorId();page="Dashboard";navigate("Dashboard");
  },failure->error(failure instanceof BackendApiClient.BackendApiException
    ?failure.getMessage():"Unable to sign in through the backend."));
 }
 public void registerPatient(String email,String password,String firstName,String lastName,
         String phone,LocalDate birthDate,String sex,String address){
  runBackendTask(()->backendApiClient.registerPatient(email,password,firstName,lastName,
          phone,birthDate,sex,address).get(),registered->{
   info("Your patient account is ready. Log in with your email address.");
   showLogin();
  },failure->error(failure instanceof BackendApiClient.BackendApiException
    ?failure.getMessage():"Unable to create the account through the backend."));
 }
 public void changePassword(String currentPassword,String newPassword,
         Runnable onSuccess,Consumer<Throwable> onFailure){
  if(current==null){onFailure.accept(new IllegalStateException("A signed-in account is required."));return;}
  runBackendTask(()->backendApiClient.changePassword(currentPassword,newPassword).get(),changed->{
   backendApiClient.logout();current=null;currentDatabaseDoctorId=null;bookingHold=null;
   showLogin();onSuccess.run();
  },onFailure);
 }
 public void logout(){
  bookingSelectionRequest++;
  if(bookingHold!=null&&current!=null){
   AppointmentHold hold=bookingHold;String identity=current.id;bookingHold=null;
   runBackendTask(()->backendApiClient.releaseAppointmentHold(identity,hold.holdId()).get(),
    released->finishLogout(),
    failure->{error("The temporary reservation could not be released. It will expire automatically in five minutes.");finishLogout();});
   return;
  }
  finishLogout();
 }
 private void finishLogout(){backendApiClient.logout();current=null;currentDatabaseDoctorId=null;bookingHold=null;databaseScheduleSelection=null;databaseDoctorScheduleDate=null;showLogin();}
 public void navigate(String dest){
  if("Timeslots".equals(page)&&!"Book Appointment".equals(dest))bookingSelectionRequest++;
  if("Book Appointment".equals(page)&&!"Book Appointment".equals(dest)&&bookingHold!=null){
   if(bookingLeaveInProgress)return;
   bookingLeaveInProgress=true;
   AppointmentHold hold=bookingHold;
   bookingHold=null;
   String identity=current==null?null:current.id;
   if(identity==null){bookingLeaveInProgress=false;navigate(dest);return;}
   runBackendTask(()->backendApiClient.releaseAppointmentHold(identity,hold.holdId()).get(),
    released->{bookingLeaveInProgress=false;navigate(dest);},
    failure->{bookingLeaveInProgress=false;error("The temporary reservation could not be released. It will expire automatically in five minutes.");navigate(dest);});
   return;
  }
  if(current==null){showLogin();return;}
  boolean restricted=switch(dest){case "Requests"->current.role!=Role.RECEPTIONIST;case "Patient Approvals","Book Appointment","Reschedule"->current.role!=Role.PATIENT;case "Doctor Management"->current.role!=Role.ADMIN;case "My Appointments"->current.role==Role.ADMIN;default->false;};
  if(restricted){error("Your role cannot access this screen.");return;}page=dest;JPanel body=switch(dest){case "Dashboard"->switch(current.role){case PATIENT->new PatientDashboardView(this);case DOCTOR->new DoctorDashboardView(this);case RECEPTIONIST->new ReceptionistDashboardView(this);case ADMIN->new AdminDashboardView(this);};case "Schedule"->new ScheduleView(this);case "Timeslots"->databaseDoctorScheduleDate!=null?new TimeslotView(this,databaseDoctorScheduleDate):databaseScheduleSelection==null?new TimeslotView(this):new TimeslotView(this,databaseScheduleSelection);case "Book Appointment"->new BookingView(this);case "Reschedule"->new RescheduleView(this);case "Doctors"->new DoctorsView(this);case "Doctor Management"->new DoctorManagementView(this);case "Services"->new ServicesView(this);case "About Us"->new AboutView(this);case "Notifications"->new NotificationsView(this);case "My Information"->new ProfileView(this);case "Requests"->new RequestQueueView(this);case "Patient Approvals"->new PatientApprovalsView(this);case "My Appointments"->new AppointmentsView(this);case "Assistant"->new AssistantView(this);default->new PatientDashboardView(this);};frame.setContentPane(new ShellView(this,dest,body));frame.revalidate();frame.repaint();}
 public void refresh(){navigate(page);}
 public void showTimeslots(LocalDate d,String doctorId,String serviceName){databaseScheduleSelection=null;databaseDoctorScheduleDate=null;date=d;selectedDoctor=doctorId;service=serviceName;navigate("Timeslots");}
 public void showDatabaseTimeslots(DoctorScheduleSelection selection){databaseScheduleSelection=selection;databaseDoctorScheduleDate=null;date=selection.day();selectedDoctor="";service=selection.service();navigate("Timeslots");}
 public void showDoctorDatabaseSchedule(LocalDate day){databaseScheduleSelection=null;databaseDoctorScheduleDate=day;date=day;navigate("Timeslots");}
 public DoctorScheduleSelection databaseScheduleSelection(){return databaseScheduleSelection;}
 public Appointment reschedulingAppointment(){return reschedulingAppointment;} public void showReschedule(Appointment a){reschedulingAppointment=a;navigate("Reschedule");}
 public BackendAppointment databaseReschedulingAppointment(){return databaseReschedulingAppointment;}
 public void showReschedule(BackendAppointment appointment){databaseReschedulingAppointment=appointment;reschedulingAppointment=null;navigate("Reschedule");}
 public void showBooking(String did,int slot){error("Select a database-backed doctor slot to reserve it.");}
 public void showBooking(DoctorDirectoryEntry doctor,LocalDate day,int slot){showBooking(doctor,day,slot,null);}
 public void showBooking(DoctorDirectoryEntry doctor,LocalDate day,int slot,JButton source){
  if(current==null||current.role!=Role.PATIENT){error("Only patients can reserve consultation slots.");return;}
  Objects.requireNonNull(doctor);Objects.requireNonNull(day);
  long request=++bookingSelectionRequest;
  if(source!=null)source.setEnabled(false);
  String identity=current.id;
  runBackendTask(()->backendApiClient.createAppointmentHold(
          identity,doctor.demoDoctorKey(),day,slot).get(),
   hold->{
    if(request!=bookingSelectionRequest){
     backendApiClient.releaseAppointmentHold(identity,hold.holdId());
     return;
    }
    bookingDoctorEntry=doctor;bookingDoctor=doctor.demoDoctorKey();date=day;bookingSlot=slot;
    bookingHold=hold;
    navigate("Book Appointment");
   },
   failure->{
    if(request==bookingSelectionRequest&&source!=null)source.setEnabled(true);
    if(request==bookingSelectionRequest)error(failure instanceof BackendApiClient.BackendApiException
      ?failure.getMessage():"Unable to reserve this slot through the backend.");
   });
 }
 public String bookingDoctor(){return bookingDoctor;} public DoctorDirectoryEntry bookingDoctorEntry(){return bookingDoctorEntry;} public int bookingSlot(){return bookingSlot;}
 public AppointmentHold bookingHold(){return bookingHold;}
 public void loadMappedDoctors(Consumer<java.util.List<DoctorDirectoryEntry>> onSuccess,Consumer<Throwable> onFailure){
  new SwingWorker<java.util.List<DoctorDirectoryEntry>,Void>(){
   @Override protected java.util.List<DoctorDirectoryEntry> doInBackground() throws Exception{
    return backendApiClient.findDoctors(current.id).get();
   }
   @Override protected void done(){
    try{onSuccess.accept(get());}
    catch(InterruptedException ex){Thread.currentThread().interrupt();onFailure.accept(ex);}
    catch(ExecutionException ex){
     Throwable failure=rootCause(ex);
     if(failure instanceof BackendApiClient.BackendApiException apiFailure
             &&apiFailure.statusCode()==401){
      current=null;currentDatabaseDoctorId=null;bookingHold=null;page="Dashboard";showLogin();
     }
     onFailure.accept(failure);
    }
   }
  }.execute();
 }
 private static Throwable rootCause(Throwable failure){
  Throwable cause=failure;
  while(cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();
  return cause;
 }
 public void loadDatabaseAvailability(String demoPatientKey,LocalDate day,java.util.List<DoctorDirectoryEntry> doctors,Consumer<Map<Long,DoctorAvailability>> onSuccess,Consumer<Throwable> onFailure){
  String patientKey=demoPatientKey;
  java.util.List<DoctorDirectoryEntry> requested=java.util.List.copyOf(doctors);
  new SwingWorker<Map<Long,DoctorAvailability>,Void>(){
   @Override protected Map<Long,DoctorAvailability> doInBackground() throws Exception{
    Map<Long,DoctorAvailability> result=new LinkedHashMap<>();
    for(DoctorDirectoryEntry doctor:requested){
     result.put(doctor.doctorId(),backendApiClient.findAvailability(patientKey,doctor.doctorId(),day).get());
    }
    return Map.copyOf(result);
   }
   @Override protected void done(){
    try{onSuccess.accept(get());}
    catch(InterruptedException ex){Thread.currentThread().interrupt();onFailure.accept(ex);}
     catch(ExecutionException ex){
      Throwable failure=rootCause(ex);
      if(failure instanceof BackendApiClient.BackendApiException apiFailure
              &&apiFailure.statusCode()==401&&current!=null){
       current=null;currentDatabaseDoctorId=null;bookingHold=null;
       databaseScheduleSelection=null;databaseDoctorScheduleDate=null;
       showLogin();
      }
      onFailure.accept(failure);
     }
   }
  }.execute();
 }
 public void loadDoctorAvailability(LocalDate day,Consumer<DoctorAvailability> onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.findDoctorAvailability(identity,day).get(),onSuccess,onFailure);
 }
 public void loadPendingConsultationRequests(Consumer<java.util.List<PendingConsultationRequest>> onSuccess,Consumer<Throwable> onFailure){
  new SwingWorker<java.util.List<PendingConsultationRequest>,Void>(){
   @Override protected java.util.List<PendingConsultationRequest> doInBackground() throws Exception{
    return backendApiClient.findPendingConsultations(current.id).get();
   }
   @Override protected void done(){
    try{onSuccess.accept(get());}
    catch(InterruptedException ex){Thread.currentThread().interrupt();onFailure.accept(ex);}
    catch(ExecutionException ex){onFailure.accept(ex.getCause()==null?ex:ex.getCause());}
   }
  }.execute();
 }
 public void loadPatientProfile(Consumer<BackendPatientProfile> onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.findPatientProfile(identity).get(),onSuccess,onFailure);
 }
 public void savePatientProfile(PatientProfileUpdate update,Consumer<BackendPatientProfile> onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.updatePatientProfile(identity,update).get(),onSuccess,onFailure);
 }
 public void loadAppointments(Consumer<java.util.List<BackendAppointment>> onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.findAppointments(identity).get(),onSuccess,onFailure);
 }
 public void loadAppointment(long appointmentId,Consumer<BackendAppointment> onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.findAppointment(identity,appointmentId).get(),onSuccess,onFailure);
 }
 public void decideAppointment(long appointmentId,boolean approved,String reason,Consumer<AppointmentDecisionResult> onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.decideAppointment(identity,appointmentId,approved,reason).get(),onSuccess,onFailure);
 }
 public void cancelAppointment(long appointmentId,Runnable onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.cancelPatientAppointment(identity,appointmentId).get(),status->{onSuccess.run();},onFailure);
 }
 public void finishAppointment(long appointmentId,String status,Runnable onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.finishDoctorAppointment(identity,appointmentId,status).get(),result->{onSuccess.run();},onFailure);
 }
 public void loadDoctorTimeOff(Consumer<java.util.List<BackendTimeOffRequest>> onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.findDoctorTimeOff(identity).get(),onSuccess,onFailure);
 }
 public void submitTimeOff(LocalDate day,int slot,String reason,Runnable onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.submitTimeOff(identity,day,slot,reason).get(),requestId->{onSuccess.run();},onFailure);
 }
 public void loadPendingTimeOff(Consumer<java.util.List<BackendTimeOffRequest>> onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.findPendingTimeOff(identity).get(),onSuccess,onFailure);
 }
 public void decideTimeOff(long timeOffId,boolean approved,String reason,Runnable onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.decideTimeOff(identity,timeOffId,approved,reason).get(),status->{onSuccess.run();},onFailure);
 }
 public void submitAppointmentChange(long appointmentId,String kind,String details,LocalDate proposedDate,Integer proposedSlot,Runnable onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.submitAppointmentChange(identity,appointmentId,kind,details,proposedDate,proposedSlot).get(),requestId->{onSuccess.run();},onFailure);
 }
 public void loadPendingAppointmentChanges(Consumer<java.util.List<BackendAppointmentChangeRequest>> onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.findPendingAppointmentChanges(identity).get(),onSuccess,onFailure);
 }
 public void decideAppointmentChange(long requestId,boolean approved,String resolution,Runnable onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.decideAppointmentChange(identity,requestId,approved,resolution).get(),status->{onSuccess.run();},onFailure);
 }
 public void decideDoctorProposal(long appointmentId,boolean approved,LocalDate alternativeDate,Integer alternativeSlot,Runnable onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.decideDoctorProposal(identity,appointmentId,approved,alternativeDate,alternativeSlot).get(),status->{onSuccess.run();},onFailure);
 }
 public void submitDoctorProposal(long patientId,Type type,LocalDate day,int slot,String reason,Runnable onSuccess,Consumer<Throwable> onFailure){
  if(current==null||current.role!=Role.DOCTOR){onFailure.accept(new IllegalStateException("Only a doctor can submit a proposal."));return;}
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.createDoctorProposal(identity,patientId,type.name(),day,slot,reason).get(),appointmentId->{onSuccess.run();},onFailure);
 }
 public void loadNotifications(Consumer<java.util.List<BackendNotification>> onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.findNotifications(identity).get(),onSuccess,onFailure);
 }
 public void markNotificationRead(long notificationId,Runnable onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.markNotificationRead(identity,notificationId).get(),isRead->{onSuccess.run();},onFailure);
 }
 public void notifyAppointmentPatient(long appointmentId,String message,Runnable onSuccess,Consumer<Throwable> onFailure){
  String identity=requireSignedInIdentity();
  runBackendTask(()->backendApiClient.notifyAppointmentPatient(identity,appointmentId,message).get(),created->{onSuccess.run();},onFailure);
 }
 private String requireSignedInIdentity(){
  if(current==null)throw new IllegalStateException("A signed-in account is required.");
  return current.id;
 }
 private static User toUser(BackendUserSession account){
  return new User(Long.toString(account.userId()),account.email(),"",account.role(),
          account.firstName(),account.lastName(),account.email(),account.phone());
 }
 private <T> void runBackendTask(Callable<T> operation,Consumer<T> onSuccess,Consumer<Throwable> onFailure){
  new SwingWorker<T,Void>(){
   @Override protected T doInBackground() throws Exception{return operation.call();}
   @Override protected void done(){
    try{onSuccess.accept(get());}
    catch(InterruptedException ex){Thread.currentThread().interrupt();onFailure.accept(ex);}
    catch(ExecutionException ex){onFailure.accept(rootCause(ex));}
   }
  }.execute();
 }
 public LocalDate chosenDate(){return date;}public String chosenDoctor(){return selectedDoctor;}public String chosenService(){return service;}
 public void error(String s){JOptionPane.showMessageDialog(frame,s,"CLYVR",JOptionPane.WARNING_MESSAGE);}public void info(String s){JOptionPane.showMessageDialog(frame,s,"CLYVR",JOptionPane.INFORMATION_MESSAGE);}
 public boolean confirm(String s){return JOptionPane.showConfirmDialog(frame,s,"CLYVR",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
 public String input(String prompt){return JOptionPane.showInputDialog(frame,prompt);}
 public void requestConsultation(long holdId,String reason,JButton submitButton){
  if(current==null||current.role!=Role.PATIENT){error("Only patients can request consultations.");return;}
  User patient=current;
  AppointmentHold hold=bookingHold;
  if(hold==null||hold.holdId()!=holdId){error("The temporary reservation is missing. Please choose the slot again.");return;}
  if(submitButton!=null)submitButton.setEnabled(false);
  runBackendTask(()->backendApiClient.confirmAppointmentHold(patient.id,holdId,reason).get(),
   appointmentId->{
    if(bookingHold!=null&&bookingHold.holdId()==holdId)bookingHold=null;
    if(submitButton!=null)submitButton.setEnabled(true);
    info("Appointment request #"+appointmentId+" was saved. Reception will review it.");
    if(current==patient)navigate("My Appointments");
   },failure->{
    if(submitButton!=null)submitButton.setEnabled(true);
    error(failure instanceof BackendApiClient.BackendApiException
      ?failure.getMessage()
      :"Unable to confirm the temporary reservation through the backend.");
   });
 }
 public String currentDoctorId(){return currentDatabaseDoctorId==null?"":Long.toString(currentDatabaseDoctorId);}
 public static void main(String[] a){SwingUtilities.invokeLater(AppController::new);}
 public String getPage() {
    return page != null ? page : "Dashboard";
}
}