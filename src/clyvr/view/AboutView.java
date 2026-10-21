package clyvr.view;
import clyvr.controller.*;import clyvr.ui.*;import javax.swing.*;import java.awt.*;
public class AboutView extends JPanel {
    public AboutView(AppController app){super(new BorderLayout());
        JPanel p=ViewSupport.page("About CLYVR","Clinic Appointment Management System");
        ViewSupport.line(p,"The CLYVR Clinic","CLYVR Clinic makes clinic appointments easier to manage. Patients can book, reschedule, or cancel visits online and receive reminders. It supports General Medicine, Pediatrics, OB-GYN, Dental, and Veterinary services. This system is for routine appointments only, not emergencies.");
        ViewSupport.line(p,"The Vision","To make clinic appointments more convenient, accessible, and efficient by providing a reliable and organized appointment management system that simplifies the scheduling process for everyone.");
        ViewSupport.line(p,"The Mission","To simplify clinic scheduling with easy online booking, quick schedule changes, timely reminders, and organized records—helping reduce waiting times and improve coordination.");
        ViewSupport.line(p,"The System and the Developers","A Java Swing desktop application for consultations, doctor-proposed visits, and clinic scheduling; developed for an academic project by the CLYVR development team.");
        ViewSupport.line(p,"Contact Us","+63 9XX XXX XXXX\n123 Main St., Quezon City, Philippines"); 
        add(ViewSupport.wrap(p));
    }
}
