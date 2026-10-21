package clyvr.view;

import clyvr.controller.AppController;import clyvr.model.Entities.*;import clyvr.ui.Theme;
import javax.swing.*;import java.awt.*;

/** Personal data view; receptionists never see patient medical fields. */
public class ProfileView extends JPanel {
 public ProfileView(AppController app){super(new BorderLayout());User u=app.current;
  JPanel p=ViewSupport.page("My Information","Legal, contact, and personal medical information");
  JPanel legal=ViewSupport.stackForm();ViewSupport.formValue(legal,"Last name",u.lastName);ViewSupport.formValue(legal,"First name",u.firstName);
  ViewSupport.formValue(legal,"Birth date",u.birthDate.isBlank()?"Not provided":u.birthDate);
  ViewSupport.formValue(legal,"Sex",u.sex.isBlank()?"Not provided":u.sex);ViewSupport.formValue(legal,"Address",u.address.isBlank()?"Not provided":u.address);
  JPanel contact=ViewSupport.stackForm();ViewSupport.formValue(contact,"Email address",u.email);ViewSupport.formValue(contact,"Phone number",u.phone);
  JPanel left=Theme.card(new BorderLayout(0,14)),right=Theme.card(new BorderLayout(0,14));left.add(Theme.label("Legal Information",21,true),BorderLayout.NORTH);left.add(legal,BorderLayout.CENTER);
  right.add(Theme.label("Contact Information",21,true),BorderLayout.NORTH);right.add(contact,BorderLayout.CENTER);
  JPanel grid=new JPanel(new GridLayout(1,2,20,0));grid.setOpaque(false);grid.setAlignmentX(Component.LEFT_ALIGNMENT);grid.add(left);grid.add(right);
  p.add(grid);Theme.space(p,16);
  if(u.role==Role.PATIENT){
   JPanel medical=Theme.card(new BorderLayout(0,12));medical.add(Theme.label("Medical Information",21,true),BorderLayout.NORTH);
   JPanel rows=new JPanel(new GridLayout(0,2,18,12));rows.setOpaque(false);
   JPanel med1=ViewSupport.stackForm(),med2=ViewSupport.stackForm();ViewSupport.formValue(med1,"Blood type",u.bloodType);ViewSupport.formValue(med1,"Allergies",u.allergies);ViewSupport.formValue(med1,"Medical conditions",u.conditions);
   ViewSupport.formValue(med2,"Current medications",u.medications);ViewSupport.formValue(med2,"Medical history",u.medicalHistory);
   rows.add(med1);rows.add(med2);medical.add(rows,BorderLayout.CENTER);medical.setAlignmentX(Component.LEFT_ALIGNMENT);p.add(medical);
  }
  add(ViewSupport.wrap(p));
 }
}
