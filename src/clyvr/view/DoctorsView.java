package clyvr.view;
import clyvr.controller.*;import clyvr.model.Entities.*;import clyvr.ui.*;
import javax.swing.*;import java.awt.*;

/** Public doctors directory; administrator controls remain inside the Doctors section. */
public class DoctorsView extends JPanel {
 public DoctorsView(AppController app){
  super(new BorderLayout());
  JPanel p=ViewSupport.page("Our Doctors","Meet the clinic's healthcare professionals");
  if(app.current.role==Role.ADMIN){
   JPanel controls=ViewSupport.row();
   JButton manage=Theme.button("+  Add / remove doctors");
   manage.addActionListener(e->app.navigate("Doctor Management"));
   controls.add(manage);p.add(controls);Theme.space(p,15);
  }
  for(Doctor d:app.store.doctors){
   JButton detail=Theme.secondary("View profile");
   detail.addActionListener(e->app.info(app.store.doctorName(d.id)+"\nSpecialty: "+d.specialty));
   ViewSupport.line(p,app.store.doctorName(d.id),d.specialty,detail);
  }
  add(ViewSupport.wrap(p));
 }
}
