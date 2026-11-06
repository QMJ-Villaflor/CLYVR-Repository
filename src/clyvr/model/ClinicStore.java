package clyvr.model;

import clyvr.model.Entities.*;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

/** Text files provide read-only fixtures; actions mutate this session's state. */
public class ClinicStore {
 public final List<User> users=new ArrayList<>();
 public final List<Doctor> doctors=new ArrayList<>();
 public final List<Appointment> appointments=new ArrayList<>();
 public final List<TimeOff> timeOffs=new ArrayList<>();
 public final List<ChangeRequest> changeRequests=new ArrayList<>();
 public final List<Notice> notices=new ArrayList<>();
 public static final String[] TIMES={"8:00–8:30 AM","8:45–9:45 AM","10:00–10:30 AM","10:45–11:45 AM","12:45–1:15 PM","1:30–2:30 PM","2:45–3:15 PM","3:30–4:30 PM"};
 public static LocalDateTime slotStart(LocalDate day,int slot){LocalTime[] starts={LocalTime.of(8,0),LocalTime.of(8,45),LocalTime.of(10,0),LocalTime.of(10,45),LocalTime.of(12,45),LocalTime.of(13,30),LocalTime.of(14,45),LocalTime.of(15,30)};return day.atTime(starts[slot]);}
 public static LocalDateTime slotEnd(LocalDate day,int slot){LocalTime[] ends={LocalTime.of(8,30),LocalTime.of(9,45),LocalTime.of(10,30),LocalTime.of(11,45),LocalTime.of(13,15),LocalTime.of(14,30),LocalTime.of(15,15),LocalTime.of(16,30)};return day.atTime(ends[slot]);}
 public static final String[] SERVICES={"General Medicine","Obstetrics and Gynecology","Pediatrics","Odontology"};
 private int sequence=1000; private final Map<String,String> formerDoctors=new HashMap<>();
 public ClinicStore(){load();}
 private List<String[]> lines(String file){
  Path p=Path.of("data",file); if(!Files.exists(p))p=Path.of("CLYVR_60","data",file);
  try {return Files.readAllLines(p).stream().filter(s->!s.isBlank()&&!s.trim().startsWith("#")).map(s->s.split("\\|",-1)).toList();}
  catch(IOException e){throw new IllegalStateException("Missing seed data: "+p.toAbsolutePath(),e);}
 }
 private void load(){
  for(String[] a:lines("users.txt"))users.add(new User(a[0],a[1],clyvr.security.FirebaseScrypt.isHashed(a[2])?a[2]:clyvr.security.FirebaseScrypt.hash(a[2]),Role.valueOf(a[3]),a[4],a[5],a[6],a[7]));
  for(String[] a:lines("doctors.txt"))doctors.add(new Doctor(a[0],a[1],a[2]));
  for(String[] a:lines("appointments.txt")){
   LocalDate day=LocalDate.now().plusDays(Integer.parseInt(a[4]));
   if(!a[6].equals("COMPLETED")&&!a[6].equals("NO_SHOW"))while(!clinicOpen(day))day=day.plusDays(1);
   LocalDateTime made=LocalDateTime.now().minusDays(Long.parseLong(a[7]));
   appointments.add(new Appointment(a[0],a[1],a[2],Type.valueOf(a[3]),day,Integer.parseInt(a[5]),Status.valueOf(a[6]),made));
  }
  expire();
  for(User u:users){if(u.role==Role.PATIENT)notify(u.id,"Welcome to CLYVR. Your clinic schedule is ready.");}
 }
 public String next(String prefix){return prefix+(++sequence);}
 public User user(String id){return users.stream().filter(u->u.id.equals(id)).findFirst().orElse(null);}
 public Doctor doctor(String id){return doctors.stream().filter(d->d.id.equals(id)).findFirst().orElse(null);}
 public String doctorName(String id){Doctor d=doctor(id);User u=d==null?null:user(d.userId);return u==null?formerDoctors.getOrDefault(id,"Former doctor"): "Dr. "+u.name();}
 public User patient(String id){return user(id);}
 public String patientName(String id){User u=user(id);return u==null?"Unknown patient":u.name();}
 public boolean clinicOpen(LocalDate day){return day.getDayOfWeek()!=DayOfWeek.SATURDAY&&day.getDayOfWeek()!=DayOfWeek.SUNDAY;}
 public boolean rightType(Type type,int slot){return (slot%2==0)==(type!=Type.PROCEDURE);}
 public boolean occupied(String doctorId,LocalDate day,int slot){expire();return appointments.stream().anyMatch(a->a.doctorId.equals(doctorId)&&a.day.equals(day)&&a.slot==slot&&a.occupies())||timeOffs.stream().anyMatch(t->t.doctorId.equals(doctorId)&&t.day.equals(day)&&t.slot==slot&&t.status==TimeOffStatus.APPROVED)||changeRequests.stream().anyMatch(r->r.pending&&"RESCHEDULE".equals(r.kind)&&day.equals(r.proposedDay)&&slot==r.proposedSlot&&appointments.stream().anyMatch(a->a.id.equals(r.appointmentId)&&a.doctorId.equals(doctorId)));}
 public Appointment at(String doctorId,LocalDate day,int slot){expire();return appointments.stream().filter(a->a.doctorId.equals(doctorId)&&a.day.equals(day)&&a.slot==slot&&a.occupies()).findFirst().orElse(null);}
 public boolean patientOverlap(String pid, LocalDate day,int slot){return appointments.stream().anyMatch(a->a.patientId.equals(pid)&&a.day.equals(day)&&a.slot==slot&&a.occupies())||changeRequests.stream().anyMatch(r->r.pending&&"RESCHEDULE".equals(r.kind)&&day.equals(r.proposedDay)&&slot==r.proposedSlot&&appointments.stream().anyMatch(a->a.id.equals(r.appointmentId)&&a.patientId.equals(pid)));}
 public boolean eligible(String doctorId,String patientId){return appointments.stream().anyMatch(a->a.doctorId.equals(doctorId)&&a.patientId.equals(patientId)&&a.type==Type.CONSULTATION&&a.status==Status.COMPLETED);}
 public void expire(){LocalDateTime now=LocalDateTime.now();for(Appointment a:appointments){if(a.status==Status.PENDING_PATIENT||a.status==Status.PENDING_RECEPTIONIST){LocalDateTime deadline=a.createdAt.plusDays(7);LocalDateTime endOfDate=a.day.plusDays(1).atStartOfDay();if(!now.isBefore(deadline.isBefore(endOfDate)?deadline:endOfDate)){a.status=Status.EXPIRED;notify(a.patientId,"Request "+a.id+" expired; its slot was released.");}}}}
 public void notify(String userId,String content){notices.add(0,new Notice(userId,content));}
 public void notifyRole(Role role,String content){for(User u:users)if(u.role==role)notify(u.id,content);}
 public Appointment book(String pid,String did,Type type,LocalDate day,int slot, boolean byDoctor){
  expire(); if(!clinicOpen(day)||day.isBefore(LocalDate.now())||day.isAfter(LocalDate.now().plusDays(30)))throw new IllegalArgumentException("Choose a clinic weekday within the next 30 days.");
  if(!rightType(type,slot))throw new IllegalArgumentException("That timeslot is for a different appointment type.");
  if(!LocalDateTime.now().isBefore(slotStart(day,slot)))throw new IllegalArgumentException("The selected time has already started.");
  if(doctor(did)==null||patient(pid)==null)throw new IllegalArgumentException("Select a valid doctor and patient.");
  if(occupied(did,day,slot)||patientOverlap(pid,day,slot))throw new IllegalArgumentException("That appointment time is no longer available.");
  if(byDoctor&&type==Type.CONSULTATION)throw new IllegalArgumentException("Doctors may only propose procedures or follow-ups.");
  if(byDoctor&&!eligible(did,pid))throw new IllegalArgumentException("This patient has no completed consultation with you.");
  if(!byDoctor&&type!=Type.CONSULTATION)throw new IllegalArgumentException("Patients may request consultations only.");
  Appointment a=new Appointment(next("AP"),pid,did,type,day,slot,byDoctor?Status.PENDING_PATIENT:Status.PENDING_RECEPTIONIST,LocalDateTime.now());appointments.add(a);
  notify(pid,byDoctor?doctorName(did)+" proposed a "+type+" visit. Please respond.":"Consultation requested. Await receptionist approval.");
  if(!byDoctor)notifyRole(Role.RECEPTIONIST,"New consultation request "+a.id+" needs review.");
  return a;
 }
 public void decide(Appointment a, boolean approve, String reason){
  if(!(a.status==Status.PENDING_PATIENT||a.status==Status.PENDING_RECEPTIONIST))throw new IllegalArgumentException("This request is no longer pending.");
  a.status=approve?Status.CONFIRMED:(a.status==Status.PENDING_PATIENT?Status.DECLINED:Status.REJECTED);a.reason=reason;
  notify(a.patientId,"Appointment "+a.id+(approve?" approved.":" not approved. "+reason));
  Doctor d=doctor(a.doctorId); if(d!=null)notify(d.userId,"Appointment "+a.id+" was "+(approve?"approved":"declined/rejected")+".");
 }
 public void cancel(Appointment a,String reason){if(!a.occupies())throw new IllegalArgumentException("Only active appointments can be cancelled.");a.status=Status.CANCELLED;a.reason=reason;notify(a.patientId,"Appointment "+a.id+" cancelled. "+reason);Doctor d=doctor(a.doctorId);if(d!=null)notify(d.userId,"Appointment "+a.id+" cancelled.");notifyRole(Role.RECEPTIONIST,"Appointment "+a.id+" was cancelled.");}
 public void finish(Appointment a,boolean attended){if(a.status!=Status.CONFIRMED)throw new IllegalArgumentException("Only confirmed appointments can be finished.");if(LocalDateTime.now().isBefore(slotEnd(a.day,a.slot)))throw new IllegalArgumentException("The timeslot has not yet finished.");a.status=attended?Status.COMPLETED:Status.NO_SHOW;notify(a.patientId,"Your visit "+a.id+" was marked "+a.status+".");}
 public TimeOff requestTimeOff(String did,LocalDate day,int slot){if(!clinicOpen(day)||day.isBefore(LocalDate.now())||day.isAfter(LocalDate.now().plusDays(30))||!LocalDateTime.now().isBefore(slotStart(day,slot)))throw new IllegalArgumentException("Choose a future clinic timeslot within 30 days.");if(occupied(did,day,slot)||timeOffs.stream().anyMatch(t->t.doctorId.equals(did)&&t.day.equals(day)&&t.slot==slot&&t.status==TimeOffStatus.PENDING))throw new IllegalArgumentException("This slot already has an appointment or a request.");TimeOff t=new TimeOff(next("TO"),did,day,slot,TimeOffStatus.PENDING);timeOffs.add(t);notifyRole(Role.RECEPTIONIST,"New time-off request "+t.id+".");return t;}
 public void decideTimeOff(TimeOff t,boolean approve,String reason){if(t.status!=TimeOffStatus.PENDING)throw new IllegalArgumentException("Request was already reviewed.");if(approve&&occupied(t.doctorId,t.day,t.slot))throw new IllegalArgumentException("Cannot approve: an appointment now occupies the slot.");t.status=approve?TimeOffStatus.APPROVED:TimeOffStatus.REJECTED;t.reason=reason;Doctor d=doctor(t.doctorId);if(d!=null)notify(d.userId,"Time-off request "+t.id+" "+(approve?"approved":"rejected")+". "+reason);}
 public void requestChange(Appointment a,String requester,String kind,String detail){
  if(!a.occupies()||detail==null||detail.isBlank())throw new IllegalArgumentException("An active appointment and explanation are required.");
  if(changeRequests.stream().anyMatch(r->r.pending&&r.appointmentId.equals(a.id)&&r.kind.equals(kind)))throw new IllegalArgumentException("A matching request is already pending.");
  ChangeRequest r=new ChangeRequest(next("CR"),a.id,requester,kind,detail);changeRequests.add(r);notifyRole(Role.RECEPTIONIST,"New "+kind+" request "+r.id+" for "+a.id+".");
 }
 /** A reschedule request reserves its proposed replacement while the original stays active. */
 public void requestReschedule(Appointment a,String requester,LocalDate newDay,int newSlot,String detail){
  expire();if(a.status!=Status.CONFIRMED)throw new IllegalArgumentException("Only confirmed appointments can be rescheduled.");
  if(!a.patientId.equals(requester))throw new IllegalArgumentException("Only the patient can request a new timeslot.");
  if(!clinicOpen(newDay)||newDay.isBefore(LocalDate.now())||newDay.isAfter(LocalDate.now().plusDays(30))||!LocalDateTime.now().isBefore(slotStart(newDay,newSlot)))throw new IllegalArgumentException("Select a future clinic timeslot within 30 days.");
  if(!rightType(a.type,newSlot))throw new IllegalArgumentException("Choose a timeslot matching the appointment category.");
  if(newDay.equals(a.day)&&newSlot==a.slot)throw new IllegalArgumentException("Select a different timeslot.");
  if(occupied(a.doctorId,newDay,newSlot)||patientOverlap(requester,newDay,newSlot))throw new IllegalArgumentException("The replacement slot is already reserved.");
  if(changeRequests.stream().anyMatch(r->r.pending&&r.appointmentId.equals(a.id)&&r.kind.equals("RESCHEDULE")))throw new IllegalArgumentException("There is already a pending reschedule request.");
  ChangeRequest r=new ChangeRequest(next("CR"),a.id,requester,"RESCHEDULE",detail);r.proposedDay=newDay;r.proposedSlot=newSlot;changeRequests.add(r);
  notifyRole(Role.RECEPTIONIST,"New reschedule request "+r.id+" for "+a.id+" needs review.");
  notify(requester,"Replacement requested: "+newDay+" · "+TIMES[newSlot]+". Await receptionist approval.");
 }
 /** Patient approves a doctor proposal at an alternate available slot. */
 public void approveProposalAt(Appointment a,LocalDate newDay,int newSlot){
  if(a.status!=Status.PENDING_PATIENT)throw new IllegalArgumentException("The proposal is no longer pending.");
  if(!rightType(a.type,newSlot)||!clinicOpen(newDay)||newDay.isBefore(LocalDate.now())||newDay.isAfter(LocalDate.now().plusDays(30))||!LocalDateTime.now().isBefore(slotStart(newDay,newSlot)))throw new IllegalArgumentException("Choose a valid future slot for this type.");
  if((!newDay.equals(a.day)||newSlot!=a.slot)&&(occupied(a.doctorId,newDay,newSlot)||patientOverlap(a.patientId,newDay,newSlot)))throw new IllegalArgumentException("That replacement time is already reserved.");
  a.day=newDay;a.slot=newSlot;decide(a,true,"Patient selected an alternate time");
 }
 public void resolveChange(ChangeRequest r,boolean approve,String reason){
  if(!r.pending)throw new IllegalArgumentException("Request already reviewed.");
  Appointment a=appointments.stream().filter(v->v.id.equals(r.appointmentId)).findFirst().orElseThrow();
  if(approve&&r.kind.equals("CANCELLATION")&&!a.occupies())throw new IllegalArgumentException("Appointment is already inactive.");
  if(approve&&r.kind.equals("RESCHEDULE")){
   if(!a.occupies()||r.proposedDay==null)throw new IllegalArgumentException("Original appointment no longer active.");
   if(appointments.stream().anyMatch(v->v!=a&&v.occupies()&&v.doctorId.equals(a.doctorId)&&v.day.equals(r.proposedDay)&&v.slot==r.proposedSlot))throw new IllegalArgumentException("The replacement time is now occupied.");
   if(timeOffs.stream().anyMatch(t->t.status==TimeOffStatus.APPROVED&&t.doctorId.equals(a.doctorId)&&t.day.equals(r.proposedDay)&&t.slot==r.proposedSlot))throw new IllegalArgumentException("The doctor is unavailable at that time.");
   a.day=r.proposedDay;a.slot=r.proposedSlot;
  }
  r.pending=false;r.resolution=reason;
  if(approve&&r.kind.equals("CANCELLATION"))cancel(a,"Approved by receptionist");
  notify(r.requesterId,"Your "+r.kind.toLowerCase()+" request for "+a.id+" was "+(approve?"approved":"rejected")+". "+reason);
  if(approve&&r.kind.equals("RESCHEDULE")){notify(r.requesterId,"Appointment "+a.id+" moved to "+a.day+" · "+TIMES[a.slot]+".");Doctor d=doctor(a.doctorId);if(d!=null)notify(d.userId,"Appointment "+a.id+" rescheduled to "+a.day+" · "+TIMES[a.slot]+".");}
 }
 public void changePassword(User u,String newPassword){u.password=clyvr.security.FirebaseScrypt.hash(newPassword);}
 public Doctor addDoctor(String first,String last,String specialty){String uid=next("U"),did=next("D");User u=new User(uid,"dr"+uid.toLowerCase(),clyvr.security.FirebaseScrypt.hash("doctor123"),Role.DOCTOR,first,last,first.toLowerCase()+"@clyvr.local","");users.add(u);Doctor d=new Doctor(did,uid,specialty);doctors.add(d);return d;}
 public void removeDoctor(String did){boolean active=appointments.stream().anyMatch(a->a.doctorId.equals(did)&&a.occupies())||timeOffs.stream().anyMatch(t->t.doctorId.equals(did)&&t.status==TimeOffStatus.PENDING);if(active)throw new IllegalArgumentException("Resolve active appointments and pending time-off requests before removal.");Doctor d=doctor(did);if(d==null)return;formerDoctors.put(did,doctorName(did));doctors.remove(d);users.removeIf(u->u.id.equals(d.userId));}
}
