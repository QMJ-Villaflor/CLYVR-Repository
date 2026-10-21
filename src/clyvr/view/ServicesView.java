package clyvr.view;
import clyvr.controller.*;import clyvr.ui.*;import javax.swing.*;import java.awt.*;
public class ServicesView extends JPanel {
 public ServicesView(AppController app){super(new BorderLayout());JPanel p=ViewSupport.page("Services","Consultation and planned care at CLYVR");String[] d={"General medical assessment and referral","Women's health consultation and planned care","Child health consultation and planned care","Dental consultation and planned care"};for(int i=0;i<4;i++)ViewSupport.line(p,clyvr.model.ClinicStore.SERVICES[i],d[i]+" · Consultation 30 min · Procedure 60 min");add(ViewSupport.wrap(p));}
}
