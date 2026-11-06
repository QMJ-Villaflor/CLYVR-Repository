package clyvr.view;

import clyvr.controller.*;
import clyvr.database.DoctorDirectoryEntry;
import clyvr.model.Entities.*;
import clyvr.ui.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Public doctors directory with expandable professional experience details. */
public class DoctorsView extends JPanel {
 public DoctorsView(AppController app){
  super(new BorderLayout());
  JPanel p=ViewSupport.page("Our Doctors","Synthetic mapped database directory · demo login remains local");

  if(app.current.role==Role.ADMIN){
   JPanel controls=ViewSupport.row();
   JButton manage=Theme.button("Demo-only · +  Add / remove doctors");
   manage.addActionListener(e->app.navigate("Doctor Management"));
   controls.add(manage);
   p.add(controls);
   Theme.space(p,15);
  }

  JPanel directory=Theme.vertical();
  directory.setAlignmentX(Component.LEFT_ALIGNMENT);
  JLabel status=Theme.muted("Loading mapped synthetic doctors…");
  status.setAlignmentX(Component.LEFT_ALIGNMENT);
  p.add(status);
  Theme.space(p,10);
  JButton refresh=Theme.secondary("Refresh directory");
  refresh.setAlignmentX(Component.LEFT_ALIGNMENT);
  p.add(refresh);
  Theme.space(p,12);
  p.add(directory);
  refresh.addActionListener(e->loadDirectory(app,directory,status,refresh));
  loadDirectory(app,directory,status,refresh);
  add(ViewSupport.wrap(p));
 }

 private void loadDirectory(AppController app,JPanel directory,JLabel status,JButton refresh){
  refresh.setEnabled(false);
  status.setText("Loading mapped synthetic doctors…");
  directory.removeAll();
  directory.revalidate();
  directory.repaint();
  app.loadMappedDoctors(doctors->{
   directory.removeAll();
   for(DoctorDirectoryEntry doctor:doctors)addDoctorCard(directory,doctor);
   status.setText(doctors.isEmpty()
     ?"No mapped synthetic doctors are configured for this directory."
     :"Showing "+doctors.size()+" configured synthetic database doctor(s). Doctor management remains demo-only.");
   refresh.setEnabled(true);
   directory.revalidate();
   directory.repaint();
  },failure->{
   String message=failure.getMessage();
   if(message!=null&&message.contains("missing its linked user")){
    status.setText("A mapped doctor is missing its linked users row. Check the synthetic database records.");
   }else if(message!=null&&message.contains("more than one row")){
    status.setText("The database returned ambiguous rows for a mapped doctor. Check the synthetic records.");
   }else if(message!=null&&message.contains("IDs were not found")){
    status.setText("A configured synthetic doctor ID was not found. Check the local ID mappings.");
   }else{
    status.setText("Could not load the synthetic doctor directory. Check the database connection and configured ID mappings.");
   }
   refresh.setEnabled(true);
  });
 }

 private void addDoctorCard(JPanel p,DoctorDirectoryEntry doctor){
   String name=doctor.displayName();
   String specialty=doctor.specialty();

   JPanel card=Theme.card(new BorderLayout(14,10));
   card.setAlignmentX(Component.LEFT_ALIGNMENT);

   JPanel summary=new JPanel(new BorderLayout(12,4));
   summary.setOpaque(false);

   JPanel labels=new JPanel();
   labels.setOpaque(false);
   labels.setLayout(new BoxLayout(labels,BoxLayout.Y_AXIS));
   JLabel nameLabel=Theme.label(name,17,true);
   JLabel specialtyLabel=Theme.muted(specialty);
   labels.add(nameLabel);
   labels.add(Box.createVerticalStrut(5));
   labels.add(specialtyLabel);

   JButton detail=Theme.secondary("View details");
   JPanel buttonPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));
   buttonPanel.setOpaque(false);
   buttonPanel.add(detail);

   summary.add(labels,BorderLayout.CENTER);
   summary.add(buttonPanel,BorderLayout.EAST);
   card.add(summary,BorderLayout.NORTH);

   JPanel experiencePanel=new JPanel();
   experiencePanel.setOpaque(false);
   experiencePanel.setLayout(new BoxLayout(experiencePanel,BoxLayout.Y_AXIS));
   experiencePanel.setBorder(new EmptyBorder(14,0,0,0));

   JLabel heading=Theme.label("Professional Experience",14,true);
   heading.setAlignmentX(Component.LEFT_ALIGNMENT);

   JTextArea description=new JTextArea(getProfessionalExperience(specialty));
   description.setEditable(false);
   description.setFocusable(false);
   description.setOpaque(false);
   description.setLineWrap(true);
   description.setWrapStyleWord(true);
   description.setFont(Theme.font(13,false));
   description.setForeground(Theme.INK);
   description.setBorder(null);
   description.setAlignmentX(Component.LEFT_ALIGNMENT);
   description.setColumns(45);
   description.setRows(4);

   experiencePanel.add(heading);
   experiencePanel.add(Box.createVerticalStrut(7));
   experiencePanel.add(description);
   experiencePanel.setVisible(false);

   detail.addActionListener(e -> {
    boolean show=!experiencePanel.isVisible();
    experiencePanel.setVisible(show);
    detail.setText(show ? "Hide details" : "View details");
    if(show) card.add(experiencePanel,BorderLayout.CENTER);
    else card.remove(experiencePanel);
    card.revalidate();
    card.repaint();
   });

   p.add(card);
   Theme.space(p,12);
 }

 private String getProfessionalExperience(String specialty){
  String normalized=specialty==null ? "" : specialty.toLowerCase();

  if(normalized.contains("cardio"))
   return "Provides consultations for general heart care and the management of common cardiovascular conditions, including high blood pressure and heart disease.\n\nPatients can book routine heart checkups, blood pressure monitoring, or guidance regarding ongoing cardiovascular concerns.";

  if(normalized.contains("pediatric"))
   return "Provides consultations focused on children's health, routine checkups, "
    +"growth and development monitoring, and common childhood illnesses. Parents "
    +"can book visits to discuss their child's health and developmental needs.";

  if(normalized.contains("obstetric") || normalized.contains("gynec"))
   return "Provides consultations focused on women's health, reproductive health "
    +"concerns, routine gynecological examinations, and pregnancy-related care. "
    +"Patients can schedule visits for regular checkups and guidance on their "
    +"health concerns.";

  if(normalized.contains("odont") || normalized.contains("dental") || normalized.contains("dentist"))
   return "Provides consultations focused on oral health, routine dental "
    +"examinations, preventive care, and common dental concerns. Patients can "
    +"schedule visits for checkups, tooth discomfort, and advice on maintaining "
    +"healthy teeth and gums.";

  if(normalized.contains("veterinary") || normalized.contains("vet"))
   return "Provides consultations focused on animal health, routine pet "
    +"examinations, preventive care, and common pet health concerns. Pet owners "
    +"can schedule visits to discuss their animals' health and general care.";

  if(normalized.contains("general medicine") || normalized.contains("general practitioner"))
   return "Provides consultations focused on primary care, routine health "
    +"assessments, common illnesses, and preventive care. Patients can book "
    +"appointments for general checkups and guidance about everyday health concerns.";

  return "Provides consultations related to "+specialty+", including routine "
   +"assessments and guidance about common health concerns. Patients can book "
   +"an appointment to discuss their needs and appropriate next steps.";
 }
}
