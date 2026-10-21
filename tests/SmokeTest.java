import clyvr.model.*;import clyvr.model.Entities.*;import java.time.*;
public class SmokeTest {
 static void yes(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] ignored){ClinicStore db=new ClinicStore();
  yes(db.users.stream().anyMatch(u->u.role==Role.RECEPTIONIST),"receptionist exists");
  yes(db.eligible("D1","U1"),"seed consultation history");
  LocalDate day=LocalDate.now().plusDays(1);while(!db.clinicOpen(day))day=day.plusDays(1);
  Appointment a=db.book("U2","D3",Type.CONSULTATION,day,0,false);
  yes(a.status==Status.PENDING_RECEPTIONIST,"patient request pending");
  yes(db.occupied("D3",day,0),"pending slot reserved");
  try{db.book("U1","D3",Type.CONSULTATION,day,0,false);throw new AssertionError("double booking allowed");}catch(IllegalArgumentException expected){}
  db.decide(a,false,"Invalid appointment details: test");yes(!db.occupied("D3",day,0),"rejection releases slot");
  Appointment proposal=db.book("U1","D1",Type.FOLLOW_UP,day,2,true);yes(proposal.status==Status.PENDING_PATIENT,"proposal pending patient");db.decide(proposal,true,"");yes(proposal.status==Status.CONFIRMED,"proposal confirmed");
  TimeOff time=db.requestTimeOff("D3",day,1);Appointment competing=db.book("U1","D3",Type.CONSULTATION,day,4,false);
  db.decideTimeOff(time,true,"");yes(time.status==TimeOffStatus.APPROVED,"timeoff approval");
  try{db.removeDoctor("D1");throw new AssertionError("removed doctor with active appointment");}catch(IllegalArgumentException expected){}
  db.requestChange(competing,"U1","RESCHEDULE","prefer another weekday");yes(db.changeRequests.size()==1,"change request created");
  db.resolveChange(db.changeRequests.get(0),false,"Other: test reason");yes(!db.changeRequests.get(0).pending,"change request reviewed");
  System.out.println("PASS: model smoke tests");
 }
}
