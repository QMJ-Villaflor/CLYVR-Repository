package clyvr.view;

import clyvr.controller.AppController;
import clyvr.model.Entities.*;
import clyvr.ui.Theme;
import java.awt.*;
import java.time.*;
import javax.swing.*;

/** Reference-style sign-up: email/password first; essential legal details on submission. */
public class SignUpView extends JPanel {
    public SignUpView(AppController app) {
        super(new BorderLayout());
        JPanel form = AuthLayout.column();
        form.add(AuthLayout.heading("Sign up"));AuthLayout.space(form,47);
        JTextField email = AuthLayout.emailField();form.add(email);
        AuthLayout.space(form,25);
        JPasswordField password = AuthLayout.passwordField();form.add(password);
        AuthLayout.space(form,50);
        JButton submit = AuthLayout.submit("Sign up");form.add(submit);
        submit.addActionListener(e -> signUp(app,email.getText().trim(),new String(password.getPassword())));
        password.addActionListener(e -> submit.doClick());
        AuthLayout.space(form,20);
        form.add(AuthLayout.footer("Already have an account?", "Login", app::showLogin));
        add(AuthLayout.shell(form),BorderLayout.CENTER);
    }
    private void signUp(AppController app,String email,String password) {
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {app.error("Enter a valid email address.");return;}
        if (password.isBlank()) {app.error("Please enter a password.");return;}
        if (app.store.users.stream().anyMatch(u->u.email.equalsIgnoreCase(email)||u.username.equalsIgnoreCase(email))) {
            app.error("An account with this email address already exists.");return;
        }
        // Legal/contact fields remain required from the existing specification.
        JTextField first=Theme.field(),last=Theme.field(),birth=Theme.field(),address=Theme.field(),phone=Theme.field();
        JComboBox<String> sex=new JComboBox<>(new String[]{"Select", "Female", "Male", "Prefer not to say"});
        JPanel details=new JPanel(new GridLayout(0,2,12,11));details.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
        addDetail(details,"First name",first);addDetail(details,"Last name",last);
        addDetail(details,"Birth date (YYYY-MM-DD)",birth);addDetail(details,"Sex",sex);
        addDetail(details,"Address",address);addDetail(details,"Phone number",phone);
        while(true){
            int action=JOptionPane.showConfirmDialog(this,details,"Complete patient information",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
            if(action!=JOptionPane.OK_OPTION)return;
            if(first.getText().isBlank()||last.getText().isBlank()||birth.getText().isBlank()||address.getText().isBlank()||phone.getText().isBlank()||sex.getSelectedIndex()==0){
                app.error("Complete all legal and contact information.");continue;
            }
            LocalDate birthday;
            try{birthday=LocalDate.parse(birth.getText().trim());if(birthday.isAfter(LocalDate.now()))throw new IllegalArgumentException();}
            catch(Exception ex){app.error("Use YYYY-MM-DD for a valid birth date.");continue;}
            User u=new User(app.store.next("U"),email,password,Role.PATIENT,first.getText().trim(),last.getText().trim(),email,phone.getText().trim());
            u.birthDate=birthday.toString();u.sex=(String)sex.getSelectedItem();u.address=address.getText().trim();
            app.store.users.add(u);app.info("Your patient account is ready. Log in with your email address.");app.showLogin();return;
        }
    }
    private void addDetail(JPanel panel,String label,JComponent field){panel.add(new JLabel(label));panel.add(field);}
}
