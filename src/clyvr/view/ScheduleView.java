package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.ClinicStore;
import clyvr.model.Entities.*;
import clyvr.ui.Theme;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Month calendar. Never mixes dates and slots in a single scrolling list. */
public class ScheduleView extends JPanel {
    private YearMonth month=YearMonth.from(LocalDate.now());
    private final AppController app;
    private final JComboBox<String> service=new JComboBox<>();
    private final JComboBox<String> doctor=new JComboBox<>();
    private final JPanel calendar=new JPanel(new BorderLayout(0,8));
    private final JLabel monthTitle=Theme.label("",22,true);
    public ScheduleView(AppController app){
        super(new BorderLayout());this.app=app;
        String subtitle= switch(app.current.role) {
          case PATIENT -> "Select a service OR a doctor, then a date and a timeslot to request a consultation.";
          case DOCTOR -> "Review your assigned timeslots, patient visits and schedule requests.";
          case RECEPTIONIST -> "Review booking requests and check doctor availability.";
          case ADMIN -> "View clinic availability. Doctor account management is under Doctors.";
        };
        JPanel page=ViewSupport.page("Schedule",subtitle);page.setBorder(new EmptyBorder(14,28,12,28));
        JPanel shortcuts=ViewSupport.row();shortcuts.setBorder(new EmptyBorder(0,0,2,0));
        switch(app.current.role) {
          case PATIENT -> {
            shortcut(shortcuts,"My Appointments",()->app.navigate("My Appointments"));
            shortcut(shortcuts,"Doctor Proposals",()->app.navigate("Patient Approvals"));
          }
          case DOCTOR -> shortcut(shortcuts,"My Appointments",()->app.navigate("My Appointments"));
          case RECEPTIONIST -> {
            shortcut(shortcuts,"Review Requests",()->app.navigate("Requests"));
            shortcut(shortcuts,"Appointments",()->app.navigate("My Appointments"));
          }
          case ADMIN -> { }
        }
        if(shortcuts.getComponentCount()>0){page.add(shortcuts);Theme.space(page,7);}
        JPanel filters=Theme.card(new BorderLayout(12,3));filters.setAlignmentX(Component.LEFT_ALIGNMENT);filters.setBorder(new EmptyBorder(11,18,11,18));
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,13,4));controls.setOpaque(false);
        controls.add(Theme.label("Service",14,true));service.addItem("All services");service.addItem("Unsure — see a physician");for(String s:ClinicStore.SERVICES)service.addItem(s);
        service.setPreferredSize(new Dimension(242,39));controls.add(service);
        controls.add(Theme.label("OR  Doctor",14,true));doctor.addItem("All doctors");for(Doctor d:app.store.doctors)if(app.current.role!=Role.DOCTOR||d.id.equals(app.currentDoctorId()))doctor.addItem(app.store.doctorName(d.id));
        if(app.current.role==Role.DOCTOR&&doctor.getItemCount()>1)doctor.setSelectedIndex(1);
        doctor.setPreferredSize(new Dimension(230,39));controls.add(doctor);filters.add(controls,BorderLayout.CENTER);
        JLabel help=Theme.muted("Weekdays only   ·   Booking up to 30 days ahead   ·   8 daily appointment slots");filters.add(help,BorderLayout.SOUTH);
        page.add(filters);Theme.space(page,13);
        JPanel outer=Theme.card(new BorderLayout(10,10));outer.setAlignmentX(Component.LEFT_ALIGNMENT);outer.setBorder(new EmptyBorder(14,19,14,19));outer.add(calendar);
        page.add(outer);
        service.addActionListener(e->{if(service.getSelectedIndex()>0&&doctor.getSelectedIndex()>0)doctor.setSelectedIndex(0);});
        doctor.addActionListener(e->{if(doctor.getSelectedIndex()>0&&service.getSelectedIndex()>0)service.setSelectedIndex(0);});
        rebuild();add(ViewSupport.wrap(page));
    }
    private void shortcut(JPanel row,String text,Runnable action){
        JButton button=Theme.secondary(text+"  ›");button.addActionListener(e->action.run());row.add(button);
    }
    private void rebuild(){
        calendar.removeAll();calendar.setOpaque(false);JPanel nav=new JPanel(new BorderLayout());nav.setOpaque(false);
        JPanel left=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));left.setOpaque(false);
        JButton prev=Theme.secondary("‹  Previous");prev.addActionListener(e->{month=month.minusMonths(1);rebuild();});prev.setEnabled(!month.equals(YearMonth.from(LocalDate.now())));
        JButton next=Theme.secondary("Next  ›");next.addActionListener(e->{month=month.plusMonths(1);rebuild();});next.setEnabled(month.isBefore(YearMonth.from(LocalDate.now().plusDays(30))));
        if(prev.isEnabled())left.add(prev);if(next.isEnabled())left.add(next);nav.add(left,BorderLayout.WEST);
        monthTitle.setText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")));nav.add(monthTitle,BorderLayout.EAST);calendar.add(nav,BorderLayout.NORTH);
        JPanel days=new JPanel(new GridLayout(0,7,6,6));days.setOpaque(false);
        for(String s:new String[]{"Sun","Mon","Tue","Wed","Thu","Fri","Sat"}){JLabel l=Theme.label(s,14,true);l.setHorizontalAlignment(SwingConstants.CENTER);days.add(l);}
        LocalDate first=month.atDay(1);int blanks=first.getDayOfWeek().getValue()%7;
        for(int i=0;i<blanks;i++){JPanel empty=new JPanel();empty.setOpaque(false);days.add(empty);}
        for(int d=1;d<=month.lengthOfMonth();d++){
            LocalDate selected=month.atDay(d);boolean enabled=app.store.clinicOpen(selected)&&!selected.isBefore(LocalDate.now())&&!selected.isAfter(LocalDate.now().plusDays(30));
            JButton cell=Theme.button(Integer.toString(d));cell.setFont(Theme.font(16,selected.equals(LocalDate.now())));cell.setPreferredSize(new Dimension(72,35));
            cell.setToolTipText(enabled?"View timeslots for "+selected:"Clinic unavailable on this date");
            if(enabled){cell.addActionListener(e->open(selected));days.add(cell);}
            else {JLabel closed=Theme.label(Integer.toString(d),16,false);closed.setForeground(Theme.MUTED);closed.setHorizontalAlignment(SwingConstants.CENTER);
                closed.setOpaque(true);closed.setBackground(new Color(192,221,226));closed.setBorder(BorderFactory.createLineBorder(Theme.LINE));closed.setToolTipText("Clinic unavailable");days.add(closed);}

        }
        calendar.add(days,BorderLayout.CENTER);calendar.revalidate();calendar.repaint();
    }
    private void open(LocalDate day){
        String chosen=(String)service.getSelectedItem(),chosenDoc=(String)doctor.getSelectedItem();String did="";
        if(!"All doctors".equals(chosenDoc))for(Doctor d:app.store.doctors)if(app.store.doctorName(d.id).equals(chosenDoc))did=d.id;
        if(app.current.role==Role.DOCTOR)did=app.currentDoctorId();
        app.showTimeslots(day,did,chosen);
    }
}
