import clyvr.model.*;
import clyvr.model.Entities.*;
import java.time.*;

/** End-to-end business-rule tests independent of GUI paint. */
public class WorkflowTest {
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 static LocalDate workday(int start){LocalDate d=LocalDate.now().plusDays(start);while(d.getDayOfWeek().getValue()>5)d=d.plusDays(1);return d;}
 static void fails(Runnable action,String name){try{action.run();throw new AssertionError("Expected rejection: "+name);}catch(IllegalArgumentException expected){}}
 public static void main(String[] args){
  ClinicStore store=new ClinicStore();LocalDate d1=workday(1), d2=workday(2), d3=workday(3);
  Appointment incoming=store.book("U1","D4",Type.CONSULTATION,d1,0,false);
  check(incoming.status==Status.PENDING_RECEPTIONIST,"patient request routes to reception");
  fails(()->store.book("U2","D4",Type.CONSULTATION,d1,0,false),"reserved slot protects from double booking");
  store.decide(incoming,true,"");check(incoming.status==Status.CONFIRMED,"reception approval");
  fails(()->store.book("U1","D1",Type.CONSULTATION,d1,0,false),"patient cannot book overlapping appointments");
  store.requestReschedule(incoming,"U1",d2,2,"Family appointment");
  ChangeRequest request=store.changeRequests.get(store.changeRequests.size()-1);
  check(store.occupied("D4",d1,0)&&store.occupied("D4",d2,2),"original and replacement held during review");
  fails(()->store.book("U2","D4",Type.CONSULTATION,d2,2,false),"reschedule hold blocks collision");
  store.resolveChange(request,true,"");
  check(incoming.day.equals(d2)&&incoming.slot==2&&incoming.status==Status.CONFIRMED,"reception approval really moves appointment");
  check(!store.occupied("D4",d1,0)&&store.occupied("D4",d2,2),"release original after confirmed move");
  Appointment proposal=store.book("U1","D1",Type.PROCEDURE,d3,1,true);
  check(proposal.status==Status.PENDING_PATIENT,"doctor sends approval to patient");
  fails(()->store.book("U2","D1",Type.PROCEDURE,d3,1,true),"proposal prevents another booking");
  store.approveProposalAt(proposal,d2,3);check(proposal.status==Status.CONFIRMED&&proposal.slot==3,"patient can confirm alternative procedure time");
  TimeOff leave=store.requestTimeOff("D3",d2,0);
  Appointment competing=store.book("U2","D3",Type.CONSULTATION,d2,0,false);
  fails(()->store.decideTimeOff(leave,true,""),"cannot approve time off after patient books");
  store.decideTimeOff(leave,false,"Scheduling conflict exists");
  check(leave.status==TimeOffStatus.REJECTED,"rejection recorded");
  Doctor newcomer=store.addDoctor("Alice","Sample","General Medicine");
  String originalName=store.doctorName(newcomer.id);
  store.appointments.add(new Appointment("HIST1","U1",newcomer.id,Type.CONSULTATION,LocalDate.now().minusDays(5),0,Status.COMPLETED,LocalDateTime.now().minusDays(9)));
  store.removeDoctor(newcomer.id);
  check(store.doctor(newcomer.id)==null,"permanent doctor removal");
  check(store.doctorName(newcomer.id).equals(originalName),"past visit retains doctor name");
  check(store.appointments.stream().anyMatch(a->a.id.equals("HIST1")),"history remains");
  fails(()->store.removeDoctor("D1"),"active proposal blocks doctor removal");
  check(store.notices.size()>2,"notifications propagate");
  System.out.println("PASS: workflows: booking, overlap, approval, reschedule, procedure, time-off, removal, notifications");
 }
}
