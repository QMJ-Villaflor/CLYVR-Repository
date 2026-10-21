package clyvr.service;

public class SystemKnowledge {

    public static final String SYSTEM_DOCUMENTATION = """
        CLYVR CLINIC APPOINTMENT MANAGEMENT SYSTEM DOCUMENTATION

        === TERMINOLOGY & SCOPE ===
        - Product Name: CLYVR Clinic Appointment Management System (or 'system'). NEVER refer to it as an 'app' or 'application'.
        - Architecture: Java Swing MVC desktop system.

        === SYSTEM ROLES & ACCESS CONTROL ===
        - Patient: Books consultations, views doctor proposals, and requests reschedules or cancellations.
        - Doctor: Manages daily timeslot schedules, marks appointment outcomes (completed/no-show), and requests time off or reschedules.
        - Receptionist: Reviews patient booking & reschedule requests, approves slots or proposes alternate times, and manages patient check-ins.
        - Administrator: Monitors clinic operational analytics and manages physician accounts via 'Doctors -> Add / remove doctors'.
        - Logging Out: Users log out by clicking their profile dropdown menu at the top-right (● [FirstName] ▾) and selecting 'Log out'.

        === GLOBAL NAVIGATION & LAYOUT ===
        1. Top Header Bar (Dark Cyan Header):
           - Left: System logo ('CLYVR - Clinic Appointment Management System').
           - Right: 'Notifications' dropdown menu.
           - Right: User Profile dropdown ('● [FirstName] ▾') with 'Legal Information', 'Medical Information' (Patients only), 'Contact Information', and 'Log out'.

        2. Left Sidebar Navigation Menu (Cyan Bar):
           - 5 Links: 'Dashboard', 'Schedule', 'Doctors', 'Services', 'About Us'.
           - Selected links display a dark highlight background.

        3. Footer & Assistant Widget:
           - Bottom-Right Footer Button: '✦ AI Assistant' toggles the floating AI chat widget.
           - Floating Corner Widget: Circular cyan button with robot icon at bottom-right.

        === DETAILED WORKFLOW GUIDES ===
        - Booking an Appointment (Patient): Go to Schedule -> Filter by Service or Doctor -> Select Date -> Click an available slot -> Enter visit reason -> Click Continue.
        - Reviewing Requests (Receptionist): Go to Schedule -> Click 'Review Requests' shortcut (or Requests in sidebar) -> Approve request or propose an alternate slot.
        - Accepting Doctor Proposals (Patient): Go to Schedule -> Click 'Doctor Proposals' -> Review proposed timeslot -> Click Accept or Decline.
        - Requesting Time Off (Doctor): Go to Schedule -> Select date -> Click an available timeslot -> Enter reason -> Submit time-off request.
        - Doctor Account Management (Admin): Click 'Doctors' in sidebar -> Click 'Add / remove doctors' button at top-right.
        """;
}