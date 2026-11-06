package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.ClinicStore;
import clyvr.ui.Theme;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Dedicated form screen, reached only after an available consultation slot is chosen. */
public class BookingView extends JPanel {
    public BookingView(AppController app){
        super(new BorderLayout());JPanel p=ViewSupport.page("Book an appointment","Schedule a consultation with one of our doctors.");p.setBorder(new EmptyBorder(14,28,12,28));
        JPanel form=Theme.card(new BorderLayout(0,18));form.setAlignmentX(Component.LEFT_ALIGNMENT);form.setBorder(new EmptyBorder(12,18,12,18));
        JLabel header=Theme.label("Appointment Information",24,true);form.add(header,BorderLayout.NORTH);
        JPanel columns=new JPanel(new GridLayout(1,2,30,10));columns.setOpaque(false);
        JPanel details=ViewSupport.stackForm(),notes=ViewSupport.stackForm();
        ViewSupport.formValue(details,"Service",app.store.doctor(app.bookingDoctor()).specialty);
        ViewSupport.formValue(details,"Doctor",app.store.doctorName(app.bookingDoctor()));
        ViewSupport.formValue(details,"Date",app.chosenDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        ViewSupport.formValue(details,"Timeslot",ClinicStore.TIMES[app.bookingSlot()]);
        JLabel note=Theme.label("Reason for visit *",16,true);notes.add(note);notes.add(Box.createVerticalStrut(8));JTextArea reason=Theme.area(7);notes.add(new JScrollPane(reason));
        columns.add(details);columns.add(notes);form.add(columns,BorderLayout.CENTER);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,0));controls.setOpaque(false);
        JButton cancel=Theme.secondary("Cancel");cancel.addActionListener(e->app.navigate("Timeslots"));controls.add(cancel);
        JButton continueButton=Theme.button("Send appointment request");continueButton.addActionListener(e->{String reasonError=clyvr.security.Validator.longText("Reason for visit",reason.getText());if(reasonError!=null){app.error(reasonError);return;}app.requestConsultation(app.bookingDoctor(),app.chosenDate(),app.bookingSlot(),reason.getText().trim());});controls.add(continueButton);
        form.add(controls,BorderLayout.SOUTH);p.add(form);add(ViewSupport.wrap(p));
    }
}
