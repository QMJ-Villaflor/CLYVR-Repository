package clyvr.model;

import java.time.*;
import java.util.*;

public final class Entities {
 private Entities() {}
 public enum Role {PATIENT, DOCTOR, RECEPTIONIST, ADMIN}
 public enum Type {CONSULTATION, FOLLOW_UP, PROCEDURE}
 public enum Status {PENDING_RECEPTIONIST, PENDING_PATIENT, CONFIRMED, REJECTED, DECLINED, EXPIRED, CANCELLED, COMPLETED, NO_SHOW}
 public enum TimeOffStatus {PENDING, APPROVED, REJECTED}
 public static class User {
  public String password;
  public final String id, username, firstName, lastName, email, phone;
  public final Role role;
  public String birthDate="",sex="",address="",bloodType="Unknown",allergies="None reported",conditions="None reported",medications="None reported",medicalHistory="None reported";
  public User(String id,String username,String password,Role role,String firstName,String lastName,String email,String phone){this.id=id;this.username=username;this.password=password;this.role=role;this.firstName=firstName;this.lastName=lastName;this.email=email;this.phone=phone;}
  public String name(){return firstName+" "+lastName;}
 }
 public static class Doctor {
  public final String id, userId, specialty;
  public Doctor(String id,String userId,String specialty){this.id=id;this.userId=userId;this.specialty=specialty;}
 }
 public static class Appointment {
  public final String id,patientId,doctorId;
  public final Type type;
  public LocalDate day;
  public int slot;
  public Status status;
  public final LocalDateTime createdAt;
  public String reason="";
  public Appointment(String id,String patientId,String doctorId,Type type,LocalDate day,int slot,Status status,LocalDateTime createdAt){this.id=id;this.patientId=patientId;this.doctorId=doctorId;this.type=type;this.day=day;this.slot=slot;this.status=status;this.createdAt=createdAt;}
  public boolean occupies(){return status==Status.PENDING_RECEPTIONIST||status==Status.PENDING_PATIENT||status==Status.CONFIRMED;}
 }
 public static class TimeOff {
  public final String id,doctorId; public final LocalDate day;public final int slot;public TimeOffStatus status;
  public String reason="";
  public TimeOff(String id,String doctorId,LocalDate day,int slot,TimeOffStatus status){this.id=id;this.doctorId=doctorId;this.day=day;this.slot=slot;this.status=status;}
 }
 public static class ChangeRequest {
  public final String id,appointmentId,requesterId,kind,details;
  public boolean pending=true; public String resolution=""; public LocalDate proposedDay=null; public int proposedSlot=-1;
  public ChangeRequest(String id,String appointmentId,String requesterId,String kind,String details){this.id=id;this.appointmentId=appointmentId;this.requesterId=requesterId;this.kind=kind;this.details=details;}
 }
 public static class Notice {
  public final String userId,text;public final LocalDateTime time; public boolean read=false;
  public Notice(String userId,String text){this.userId=userId;this.text=text;this.time=LocalDateTime.now();}
 }
}
