package clyvr.view;
import clyvr.controller.*;import clyvr.ui.*;import javax.swing.*;import java.awt.*;
public class AboutView extends JPanel {
 public AboutView(AppController app){super(new BorderLayout());JPanel p=ViewSupport.page("About CLYVR","Clinic Appointment Management System");ViewSupport.line(p,"The system","A Java Swing desktop application for consultations, doctor-proposed visits, and clinic scheduling.");ViewSupport.line(p,"Our clinic","An appointment-focused community clinic. Not intended for emergency care.");ViewSupport.line(p,"Developers","CLYVR academic project · Development team");add(ViewSupport.wrap(p));}
}
