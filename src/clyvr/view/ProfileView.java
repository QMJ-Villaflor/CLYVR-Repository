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
  Theme.space(p,16);
  JPanel security=Theme.card(new BorderLayout(0,12));security.setAlignmentX(Component.LEFT_ALIGNMENT);
  security.add(Theme.label("Account Security",21,true),BorderLayout.NORTH);
  JPanel securityRow=new JPanel(new BorderLayout(16,0));securityRow.setOpaque(false);
  securityRow.add(Theme.muted("Use at least "+clyvr.security.Validator.MIN_PASSWORD+" characters with a letter and a number."),BorderLayout.CENTER);
  JButton change=Theme.button("Change password");change.addActionListener(e->changePassword(app));securityRow.add(change,BorderLayout.EAST);
  security.add(securityRow,BorderLayout.CENTER);p.add(security);
  add(ViewSupport.wrap(p));
 }
 private void changePassword(AppController app){
  User u=app.current;JPasswordField current=new JPasswordField(20),next=new JPasswordField(20),confirm=new JPasswordField(20);
  JPanel form=new JPanel(new GridLayout(0,1,3,6));form.add(new JLabel("Current password"));form.add(current);form.add(new JLabel("New password"));form.add(next);form.add(new JLabel("Confirm new password"));form.add(confirm);
  while(true){
   if(JOptionPane.showConfirmDialog(this,form,"Change password",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
   String now=new String(current.getPassword()),pw=new String(next.getPassword()),again=new String(confirm.getPassword());
   String problem;
   if(!clyvr.security.FirebaseScrypt.verify(now,u.password))problem="Your current password is incorrect.";
   else if((problem=clyvr.security.Validator.password(pw,u.email))!=null){}
   else if(!pw.equals(again))problem="The new passwords do not match.";
   else if(pw.equals(now))problem="The new password must be different from your current one.";
   if(problem!=null){app.error(problem);continue;}
   app.store.changePassword(u,pw);app.info("Your password has been changed.");return;
  }
 }
}
