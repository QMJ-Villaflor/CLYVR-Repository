import clyvr.controller.AppController;
import clyvr.model.Entities.*;
import java.awt.*;
import java.lang.reflect.*;
import java.util.*;
import javax.swing.*;

/** Exercises the actual clickable Login -> Schedule -> Timeslot -> Book -> Review UI. */
public class UiInteractionTest {
 static AppController app; static JFrame frame;
 static void check(boolean good,String msg){if(!good)throw new AssertionError(msg);}
 static java.util.List<Component> walk(Component root){java.util.List<Component> all=new ArrayList<>();all.add(root);if(root instanceof Container)for(Component item:((Container)root).getComponents())all.addAll(walk(item));return all;}
 static JButton button(String match){for(Component c:walk(frame.getContentPane()))if(c instanceof JButton&&((JButton)c).getText()!=null&&((JButton)c).getText().contains(match))return (JButton)c;throw new AssertionError("No button containing: "+match);}
 static <T> T control(Class<T> type){for(Component c:walk(frame.getContentPane()))if(type.isInstance(c))return type.cast(c);throw new AssertionError("No "+type.getName());}
 static void dismissDialogs(){for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isVisible())w.dispose();}
 public static void main(String[] args) throws Exception {
  SwingUtilities.invokeAndWait(()->app=new AppController());Field f=AppController.class.getDeclaredField("frame");f.setAccessible(true);frame=(JFrame)f.get(app);
  SwingUtilities.invokeAndWait(()->{
    JTextField name=control(JTextField.class);JPasswordField pw=control(JPasswordField.class);
    name.setText("patient");pw.setText("patient123");button("Login").doClick();
    check(app.current.role==Role.PATIENT,"patient login via button");
    button("Schedule").doClick();check(button("Next")!=null,"calendar is open");
    JButton day=null;
    for(Component c:walk(frame.getContentPane()))if(c instanceof JButton){JButton b=(JButton)c;if(b.getText()!=null&&b.getText().matches("[0-9]{1,2}")&&b.getActionListeners().length>0&&Integer.parseInt(b.getText())>java.time.LocalDate.now().getDayOfMonth()){day=b;break;}}
    if(day==null){button("Next").doClick();for(Component c:walk(frame.getContentPane()))if(c instanceof JButton){JButton b=(JButton)c;if(b.getText()!=null&&b.getText().matches("[0-9]{1,2}")&&b.getActionListeners().length>0){day=b;break;}}}
    check(day!=null,"selectable future calendar date");day.doClick();
    JButton slot=null;for(Component c:walk(frame.getContentPane()))if(c instanceof JButton){JButton b=(JButton)c;
      if(b.getText()!=null&&b.getText().contains("Consultation / Follow-up")&&b.getText().contains("AVAILABLE")){slot=b;break;}}
    check(slot!=null,"open consultation timeslot");slot.doClick();
    check(button("Send appointment request")!=null,"booking screen reached");
    JTextArea reason=control(JTextArea.class);reason.setText("Routine consultation for persistent cough");
    javax.swing.Timer closer=new javax.swing.Timer(120,e->dismissDialogs());closer.setRepeats(true);closer.start();
    int before=app.store.appointments.size();button("Send appointment request").doClick();closer.stop();
    check(app.store.appointments.size()==before+1,"booking button creates real request");
    Appointment a=app.store.appointments.get(app.store.appointments.size()-1);
    check(a.status==Status.PENDING_RECEPTIONIST,"booking enters pending receptionist state");
    check(button("Schedule")!=null,"patient Schedule navigation remains visible after booking");
    app.logout();app.login("reception","reception123");
    button("Schedule").doClick();button("Review Requests").doClick();
    // Approve the first currently pending consultation through the actual receptionist UI.
    JButton approve=button("Approve");approve.doClick();
    check(app.store.appointments.stream().anyMatch(x->x.status==Status.CONFIRMED),"reception approval via button");
    System.out.println("PASS: UI login, calendar navigation, timeslot selection, booking form, appointment creation and reception approval");
    frame.dispose();
  });
 }
}
