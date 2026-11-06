package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.DoctorDirectoryEntry;
import clyvr.database.DoctorScheduleSelection;
import clyvr.model.ClinicStore;
import clyvr.model.Entities.*;
import clyvr.ui.Theme;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Month calendar with consistent hover tooltips, cursors, and visual feedback across date cells. */
public class ScheduleView extends JPanel {
    private YearMonth month = YearMonth.from(LocalDate.now());
    private final AppController app;
    private final JComboBox<String> service = new JComboBox<>();
    private final JComboBox<Object> doctor = new JComboBox<>();
    private final JPanel calendar = new JPanel(new BorderLayout(0, 8));
    private final JLabel monthTitle = Theme.label("", 22, true);
    private final JLabel dataStatus = Theme.muted("");
    private java.util.List<DoctorDirectoryEntry> databaseDoctors = java.util.List.of();
    private boolean databaseDoctorsReady;
    private boolean databaseDoctorLoadFailed;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMM d");

    public ScheduleView(AppController app){
        super(new BorderLayout());
        this.app = app;

        String subtitle = switch(app.current.role) {
          case PATIENT -> "Select a service OR a doctor, then a date and a timeslot to request a consultation.";
          case DOCTOR -> "Review your assigned timeslots, patient visits and schedule requests.";
          case RECEPTIONIST -> "DEMO ONLY — this calendar shows local fixture availability. Review persisted requests in Review Requests.";
          case ADMIN -> "DEMO ONLY — this calendar shows local fixture availability. Doctor account management is also local.";
        };

        JPanel page = ViewSupport.page("Schedule", subtitle);

        JPanel shortcuts = ViewSupport.row();
        shortcuts.setBorder(new EmptyBorder(0, 0, 2, 0));
        switch(app.current.role) {
          case PATIENT -> {
            shortcut(shortcuts, "My Appointments", () -> app.navigate("My Appointments"));
            shortcut(shortcuts, "Doctor Proposals", () -> app.navigate("Patient Approvals"));
          }
          case DOCTOR -> shortcut(shortcuts, "My Appointments", () -> app.navigate("My Appointments"));
          case RECEPTIONIST -> {
            shortcut(shortcuts, "Review Requests", () -> app.navigate("Requests"));
            shortcut(shortcuts, "Appointments", () -> app.navigate("My Appointments"));
          }
          case ADMIN -> { }
        }
        if (shortcuts.getComponentCount() > 0) {
            page.add(shortcuts);
            Theme.space(page, 7);
        }

        JPanel filters = Theme.card(new BorderLayout(12, 3));
        filters.setAlignmentX(Component.LEFT_ALIGNMENT);
        filters.setBorder(new EmptyBorder(11, 18, 11, 18));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 13, 4));
        controls.setOpaque(false);
        controls.add(Theme.label("Service", 14, true));
        service.addItem("All services");
        service.addItem("Unsure — see a physician");
        for (String s : ClinicStore.SERVICES) service.addItem(s);
        service.setPreferredSize(new Dimension(242, 39));
        controls.add(service);

        controls.add(Theme.label("OR  Doctor", 14, true));
        if (app.current.role == Role.PATIENT) {
            doctor.addItem("Loading database doctors…");
            databaseDoctorsReady = false;
        } else if (app.current.role == Role.DOCTOR) {
            doctor.addItem("My database schedule");
            databaseDoctorsReady = true;
        } else {
            doctor.addItem("All doctors");
            for (Doctor d : app.store.doctors) {
                if (app.current.role != Role.DOCTOR || d.id.equals(app.currentDoctorId())) {
                    doctor.addItem(new DemoDoctorChoice(d.id, app.store.doctorName(d.id)));
                }
            }
            if (app.current.role == Role.DOCTOR && doctor.getItemCount() > 1) {
                doctor.setSelectedIndex(1);
            }
            databaseDoctorsReady = true;
        }
        doctor.setPreferredSize(new Dimension(230, 39));
        doctor.setRenderer(new DoctorOptionRenderer());
        controls.add(doctor);
        filters.add(controls, BorderLayout.CENTER);

        dataStatus.setText(app.current.role == Role.PATIENT
            ? "Loading synthetic database doctors. Availability is read through the local backend."
            : app.current.role == Role.DOCTOR
                ? "Your appointment schedule and slot availability are loaded from the backend."
                : "Legacy session fixture schedule — not database availability.");
        filters.add(dataStatus, BorderLayout.SOUTH);

        page.add(filters);
        Theme.space(page, 13);

        JPanel outer = Theme.card(new BorderLayout(10, 10));
        outer.setAlignmentX(Component.LEFT_ALIGNMENT);
        outer.setBorder(new EmptyBorder(14, 19, 14, 19));
        outer.add(calendar);

        page.add(outer);

        service.addActionListener(e -> {
            if (service.getSelectedIndex() > 0 && doctor.getSelectedIndex() > 0) doctor.setSelectedIndex(0);
        });
        doctor.addActionListener(e -> {
            if (doctor.getSelectedIndex() > 0 && service.getSelectedIndex() > 0) service.setSelectedIndex(0);
        });

        if (app.current.role == Role.PATIENT) {
            app.loadMappedDoctors(doctors -> {
                databaseDoctors = java.util.List.copyOf(doctors);
                doctor.removeAllItems();
                doctor.addItem("All doctors");
                for (DoctorDirectoryEntry entry : databaseDoctors) {
                    doctor.addItem(entry);
                }
                databaseDoctorsReady = true;
                dataStatus.setText(databaseDoctors.isEmpty()
                    ? "No mapped synthetic doctors are available. Check the backend mappings."
                    : "Database doctors loaded. Available consultation slots can be requested; the backend rechecks each slot before saving.");
                rebuild();
            }, failure -> {
                databaseDoctorLoadFailed = true;
                doctor.removeAllItems();
                doctor.addItem("Database doctor list unavailable");
                dataStatus.setText("Could not load mapped database doctors. Refresh the directory before scheduling.");
                rebuild();
            });
        } else if (app.current.role == Role.DOCTOR) {
            dataStatus.setText("Your database schedule supports persisted time-off requests and proposals to eligible patients.");
        }

        rebuild();
        add(ViewSupport.wrap(page));
    }

    private void shortcut(JPanel row, String text, Runnable action){
        JButton button = Theme.secondary(text + "  ›");
        button.addActionListener(e -> action.run());
        row.add(button);
    }

    private void rebuild(){
        calendar.removeAll();
        calendar.setOpaque(false);

        JPanel nav = new JPanel(new BorderLayout());
        nav.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        JButton prev = Theme.secondary("‹  Previous");
        prev.addActionListener(e -> { month = month.minusMonths(1); rebuild(); });
        prev.setEnabled(!month.equals(YearMonth.from(LocalDate.now())));

        JButton next = Theme.secondary("Next  ›");
        next.addActionListener(e -> { month = month.plusMonths(1); rebuild(); });
        next.setEnabled(month.isBefore(YearMonth.from(LocalDate.now().plusDays(30))));

        if (prev.isEnabled()) left.add(prev);
        if (next.isEnabled()) left.add(next);
        nav.add(left, BorderLayout.WEST);

        monthTitle.setText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        nav.add(monthTitle, BorderLayout.EAST);
        calendar.add(nav, BorderLayout.NORTH);

        JPanel days = new JPanel(new GridLayout(0, 7, 6, 6));
        days.setOpaque(false);
        for (String s : new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}) {
            JLabel l = Theme.label(s, 14, true);
            l.setHorizontalAlignment(SwingConstants.CENTER);
            days.add(l);
        }

        LocalDate first = month.atDay(1);
        int blanks = first.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < blanks; i++) {
            JPanel empty = new JPanel();
            empty.setOpaque(false);
            days.add(empty);
        }

        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate selected = month.atDay(d);
            boolean enabled = app.store.clinicOpen(selected) && !selected.isBefore(LocalDate.now()) && !selected.isAfter(LocalDate.now().plusDays(30));
            if (app.current.role == Role.PATIENT && (!databaseDoctorsReady || databaseDoctorLoadFailed || databaseDoctors.isEmpty())) {
                enabled = false;
            }
            String dateLabel = selected.format(DATE_FORMATTER);

            if (enabled) {
                JButton cell = new JButton(Integer.toString(d));
                cell.setUI(new javax.swing.plaf.basic.BasicButtonUI());
                cell.setOpaque(true);
                cell.setContentAreaFilled(true);
                cell.setBackground(Theme.WHITE);
                cell.setForeground(Theme.INK);
                cell.setFont(Theme.font(16, selected.equals(LocalDate.now())));
                cell.setPreferredSize(new Dimension(72, 35));
                cell.setBorder(BorderFactory.createLineBorder(Theme.LINE));
                cell.setFocusPainted(false);
                cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                cell.setToolTipText("View timeslots for " + dateLabel);
                cell.addActionListener(e -> open(selected));

                // Rollover hover effect for smooth visual response
                cell.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        cell.setBackground(new Color(230, 242, 245));
                    }
                    @Override
                    public void mouseExited(MouseEvent e) {
                        cell.setBackground(Theme.WHITE);
                    }
                });

                days.add(cell);
            } else {
                JLabel closed = Theme.label(Integer.toString(d), 16, false);
                closed.setForeground(Theme.MUTED);
                closed.setHorizontalAlignment(SwingConstants.CENTER);
                closed.setOpaque(true);
                closed.setBackground(new Color(192, 221, 226));
                closed.setBorder(BorderFactory.createLineBorder(Theme.LINE));
                closed.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                closed.setToolTipText("Clinic unavailable on " + dateLabel);
                days.add(closed);
            }
        }
        calendar.add(days, BorderLayout.CENTER);
        calendar.revalidate();
        calendar.repaint();
    }

    private void open(LocalDate day){
        String chosen = (String) service.getSelectedItem();
        if (app.current.role == Role.DOCTOR) {
            app.showDoctorDatabaseSchedule(day);
            return;
        }
        if (app.current.role == Role.PATIENT) {
            DoctorDirectoryEntry selected = doctor.getSelectedItem() instanceof DoctorDirectoryEntry entry
                ? entry
                : null;
            app.showDatabaseTimeslots(new DoctorScheduleSelection(
                day,
                chosen,
                selected,
                databaseDoctors
            ));
            return;
        }
        Object selectedDoctor = doctor.getSelectedItem();
        String did = "";
        if (selectedDoctor instanceof DemoDoctorChoice demoDoctor) {
            did = demoDoctor.id;
        }
        if (app.current.role == Role.DOCTOR) did = app.currentDoctorId();
        app.showTimeslots(day, did, chosen);
    }

    private record DemoDoctorChoice(String id, String name) {
        @Override public String toString(){return name;}
    }

    private static final class DoctorOptionRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(
                JList<?> list,Object value,int index,boolean selected,boolean focused) {
            String label=value instanceof DoctorDirectoryEntry entry
                ? entry.displayName()+" · "+entry.specialty()
                : value instanceof DemoDoctorChoice demo ? demo.name : String.valueOf(value);
            return super.getListCellRendererComponent(list,label,index,selected,focused);
        }
    }
}