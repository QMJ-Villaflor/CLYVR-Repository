package clyvr.view;

import clyvr.controller.AppController;
import clyvr.database.BackendPatientProfile;
import clyvr.database.PatientProfileUpdate;
import clyvr.model.Entities.Role;
import clyvr.model.Entities.User;
import clyvr.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JPasswordField;

/** Patient information is loaded and saved through the trusted backend. */
public class ProfileView extends JPanel {

    private final AppController app;
    private final JLabel status = Theme.muted("Loading your profile…");
    private final JButton save = Theme.button("Save changes");
    private final JButton reload = Theme.secondary("Refresh");
    private final JTextField firstName = field();
    private final JTextField lastName = field();
    private final JTextField birthDate = field();
    private final JTextField sex = field();
    private final JTextField address = field();
    private final JTextField email = field();
    private final JTextField phone = field();
    private final JTextField bloodType = field();
    private final JTextArea allergies = area();
    private final JTextArea conditions = area();
    private final JTextArea medications = area();
    private final JTextArea medicalHistory = area();
    private boolean profileLoaded;

    public ProfileView(AppController app) {
        super(new BorderLayout());
        this.app = app;
        if (app.current.role != Role.PATIENT) {
            addLegacyReadOnlyProfile(app.current);
            return;
        }

        JPanel page = ViewSupport.page(
                "My Information",
                "Patient information is loaded from the database. Email is read-only.");
        JPanel legal = ViewSupport.stackForm();
        formField(legal, "First name", firstName);
        formField(legal, "Last name", lastName);
        formField(legal, "Birth date (YYYY-MM-DD)", birthDate);
        formField(legal, "Sex", sex);
        formField(legal, "Address", address);

        JPanel contact = ViewSupport.stackForm();
        formField(contact, "Email address", email);
        email.setEditable(false);
        formField(contact, "Phone number", phone);

        JPanel left = Theme.card(new BorderLayout(0, 14));
        JPanel right = Theme.card(new BorderLayout(0, 14));
        left.add(Theme.label("Legal Information", 21, true), BorderLayout.NORTH);
        left.add(legal, BorderLayout.CENTER);
        right.add(Theme.label("Contact Information", 21, true), BorderLayout.NORTH);
        right.add(contact, BorderLayout.CENTER);
        JPanel grid = new JPanel(new GridLayout(1, 2, 20, 0));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.add(left);
        grid.add(right);
        page.add(grid);
        Theme.space(page, 16);

        JPanel medical = Theme.card(new BorderLayout(0, 12));
        medical.add(Theme.label("Medical Information", 21, true), BorderLayout.NORTH);
        JPanel rows = new JPanel(new GridLayout(0, 2, 18, 12));
        rows.setOpaque(false);
        JPanel first = ViewSupport.stackForm();
        JPanel second = ViewSupport.stackForm();
        formField(first, "Blood type", bloodType);
        formField(first, "Allergies", allergies);
        formField(first, "Medical conditions", conditions);
        formField(second, "Current medications", medications);
        formField(second, "Medical history", medicalHistory);
        rows.add(first);
        rows.add(second);
        medical.add(rows, BorderLayout.CENTER);
        medical.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(medical);

        JPanel actions = ViewSupport.row();
        save.setEnabled(false);
        reload.addActionListener(event -> loadProfile());
        save.addActionListener(event -> saveProfile());
        actions.add(save);
        actions.add(reload);
        actions.add(status);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        Theme.space(page, 12);
        page.add(actions);
        addPasswordChange(page);
        add(ViewSupport.wrap(page));
        loadProfile();
    }

    private void loadProfile() {
        setBusy(true);
        profileLoaded = false;
        status.setText("Loading your profile…");
        app.loadPatientProfile(profile -> {
            populate(profile);
            profileLoaded = true;
            setBusy(false);
            status.setText("Profile loaded from the database.");
        }, failure -> {
            setBusy(false);
            save.setEnabled(false);
            if (failure instanceof RuntimeException
                    && failure.getCause() instanceof RuntimeException cause) {
                failure = cause;
            }
            if (failure instanceof clyvr.service.BackendApiClient.BackendApiException apiFailure
                    && apiFailure.statusCode() == 404) {
                status.setText("No patient profile exists for this account. No profile was created.");
            } else if (failure instanceof clyvr.service.BackendApiClient.BackendApiException apiFailure
                    && apiFailure.statusCode() == 409) {
                status.setText("The database has duplicate patient profiles. Editing is disabled.");
            } else {
                status.setText("Could not load the database profile. Use Refresh to try again.");
            }
        });
    }

