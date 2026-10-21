package clyvr.view;

import clyvr.controller.AppController;
import java.awt.*;
import javax.swing.*;

/** Minimal login screen in the supplied white/cyan reference style. */
public class LoginView extends JPanel {
    public LoginView(AppController app) {
        super(new BorderLayout());
        JPanel form = AuthLayout.column();
        form.add(AuthLayout.heading("Login"));
        AuthLayout.space(form, 47);
        JTextField email = AuthLayout.emailField();form.add(email);
        AuthLayout.space(form, 25);
        JPasswordField password = AuthLayout.passwordField();form.add(password);
        AuthLayout.space(form, 50);
        JButton submit = AuthLayout.submit("Login");form.add(submit);
        submit.addActionListener(e -> app.login(email.getText(), new String(password.getPassword())));
        password.addActionListener(e -> submit.doClick());
        AuthLayout.space(form, 20);
        form.add(AuthLayout.footer("Don't have an account?", "Sign up", app::showSignUp));
        add(AuthLayout.shell(form),BorderLayout.CENTER);
    }
}
