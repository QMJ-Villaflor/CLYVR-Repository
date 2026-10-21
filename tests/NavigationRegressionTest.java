import clyvr.controller.AppController;
import java.awt.*;
import java.lang.reflect.*;
import javax.swing.*;
import java.util.*;

/** Checks the five-link sidebar and role-specific Schedule entry points. */
public class NavigationRegressionTest {
 static AppController app;static JFrame frame;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static java.util.List<JButton> buttons(Component c){
  java.util.List<JButton> result=new ArrayList<>();
  if(c instanceof JButton b)result.add(b);
  if(c instanceof Container container)for(Component child:container.getComponents())result.addAll(buttons(child));
  return result;
 }
 static boolean contains(String name){return buttons(frame.getContentPane()).stream().anyMatch(b->b.getText()!=null&&b.getText().contains(name));}
 static void sidebar(){
  JPanel shell=(JPanel)frame.getContentPane();
  Component west=((BorderLayout)shell.getLayout()).getLayoutComponent(BorderLayout.WEST);
  String[] expected={"Dashboard","Schedule","Doctors","Services","About Us"};
  java.util.List<JButton> actual=buttons(west);
  check(actual.size()==expected.length,"Sidebar must contain only five links");
  for(int i=0;i<expected.length;i++)check(expected[i].equals(actual.get(i).getText()),"Sidebar item "+i+" is wrong");
 }
 public static void main(String[] args) throws Exception {
  SwingUtilities.invokeAndWait(()->app=new AppController());
  Field field=AppController.class.getDeclaredField("frame");field.setAccessible(true);frame=(JFrame)field.get(app);
  SwingUtilities.invokeAndWait(()->{
   check(contains("Login"),"Login button missing");
   app.showSignUp();check(contains("Sign up"),"Sign-up button missing");
   app.login("angela@example.org","patient123");check(app.current!=null,"Email login failed");
   sidebar();app.navigate("Schedule");check(contains("My Appointments")&&contains("Doctor Proposals"),"Patient Schedule links missing");
   app.logout();app.login("doctor","doctor123");sidebar();app.navigate("Schedule");
   check(contains("My Appointments")&&!contains("Doctor Proposals"),"Doctor Schedule actions incorrect");
   app.logout();app.login("reception","reception123");sidebar();app.navigate("Schedule");
   check(contains("Review Requests")&&contains("Appointments"),"Reception Schedule actions missing");
   app.logout();app.login("admin","admin123");sidebar();app.navigate("Schedule");
   check(!contains("Review Requests"),"Admin must not review requests");
   app.navigate("Doctors");check(contains("Add / remove doctors"),"Doctor management not reachable from Doctors");
   frame.dispose();System.out.println("PASS: two-field auth, email login, five-link sidebar, Schedule role actions, admin doctor management");
  });
 }
}
