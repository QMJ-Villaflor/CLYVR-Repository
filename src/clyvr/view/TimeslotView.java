package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.DoctorDirectoryEntry;
import clyvr.database.AvailabilitySlot;
import clyvr.database.DoctorAvailability;
import clyvr.database.DoctorScheduleSelection;
import clyvr.model.ClinicStore;
import clyvr.model.Entities.*;
import clyvr.ui.Theme;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Separate day/timeslot view, laid out as a 4 × 2 grid (no slot list scrolling). */
public class TimeslotView extends JPanel {
    private final AppController app;
    private final JPanel slots=new JPanel(new GridLayout(2,4,12,12));
    private final JComboBox<DoctorChoice> selector=new JComboBox<>();
    private final LocalDate day;

    public TimeslotView(AppController app){
        this(app,(DoctorScheduleSelection)null);
    }

    public TimeslotView(AppController app,LocalDate doctorScheduleDate){
        super(new BorderLayout());
        this.app=app;
        this.day=Objects.requireNonNull(doctorScheduleDate);
        buildDoctorDatabaseSchedule();
    }

    public TimeslotView(AppController app,DoctorScheduleSelection databaseSelection){
        super(new BorderLayout());
        this.app=app;
        this.day=app.chosenDate();
        if(databaseSelection!=null){
            buildDatabaseSelection(databaseSelection);
            return;
        }
        if(app.current.role==Role.DOCTOR){
            buildDoctorDatabaseSchedule();
            return;
        }
        JPanel p=ViewSupport.page("Schedule  >  "+day.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))+" Timeslots","Legacy session fixture schedule for demonstration only; use the database-backed role screens for persisted actions.");p.setBorder(new EmptyBorder(14,28,12,28));
        JPanel header=ViewSupport.row();JButton back=Theme.secondary("← Back to calendar");back.addActionListener(e->app.navigate("Schedule"));header.add(back);
        header.add(Theme.label("Doctor",14,true));for(Doctor d:new ArrayList<>(app.store.doctors)){
            if(app.current.role==Role.DOCTOR&&!d.id.equals(app.currentDoctorId()))continue;
            if(!app.chosenDoctor().isBlank()&&!d.id.equals(app.chosenDoctor()))continue;
            String selected=app.chosenService();if("Unsure — see a physician".equals(selected)&&!d.specialty.equals("General Medicine"))continue;
            if(!"All services".equals(selected)&&!selected.startsWith("Unsure")&&!d.specialty.equals(selected))continue;
            selector.addItem(new DoctorChoice(d.id,app.store.doctorName(d.id),d.specialty));
        }
        selector.setPreferredSize(new Dimension(310,39));header.add(selector);p.add(header);Theme.space(p,10);
        JPanel card=Theme.card(new BorderLayout(0,14));card.setAlignmentX(Component.LEFT_ALIGNMENT);card.setBorder(new EmptyBorder(14,20,14,20));
        JLabel doctorName=Theme.label("",21,true);card.add(doctorName,BorderLayout.NORTH);
        slots.setOpaque(false);card.add(slots,BorderLayout.CENTER);p.add(card);
        if(selector.getItemCount()==0){doctorName.setText("No doctors match this filter");slots.add(Theme.muted("Go back and choose a different filter."));}
        else {selector.addActionListener(e->{DoctorChoice d=(DoctorChoice)selector.getSelectedItem();if(d!=null){doctorName.setText(d.name+"  ·  "+d.specialty);drawSlots(d.id);}});DoctorChoice first=(DoctorChoice)selector.getSelectedItem();doctorName.setText(first.name+"  ·  "+first.specialty);drawSlots(first.id);}
        Theme.space(p,12);p.add(Theme.muted("White: available   ·   Cyan: reserved / booked   ·   Closed: approved time off"));
        add(ViewSupport.wrap(p));
    }

    private void buildDatabaseSelection(DoctorScheduleSelection selection){
        JPanel p=ViewSupport.page(
            "Schedule  >  "+selection.day().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))+" Timeslots",
            "Read-only backend availability for mapped synthetic identities. Database slot selection remains informational."
        );
        p.setBorder(new EmptyBorder(14,28,12,28));
        JPanel header=ViewSupport.row();
        JButton back=Theme.secondary("← Back to calendar");
        back.addActionListener(e->app.navigate("Schedule"));
        header.add(back);
        p.add(header);
        Theme.space(p,12);

        java.util.List<DoctorDirectoryEntry> choices=selection.doctorsForService();
        if(selection.selectedDoctor()!=null){
            choices=java.util.List.of(selection.selectedDoctor());
        }
        java.util.List<DoctorDirectoryEntry> selectedDoctors=java.util.List.copyOf(choices);
        JLabel status=Theme.muted(choices.isEmpty()
            ?"No mapped database doctors match this service."
            :"Loading appointment, time-off, and pending reschedule availability…");
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(status);
        Theme.space(p,10);
        JPanel results=Theme.vertical();
        results.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(results);
        add(ViewSupport.wrap(p));
        if(selectedDoctors.isEmpty()){
            results.add(ViewSupport.empty("Return to the calendar and choose another service or refresh the doctor directory."));
            return;
        }
        app.loadDatabaseAvailability(app.current.id,selection.day(),selectedDoctors,availability->{
            renderDatabaseAvailability(results,selectedDoctors,availability);
            status.setText("Availability loaded through the backend. Slots are informational only on this screen.");
        },failure->{
            results.removeAll();
            results.add(ViewSupport.empty("Could not load appointment availability. Check the development database connection and synthetic ID mappings, then return to the schedule to retry."));
            results.revalidate();
            results.repaint();
            status.setText("Database availability could not be loaded.");
        });
    }

    private void buildDoctorDatabaseSchedule(){
        JPanel page=ViewSupport.page(
            "My schedule  >  "+day.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
            "Appointments and availability are database-backed. Request time off or propose an eligible follow-up/procedure."
        );
        page.setBorder(new EmptyBorder(14,28,12,28));
        JButton back=Theme.secondary("← Back to calendar");
        back.addActionListener(event->app.navigate("Schedule"));
        page.add(back);
        Theme.space(page,14);

        JLabel availabilityStatus=Theme.muted("Loading doctor availability…");
        availabilityStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(availabilityStatus);
        JPanel availabilityRows=Theme.vertical();
        availabilityRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(availabilityRows);
        Theme.space(page,18);

        JLabel appointmentsStatus=Theme.muted("Loading appointments…");
        appointmentsStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(appointmentsStatus);
        JPanel appointmentsRows=Theme.vertical();
        appointmentsRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(appointmentsRows);
        Theme.space(page,16);
        JLabel requestsStatus=Theme.muted("Loading your time-off requests…");
        requestsStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(requestsStatus);
        JPanel requestsRows=Theme.vertical();
        requestsRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(requestsRows);
        add(ViewSupport.wrap(page));

        app.loadDoctorAvailability(day,availability->{
            renderDoctorAvailability(availabilityRows,availability);
            availabilityStatus.setText("Availability loaded from the backend.");
        },failure->{
            availabilityRows.removeAll();
            availabilityRows.add(ViewSupport.empty("Availability is unavailable. Refresh the schedule to try again."));
            availabilityRows.revalidate();
            availabilityRows.repaint();
            availabilityStatus.setText("Could not load doctor availability.");
        });
        app.loadAppointments(appointments->{
            appointmentsRows.removeAll();
            java.util.List<clyvr.database.BackendAppointment> scheduled=appointments.stream()
                .filter(appointment->appointment.appointmentDate().equals(day))
                .toList();
            for(clyvr.database.BackendAppointment appointment:scheduled){
                appointmentsRows.add(DashboardLayout.itemCard(
                    appointment.patientName(),
                    appointment.summary()
                ));
                appointmentsRows.add(Box.createVerticalStrut(10));
            }
            if(scheduled.isEmpty()){
                appointmentsRows.add(ViewSupport.empty("No appointment records are scheduled for this date."));
            }
            appointmentsRows.revalidate();
            appointmentsRows.repaint();
            appointmentsStatus.setText("Appointment details are scoped to your doctor account.");
        },failure->{
            appointmentsRows.removeAll();
            appointmentsRows.add(ViewSupport.empty("Appointments are unavailable. Refresh the schedule to try again."));
            appointmentsRows.revalidate();
            appointmentsRows.repaint();
            appointmentsStatus.setText("Could not load appointment details.");
        });
        app.loadDoctorTimeOff(requests->{
            requestsRows.removeAll();
            for(clyvr.database.BackendTimeOffRequest request:requests){
                ViewSupport.line(requestsRows,
                    request.requestDate()+" · "+ClinicStore.TIMES[request.slotNumber()],
                    request.status().toUpperCase(java.util.Locale.ROOT)
                        +(request.reason().isBlank()?"":" · "+request.reason())
                );
            }
            if(requests.isEmpty()){
                requestsRows.add(ViewSupport.empty("You have not submitted any time-off requests."));
            }
            requestsStatus.setText("Your time-off requests are loaded from the database.");
            requestsRows.revalidate();requestsRows.repaint();
        },failure->{
            requestsRows.removeAll();
            requestsRows.add(ViewSupport.empty("Time-off request history is unavailable."));
            requestsStatus.setText("Could not load your time-off requests.");
            requestsRows.revalidate();requestsRows.repaint();
        });
    }

    private void renderDoctorAvailability(
            JPanel parent,DoctorAvailability availability){
        parent.removeAll();
        JPanel grid=new JPanel(new GridLayout(2,4,10,10));
        grid.setOpaque(false);
        for(AvailabilitySlot slot:availability.slots()){
            String state=switch(slot.state()){
                case AVAILABLE -> "AVAILABLE";
                case APPOINTMENT -> "APPOINTMENT";
                case APPROVED_TIME_OFF -> "APPROVED TIME OFF";
                case RESCHEDULE_HOLD -> "RESCHEDULE HOLD";
                case PATIENT_CONFLICT -> "UNAVAILABLE";
                case ACTIVE_HOLD -> "TEMPORARILY HELD";
            };
            if(!LocalDateTime.now().isBefore(ClinicStore.slotStart(day,slot.slotNumber()))){
                state="PAST · "+state;
            }
            JButton item=Theme.secondary("<html><center>"+ClinicStore.TIMES[slot.slotNumber()]
                +"<br>"+state+"</center></html>");
            boolean available=slot.state()==AvailabilitySlot.State.AVAILABLE
                    && LocalDateTime.now().isBefore(
                            ClinicStore.slotStart(day,slot.slotNumber()));
            item.setEnabled(available);
            if(available){
                item.setToolTipText("Submit a time-off request for this slot");
                item.addActionListener(event->{
                    String reason=app.input("Reason for requesting time off");
                    if(reason==null)return;
                    if(reason.isBlank()){
                        app.error("Please provide a reason for the time-off request.");
                        return;
                    }
                    item.setEnabled(false);
                    app.submitTimeOff(day,slot.slotNumber(),reason,()->{
                        app.info("Time-off request saved. Reception will review it.");
                        app.showDoctorDatabaseSchedule(day);
                    },failure->{
                        item.setEnabled(true);
                        app.error("The time-off request was not saved. Refresh the schedule and retry.");
                    });
                });
            }
            grid.add(item);
        }
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(grid);
        parent.revalidate();
        parent.repaint();
    }

    private void renderDatabaseAvailability(
            JPanel parent,
            java.util.List<DoctorDirectoryEntry> doctors,
            Map<Long,DoctorAvailability> availability) {
        parent.removeAll();
        for(DoctorDirectoryEntry doctor:doctors){
            DoctorAvailability doctorAvailability=availability.get(doctor.doctorId());
            if(doctorAvailability==null){
                throw new IllegalStateException("Availability was not returned for a selected doctor.");
            }
            parent.add(Theme.label(
                doctor.displayName()+" · "+doctor.specialty()+" · Doctor ID #"+doctor.doctorId(),
                17,true
            ));
            Theme.space(parent,8);
            addDatabaseSlots(parent,doctor,doctorAvailability);
            Theme.space(parent,15);
        }
        parent.revalidate();
        parent.repaint();
    }

    private void addDatabaseSlots(
            JPanel parent,DoctorDirectoryEntry doctor,DoctorAvailability availability){
        JPanel grid=new JPanel(new GridLayout(2,4,10,10));
        grid.setOpaque(false);
        for(AvailabilitySlot slot:availability.slots()){
            String state=switch(slot.state()){
                case AVAILABLE -> "AVAILABLE";
                case APPOINTMENT -> "RESERVED";
                case PATIENT_CONFLICT -> "YOUR OTHER APPOINTMENT";
                case APPROVED_TIME_OFF -> "DOCTOR TIME OFF";
                case RESCHEDULE_HOLD -> "RESCHEDULE HOLD";
                case ACTIVE_HOLD -> "TEMPORARILY HELD";
            };
            if(!LocalDateTime.now().isBefore(ClinicStore.slotStart(day,slot.slotNumber()))){
                state="PAST";
            }
            int slotNumber=slot.slotNumber();
            JButton button=Theme.secondary("<html><center>"+ClinicStore.TIMES[slotNumber]
                +"<br>"+state+"</center></html>");
            boolean available=slot.state()==AvailabilitySlot.State.AVAILABLE
                    &&LocalDateTime.now().isBefore(ClinicStore.slotStart(day,slotNumber));
            boolean patientCanBook=app.current.role==Role.PATIENT&&slotNumber%2==0;
            boolean doctorCanAct=app.current.role==Role.DOCTOR;
            button.setEnabled(available&&(patientCanBook||doctorCanAct));
            button.setToolTipText(button.isEnabled()
                    ?patientCanBook
                        ?"Request this consultation; the backend will check availability again."
                        :"Request time off or propose a visit to a previously seen patient."
                    :"This slot is not currently available for the selected action.");
            if(button.isEnabled()){
                button.addActionListener(event->{
                    if(app.current.role==Role.PATIENT){
                        app.showBooking(doctor,day,slotNumber,button);
                    }else{
                        chooseDoctorSlot(button,slotNumber);
                    }
                });
            }
            grid.add(button);
        }
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(grid);
    }

    private void chooseDoctorSlot(JButton item,int slot) {
        String proposalType=slot%2==0?"FOLLOW_UP":"PROCEDURE";
        String[] actions={"Request time off","Propose "+proposalType.replace('_',' '),"Cancel"};
        int selected=JOptionPane.showOptionDialog(this,
                "Choose an action for "+ClinicStore.TIMES[slot]+".",
                "Doctor schedule",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,
                null,actions,actions[0]);
        if(selected==0){
            String reason=app.input("Reason for requesting time off");
            if(reason==null)return;
            if(reason.isBlank()){app.error("Please provide a reason for the time-off request.");return;}
            item.setEnabled(false);
            app.submitTimeOff(day,slot,reason.trim(),()->{
                app.info("Time-off request saved. Reception will review it.");
                app.showDoctorDatabaseSchedule(day);
            },failure->{
                item.setEnabled(true);
                app.error(failure.getMessage());
            });
        }else if(selected==1){
            proposeToEligiblePatient(item,slot,proposalType);
        }
    }

    private void proposeToEligiblePatient(JButton item,int slot,String appointmentType){
        app.loadAppointments(appointments->{
            java.util.Map<Long,String> eligible=new java.util.TreeMap<>();
            for(clyvr.database.BackendAppointment appointment:appointments){
                if("COMPLETED".equals(appointment.status())
                        &&"CONSULTATION".equals(appointment.appointmentType())){
                    eligible.putIfAbsent(appointment.patientId(),appointment.patientName());
                }
            }
            if(eligible.isEmpty()){
                app.error("No patients with a completed consultation are eligible for a proposal.");
                return;
            }
            JComboBox<ProposalPatient> patients=new JComboBox<>();
            eligible.forEach((id,name)->patients.addItem(new ProposalPatient(id,name)));
            if(JOptionPane.showConfirmDialog(this,patients,"Select a patient",
                    JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)
                    !=JOptionPane.OK_OPTION)return;
            ProposalPatient patient=(ProposalPatient)patients.getSelectedItem();
            if(patient==null)return;
            String reason=app.input("Reason for proposing this visit");
            if(reason==null)return;
            if(reason.isBlank()){app.error("Please provide a reason for the proposal.");return;}
            item.setEnabled(false);
            Type type=Type.valueOf(appointmentType);
            app.submitDoctorProposal(patient.patientId(),type,day,slot,reason.trim(),()->{
                app.info("Proposal saved and sent to the patient for review.");
                app.showDoctorDatabaseSchedule(day);
            },failure->{
                item.setEnabled(true);
                app.error(failure.getMessage());
            });
        },failure->app.error("Could not load eligible patients from the backend."));
    }

    private record ProposalPatient(long patientId,String name){
        @Override public String toString(){return name;}
    }

    private void drawSlots(String doctorId){
        slots.removeAll();
        for(int index=0;index<ClinicStore.TIMES.length;index++){
            final int slot=index;Appointment appt=app.store.at(doctorId,day,slot);
            boolean timeOff=app.store.timeOffs.stream().anyMatch(t->t.doctorId.equals(doctorId)&&t.day.equals(day)&&t.slot==slot&&t.status==TimeOffStatus.APPROVED);
            boolean past=appt==null&&!LocalDateTime.now().isBefore(ClinicStore.slotStart(day,slot));
            Type type=index%2==0?Type.CONSULTATION:Type.PROCEDURE;
            String status=timeOff?"UNAVAILABLE":past?"PAST":appt!=null?(app.current.role==Role.PATIENT&&!appt.patientId.equals(app.current.id)?"RESERVED":appt.status.toString().replace('_',' ')):"AVAILABLE";
            String name=type==Type.CONSULTATION?"Consultation / Follow-up":"Procedure / Treatment";
            String html="<html><center><font size='4'><b>"+ClinicStore.TIMES[slot]+"</b></font><br><br>"+name+"<br><b>"+status+"</b></center></html>";
            JButton btn=new Theme.AquaButton(html,appt!=null?Theme.CARD_DARK:(timeOff||past)?new Color(190,217,221):Theme.WHITE,Theme.INK);
            btn.setPreferredSize(new Dimension(145,130));btn.setFont(Theme.font(14,true));btn.setEnabled(!timeOff);
            btn.setToolTipText(timeOff?"This doctor has approved time off":appt!=null?"View booking details":"Open timeslot actions");
            btn.addActionListener(e->{if(past){app.info("This timeslot has already started.");return;}click(doctorId,day,slot,type,appt);});slots.add(btn);
        }
        slots.revalidate();slots.repaint();
    }

    private void click(String did,LocalDate date,int slot,Type type,Appointment a){
        if(a!=null){if(app.current.role==Role.PATIENT&&!a.patientId.equals(app.current.id)){app.info("This timeslot is already reserved.");return;}appointmentDialog(app,a);return;}
        app.info("This legacy schedule is for fixture display only. Use database-backed actions from your role dashboard.");
    }

    public static void appointmentDialog(AppController app,Appointment a){
        String text=ViewSupport.display(app,a)+"\n"+ViewSupport.detail(a)+(a.reason.isBlank()?"":"\nNotes: "+a.reason);
        app.info(text+"\n\nThis appointment is a session-only fixture and cannot be changed here.");
    }

    private static class DoctorChoice {
        final String id,name,specialty;
        DoctorChoice(String id,String name,String specialty){this.id=id;this.name=name;this.specialty=specialty;}
        public String toString(){return name+"  ·  "+specialty;}
    }

}
