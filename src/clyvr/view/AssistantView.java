package clyvr.view;
import clyvr.controller.*;import clyvr.ui.*;import javax.swing.*;import java.awt.*;
public class AssistantView extends JPanel {
 public AssistantView(AppController app){super(new BorderLayout());JPanel p=ViewSupport.page("CLYVR Assistant","Quick guidance for navigating the clinic appointment system");ViewSupport.line(p,"How do I request a consultation?","Open Schedule, select a service or doctor, choose a weekday and an open consultation timeslot.");ViewSupport.line(p,"How do doctor proposals work?","A doctor may propose a follow-up or procedure after a completed consultation. Patients respond under Patient Approvals.");ViewSupport.line(p,"How do I receive updates?","Open Notifications using the bell icon in the top bar.");ViewSupport.line(p,"Important","This application is for clinic appointments, not emergency medical care.");add(ViewSupport.wrap(p));}
}