    private void saveProfile() {
        if (!profileLoaded) {
            return;
        }
        PatientProfileUpdate update = new PatientProfileUpdate(
                firstName.getText(),
                lastName.getText(),
                phone.getText(),
                birthDate.getText(),
                sex.getText(),
                address.getText(),
                bloodType.getText(),
                allergies.getText(),
                conditions.getText(),
                medications.getText(),
                medicalHistory.getText());
        setBusy(true);
        status.setText("Saving changes to the database…");
        app.savePatientProfile(update, profile -> {
            populate(profile);
            profileLoaded = true;
            setBusy(false);
            status.setText("Changes saved to the database.");
        }, failure -> {
            setBusy(false);
            profileLoaded = true;
            save.setEnabled(true);
            status.setText(failure instanceof IllegalArgumentException
                    ? "The profile contains an invalid value; check the birth date and field lengths."
                    : "Profile changes were not saved. Check the connection and try again.");
        });
    }

    private void populate(BackendPatientProfile profile) {
        firstName.setText(profile.firstName());
        lastName.setText(profile.lastName());
        birthDate.setText(profile.birthDate());
        sex.setText(profile.sex());
        address.setText(profile.address());
        email.setText(profile.email());
        phone.setText(profile.phone());
        bloodType.setText(profile.bloodType());
        allergies.setText(profile.allergies());
        conditions.setText(profile.conditions());
        medications.setText(profile.medications());
        medicalHistory.setText(profile.medicalHistory());
    }

    private void setBusy(boolean busy) {
        reload.setEnabled(!busy);
        boolean editable = !busy && profileLoaded;
        save.setEnabled(editable);
        firstName.setEnabled(editable);
        lastName.setEnabled(editable);
        birthDate.setEnabled(editable);
        sex.setEnabled(editable);
        address.setEnabled(editable);
        phone.setEnabled(editable);
        bloodType.setEnabled(editable);
        allergies.setEnabled(editable);
        conditions.setEnabled(editable);
        medications.setEnabled(editable);
        medicalHistory.setEnabled(editable);
    }

    private void addLegacyReadOnlyProfile(User user) {
        JPanel page = ViewSupport.page(
                "My Information", "This non-patient profile still uses the local demo account.");
        JPanel legal = ViewSupport.stackForm();
        ViewSupport.formValue(legal, "Last name", user.lastName);
        ViewSupport.formValue(legal, "First name", user.firstName);
        ViewSupport.formValue(legal, "Birth date", user.birthDate);
        ViewSupport.formValue(legal, "Sex", user.sex);
        ViewSupport.formValue(legal, "Address", user.address);
        JPanel contact = ViewSupport.stackForm();
        ViewSupport.formValue(contact, "Email address", user.email);
        ViewSupport.formValue(contact, "Phone number", user.phone);
        JPanel grid = new JPanel(new GridLayout(1, 2, 20, 0));
        grid.setOpaque(false);
        grid.add(legal);
        grid.add(contact);
        page.add(grid);
        addPasswordChange(page);
        add(ViewSupport.wrap(page));
    }

    private void addPasswordChange(JPanel page) {
        Theme.space(page, 18);
        JPanel security = Theme.card(new BorderLayout(0, 12));
        security.add(Theme.label("Change password", 21, true), BorderLayout.NORTH);
        JPanel fields = ViewSupport.stackForm();
        JPasswordField currentPassword = new JPasswordField();
        JPasswordField newPassword = new JPasswordField();
        JPasswordField confirmation = new JPasswordField();
        formField(fields, "Current password", currentPassword);
        formField(fields, "New password", newPassword);
        formField(fields, "Confirm new password", confirmation);
        security.add(fields, BorderLayout.CENTER);
        JButton change = Theme.button("Change password");
        JLabel passwordStatus = Theme.muted("");
        JPanel actions = ViewSupport.row();
        actions.add(change);
        actions.add(passwordStatus);
        security.add(actions, BorderLayout.SOUTH);
        change.addActionListener(event -> {
            String currentValue = new String(currentPassword.getPassword());
            String newValue = new String(newPassword.getPassword());
            if (!newValue.equals(new String(confirmation.getPassword()))) {
                passwordStatus.setText("The new passwords do not match.");
                return;
            }
            change.setEnabled(false);
            passwordStatus.setText("Updating password…");
            app.changePassword(currentValue, newValue, () -> {
                currentPassword.setText("");
                newPassword.setText("");
                confirmation.setText("");
                passwordStatus.setText("Password changed. Sign in again.");
                app.info("Password changed. Please sign in again.");
            }, failure -> {
                change.setEnabled(true);
                passwordStatus.setText(failure instanceof RuntimeException
                        ? failure.getMessage() : "Password was not changed.");
            });
        });
        security.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(security);
    }

    private static JTextField field() {
        JTextField field = Theme.field();
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        return field;
    }

    private static JTextArea area() {
        JTextArea area = Theme.area(3);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    private static void formField(JPanel parent, String label, JTextField field) {
        parent.add(Theme.label(label, 16, true));
        parent.add(field);
        parent.add(javax.swing.Box.createVerticalStrut(8));
    }

    private static void formField(JPanel parent, String label, JTextArea area) {
        parent.add(Theme.label(label, 16, true));
        parent.add(new javax.swing.JScrollPane(area));
        parent.add(javax.swing.Box.createVerticalStrut(8));
    }
}
