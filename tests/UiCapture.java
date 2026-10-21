import clyvr.controller.AppController;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.time.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Real Swing render smoke-check; screenshots are saved to previews/. */
public class UiCapture {
    static AppController app;static JFrame frame;
    static void stage(String filename,Runnable change) throws Exception {
        SwingUtilities.invokeAndWait(change);
        Thread.sleep(550);
        Rectangle rect=new Rectangle(frame.getLocationOnScreen(),frame.getSize());
        BufferedImage image=new Robot().createScreenCapture(rect);
        ImageIO.write(image,"png",Path.of("previews",filename+".png").toFile());
        System.out.println("Captured "+filename+": "+image.getWidth()+"x"+image.getHeight());
    }
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("previews"));
        SwingUtilities.invokeAndWait(()->app=new AppController());
        Field f=AppController.class.getDeclaredField("frame");f.setAccessible(true);frame=(JFrame)f.get(app);
        stage("login",()->{});
        stage("signup",()->app.showSignUp());
        stage("login-return",()->app.showLogin());
        stage("patient-dashboard",()->app.login("patient","patient123"));
        stage("schedule",()->app.navigate("Schedule"));
        LocalDate day=LocalDate.now().plusDays(1);while(day.getDayOfWeek().getValue()>5)day=day.plusDays(1);
        final LocalDate selected=day;
        stage("timeslots",()->app.showTimeslots(selected,"D1","All services"));
        stage("booking",()->app.showBooking("D1",2));
        stage("patient-approvals",()->app.navigate("Patient Approvals"));
        stage("my-appointments",()->app.navigate("My Appointments"));
        stage("profile",()->app.navigate("My Information"));
        stage("receptionist",()->app.login("reception","reception123"));
        stage("requests",()->app.navigate("Requests"));
        stage("doctor",()->app.login("doctor","doctor123"));
        stage("admin",()->app.login("admin","admin123"));
        stage("doctor-management",()->app.navigate("Doctor Management"));
        frame.setSize(1080,720);frame.setLocationRelativeTo(null);
        stage("small-admin",()->app.navigate("Dashboard"));
        stage("small-schedule",()->{app.login("patient","patient123");app.navigate("Schedule");});
        stage("small-timeslots",()->app.showTimeslots(selected,"D1","All services"));
        stage("small-booking",()->app.showBooking("D1",2));
        SwingUtilities.invokeAndWait(()->frame.dispose());
    }
}
