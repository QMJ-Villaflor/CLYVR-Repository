package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.Entities.*;
import clyvr.model.ClinicStore;
import clyvr.ui.Theme;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Layout primitives: explicit padding, alignment, readable text and consistent cards. */
public final class ViewSupport {
    private ViewSupport() {}
    public static JPanel page(String title,String subtitle) {
        JPanel p=Theme.vertical();p.setBorder(new EmptyBorder(26,28,28,28));
        JLabel h=Theme.label(title,27,true);h.setAlignmentX(Component.LEFT_ALIGNMENT);p.add(h);
        Theme.space(p,7);
        JLabel sub=Theme.muted(subtitle);sub.setAlignmentX(Component.LEFT_ALIGNMENT);p.add(sub);
        Theme.space(p,21);return p;
    }
    public static JPanel row(){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,12,6));p.setOpaque(false);p.setAlignmentX(Component.LEFT_ALIGNMENT);return p;}
    public static JPanel listCard(){JPanel p=Theme.card(new BorderLayout(15,9));p.setAlignmentX(Component.LEFT_ALIGNMENT);p.setMaximumSize(new Dimension(Integer.MAX_VALUE,118));return p;}
    public static void line(JPanel root,String title,String desc,JButton... actions){
        JPanel item=listCard();
        JPanel text=new JPanel();text.setOpaque(false);text.setLayout(new BoxLayout(text,BoxLayout.Y_AXIS));
        JLabel head=Theme.label(title,17,true);text.add(head);text.add(Box.createVerticalStrut(7));
        JTextArea d=new JTextArea(desc,2,24);d.setEditable(false);d.setFocusable(false);d.setOpaque(false);d.setForeground(Theme.INK);d.setFont(Theme.font(13,false));d.setLineWrap(true);d.setWrapStyleWord(true);
        d.setBorder(null);d.setAlignmentX(Component.LEFT_ALIGNMENT);d.setPreferredSize(new Dimension(300,38));text.add(d);
        item.add(text,BorderLayout.CENTER);
        if(actions.length>0){JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,7,6));buttons.setOpaque(false);for(JButton b:actions)buttons.add(b);item.add(buttons,BorderLayout.EAST);}
        root.add(item);Theme.space(root,12);
    }
    public static String display(AppController app,Appointment a){return a.id+"  ·  "+app.store.patientName(a.patientId)+"  ·  "+app.store.doctorName(a.doctorId);}
    public static String detail(Appointment a){return a.type.toString().replace('_',' ')+"   •   "+a.day+"   •   "+ClinicStore.TIMES[a.slot]+"   •   "+a.status.toString().replace('_',' ');}
    public static JPanel empty(String s){JPanel p=Theme.card(new BorderLayout());p.add(Theme.muted(s),BorderLayout.CENTER);p.setAlignmentX(Component.LEFT_ALIGNMENT);p.setMaximumSize(new Dimension(Integer.MAX_VALUE,85));return p;}
    public static JPanel wrap(JPanel root){JPanel p=Theme.panel(new BorderLayout());p.add(Theme.scroll(root));return p;}
    public static String reasonDialog(Component parent){
        JComboBox<String> reasons=new JComboBox<>(new String[]{"Incomplete patient information","Invalid appointment details","Duplicate request","Other"});
        JTextArea explanation=Theme.area(4);
        JPanel form=new JPanel(new BorderLayout(8,9));form.setBorder(new EmptyBorder(8,8,8,8));
        JPanel top=new JPanel(new BorderLayout(5,5));top.add(Theme.label("Select a rejection reason",14,true),BorderLayout.NORTH);top.add(reasons,BorderLayout.CENTER);
        form.add(top,BorderLayout.NORTH);
        JPanel details=new JPanel(new BorderLayout(5,5));details.add(Theme.label("Written explanation (required)",14,true),BorderLayout.NORTH);details.add(new JScrollPane(explanation),BorderLayout.CENTER);form.add(details,BorderLayout.CENTER);
        while(true){int r=JOptionPane.showConfirmDialog(parent,form,"Reject request",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);if(r!=JOptionPane.OK_OPTION)return null;
            String problem=clyvr.security.Validator.longText("Explanation",explanation.getText());
            if(problem==null)return reasons.getSelectedItem()+": "+explanation.getText().trim();
            JOptionPane.showMessageDialog(parent,problem,"Explanation required",JOptionPane.WARNING_MESSAGE);
        }
    }
    public static boolean writable(String s){return s!=null&&!s.isBlank();}
    public static JLabel header(String title){JLabel l=Theme.label(title,19,true);l.setAlignmentX(Component.LEFT_ALIGNMENT);return l;}
    public static JPanel pad(Component content){JPanel p=new JPanel(new BorderLayout());p.setOpaque(false);p.add(content);return p;}
    public static JPanel stackForm(){JPanel p=new JPanel();p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;}
    public static void formValue(JPanel p,String title,String value){p.add(Theme.label(title,16,true));p.add(Box.createVerticalStrut(7));JTextField f=Theme.field();f.setText(value);f.setEditable(false);f.setMaximumSize(new Dimension(Integer.MAX_VALUE,45));f.setAlignmentX(Component.LEFT_ALIGNMENT);p.add(f);p.add(Box.createVerticalStrut(8));}
}
