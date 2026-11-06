# CLYVR — Swing client with database-backed workflows

**Java 21 Swing desktop • Java 17+ Spring Boot backend • Maven**

> Historical milestone and screenshot sections below describe earlier states
> of the project. The current authentication, session, and database behavior is
> summarized here and in [backend/README.md](backend/README.md).

### UI and authentication

- **Login** verifies the entered email or username and password against `public.users` through the backend. Password hashes and database credentials never enter the Swing process.
- **Sign up** creates a patient user and profile through the backend.
- Successful login creates an opaque eight-hour backend session. Protected requests carry its bearer token; logout and password change revoke sessions. A backend restart invalidates in-memory sessions.
- Sign-up first asks for **email and password**. Clicking Sign up opens a compact legal/contact information dialog, preserving the original requirement to record a patient name, date of birth, sex, address, and phone number before activation.
- The sidebar has **exactly five** links: **Dashboard, Schedule, Doctors, Services, About Us**. **Log out** is now in the top-right profile menu.
- Role-specific appointment navigation is found in **Schedule**: patients see **My Appointments / Doctor Proposals**, doctors see **My Appointments**, and receptionists see **Review Requests / Appointments**. Appointment-related screens remain their own View files.
- Administrator doctor management is still demo-only and updates local session fixtures.
- Patient booking, appointment records, proposals, time off, request decisions, profiles, and notifications now use the backend. Remaining local-only behavior is listed in the fixture section below.


This revision uses the **modified 30% CLYVR UI** as the visual reference: turquoise header, bright cyan sidebar, light blue content background, cyan cards, and dark teal text. Unlike the earlier 60% build, this edition was **rendered and screenshot-tested under a desktop display** before packaging.

## Quick start

### Supabase connection configuration

The database connection test reads `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, and
`SUPABASE_DB_PASSWORD` from the process environment. The launch scripts can
also load these three values from a project-root `.env` file. To set up a
local copy, copy `.env.example` to `.env` and replace the placeholders using
your own development connection details. Never commit or share `.env`.

The scripts do not execute `.env`; they parse simple `KEY=value` lines.
Blank lines and full-line `#` comments are ignored, matching single or double
quotes around values are removed, and inline comments are not interpreted.
Other well-formed variables are ignored. Existing process environment values
take precedence over `.env`. Malformed lines and duplicate database variables
are rejected without printing their contents.

The database test uses the configured database role and performs only the
read-only query `SELECT current_database(), current_timestamp`. Use a restricted
development role for application work; do not use the project owner credentials
for database persistence.

The backend also requires `CLYVR_HASH_SIGNER_KEY` and
`CLYVR_HASH_SALT_SEPARATOR` to verify the existing Firebase-compatible Scrypt
hashes. These values must match the parameters used to create
`users.password_hash`; they stay server-side and are never sent to Swing.
Unsupported existing hash formats fail closed without resetting or overwriting
stored hashes. Confirm compatibility before enabling real-user sign-in.

The backend also requires `CLYVR_HASH_SIGNER_KEY` and
`CLYVR_HASH_SALT_SEPARATOR` to verify the existing Firebase-compatible Scrypt
hashes. These values must match the parameters used to create
`users.password_hash`; they stay server-side and are never sent to Swing.
Unsupported existing hash formats fail closed without resetting or overwriting
stored hashes. Confirm compatibility before enabling real-user sign-in.

### Authenticated backend

A separate Java backend module now owns authentication and database operations for patient
profiles, appointment booking and status, doctor schedules, time off, doctor
proposals and patient approval/decline, receptionist request reviews,
appointment cancellation/rescheduling requests, and persistent notifications.
Patient booking and proposal decisions are validated and persisted through
asynchronous backend calls. The backend resolves each request's database user
ID and role from its session; the client cannot choose an identity by sending
an ID or development header. Proposal decline uses the distinct `DECLINED`
appointment status; receptionist rejection uses `REJECTED`. The Swing client uses
asynchronous HTTP calls; the launch scripts remove database credentials and
hash parameters and demo ID mappings from the desktop process. Firebase
Authentication is not used. See
[backend/README.md](backend/README.md) for endpoints, setup, and security limits.

### Synthetic demo ID mappings

The backend maps doctor directory aliases `D1`–`D4` to database doctor IDs
using server-side environment variables. These aliases identify doctors for
booking and do not authenticate the signed-in user. Backend role checks use
the current database `users.role`, not a role supplied by the Swing client.

### Windows / VSCode

1. Extract the entire ZIP. Do not open or run individual `.java` files outside the extracted project.
2. Install **JDK 21** and the VSCode Java Extension Pack.
3. Open the extracted `CLYVR_60_Revised` folder in VSCode.
4. Copy `.env.example` to `.env` and fill in the backend database connection, matching Scrypt parameters, and doctor mappings. Do not use a privileged database role in production.
5. Start the backend in one terminal using `.\run.ps1 backend`.
6. Start the Swing client in another terminal using `.\run.ps1`.
To run only the demo client, double-click **`run.bat`** or use:

```powershell
.\run.ps1
```

To run the read-only database connection test:

```powershell
.\run.ps1 db-test
```

### Linux / macOS

Copy `.env.example` to `.env`, fill in the backend connection, matching Scrypt parameters, and doctor mappings, then start the backend:

```bash
./run.sh backend
```

In another terminal, launch the Swing client:

```bash
./run.sh
```

Run the read-only database connection test with:

```bash
./run.sh db-test
```

**Important:** Launch from the project root so the `data/*.txt` startup files can be found. On some systems the app may need a desktop display rather than a headless terminal.

## Account setup

The local fixture accounts in `data/*.txt` are presentation/demo data and no
longer authenticate the application. Sign in with an existing database account
whose password hash uses the configured compatible Scrypt parameters, or use
Sign up to create a patient account. Doctor, receptionist, and administrator
accounts must be provisioned in the database by an authorized operator.

## Features and test flows

### Patient

- Log in using email (or seeded username) and use the dashboard actions or the five-link sidebar.
- Open **Schedule**. Filter by **Service OR Doctor**; choosing **Unsure** leads to General Medicine physicians.
- Select an enabled weekday on the month calendar. The next screen contains **eight appointment slots**, not a vertically scrolling list.
- Choose a consultation slot to create a five-minute hold, then complete the distinct **Book Appointment** form and enter a reason for visit. Confirmation converts the hold into a pending request for Reception.
- Open **Schedule → My Appointments** for status, cancellation, and actual reschedule requests. A reschedule request reserves the new slot while retaining the old one until Reception approves.
- Open **Schedule → Doctor Proposals** to approve a doctor-proposed follow-up/procedure, choose an alternative slot, or decline the proposal.

### Doctor

- Review their own appointments on **Dashboard** and **Schedule → My Appointments**.
- For an unoccupied slot: request time off for Reception approval, or create a backend-persisted follow-up/procedure proposal for a patient already seen in a completed consultation.
- For a confirmed future appointment: submit a cancellation request or send a rescheduling notice to the patient.
- After the appointment's scheduled end: mark **Completed** or **No-show**. Medical details are limited to patients assigned to that doctor.

### Receptionist

- Open **Schedule → Review Requests** for pending consultations, time-off requests, cancellation requests, and reschedule requests.
- Approve a valid request, or reject with **both** a dropdown reason and written explanation.
- Reschedule approval moves the appointment and releases the original slot.

### Administrator

- **Doctors → Doctor Management** is a local demo-only screen. It does not create or remove backend doctor records.
- The old **Admins directory** and administrator request-review privileges are removed.

### Shared

- Role-specific dashboards, five-link authenticated sidebar, doctors list, services list, about page, notification popover and full notifications screen, account information, and informational help assistant.
- One patient per doctor per slot. Eight slots are four 30-minute consultations/follow-ups and four 60-minute procedure/treatment visits, with transition breaks and lunch.

## Project structure

```text
CLYVR_60_Revised/
  src/clyvr/controller/AppController.java      Application navigation and UI coordination
  src/clyvr/model/Entities.java               Data classes / statuses
  src/clyvr/model/ClinicStore.java            Demo fixture state and static presentation helpers
  src/clyvr/ui/Theme.java                     Original palette and explicit button painting
  src/clyvr/view/*.java                       Each screen in its own View class
  src/clyvr/view/AuthLayout.java              Shared authentication layout
  src/clyvr/view/DashboardLayout.java         Shared role-dashboard layout
  src/clyvr/view/ViewSupport.java             Shared cards, typography and forms
  data/*.txt                                  Text-based, editable startup data
  tests/                                      Model, workflow, GUI interaction, capture tests
  previews/                                   Actual application screenshots
  run.bat / run.ps1 / run.sh                   Build and run scripts
```

## Historical visual and functional checks

- Java 21 compilation with no build tool: **passed**.
- `SmokeTest`: **passed**.
- These UI screenshots and fixture-based checks predate the backend migration and do not verify current database-backed workflows.
- `UiCapture` screenshots: **captured and inspected** for login, signup, each role's dashboard, schedule, timeslots, booking, request queue, approvals, doctor management, and narrower **1080 × 720** layouts. These files are in `previews/`.

### Re-running tests (Linux with Xvfb)

```bash
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name '*.java')
javac -encoding UTF-8 -cp out -d out tests/*.java
java -cp 'out:lib/postgresql-42.7.14.jar' SmokeTest
java -cp 'out:lib/postgresql-42.7.14.jar' WorkflowTest
xvfb-run -a -s '-screen 0 1440x900x24' java -cp 'out:lib/postgresql-42.7.14.jar' NavigationRegressionTest
xvfb-run -a -s '-screen 0 1440x900x24' java -cp 'out:lib/postgresql-42.7.14.jar' UiInteractionTest
xvfb-run -a -s '-screen 0 1440x900x24' java -cp 'out:lib/postgresql-42.7.14.jar' UiCapture
```

The app was **not** visually tested on your specific Windows PC, DPI scaling, or fonts. The layout uses Swing layout managers instead of manual absolute positioning to reduce platform-specific defects.

## Milestone 5 workflow audit

The application screens for patient booking, patient/doctor appointments,
doctor availability and time off, proposals and patient approval, receptionist
request decisions, patient profiles, and notifications use the backend. The
backend validates booking conflicts again during insert. Proposal rejection is
disabled until a declined-proposal appointment status is part of the verified
database contract.

Remaining `ClinicStore` dependencies are classified as follows:

- **Must migrate / currently blocked:** local login and signup, plus
  administrator doctor creation/removal. Firebase is not configured, and no
  supported identity/account-provisioning contract is available for creating
  real users safely without introducing a competing password system.
- **Demo-only:** `users.txt`, `doctors.txt`, and `appointments.txt`; the
  receptionist legacy fixture section; and the receptionist/administrator
  legacy schedule. The UI labels these as local/session fixtures. They are not
  authoritative for the database-backed workflows.
- **Can remain:** `ClinicStore` clinic-time, service-label, and business-hour
  constants used for presentation and date/slot display; these are not
  persisted records.
- **Obsolete/test-only desktop JDBC:** the old `AppointmentRepository`,
  `AvailabilityRepository`, and `DoctorRepository` are not called by
  application screens and are retained for existing mocked repository tests.
  The separate `db-test` diagnostic still performs a read-only direct JDBC
  query; it is not part of the Swing workflow, but should be moved behind the
  backend if the rule becomes “no desktop process may execute SQL.”

The verified `appointments.status` column is unconstrained `varchar`; the
backend now uses `REJECTED` for receptionist consultation rejection and
`DECLINED` for patient proposal decline. Both release the slot. Other
schema/security limitations remain: verified foreign keys and active-slot
uniqueness constraints are missing; `patient_profiles.user_id` has no verified
unique constraint; backend advisory locks do not coordinate external writers;
and the backend currently uses privileged credentials with temporary
development identity mappings. Do not deploy this local setup.

Latest verification: `mvn -f backend/pom.xml package` passed with 76 tests.
All 51 Swing source files and 12 desktop test files compiled. `SmokeTest`,
`BackendApiClientTest`, `DemoIdMappingTest`, `DoctorScheduleSelectionTest`,
`AppointmentRepositoryTest`, `AvailabilityRepositoryTest`,
`DoctorRepositoryTest`, and `PendingConsultationRepositoryTest` passed.
`WorkflowTest` fails on its fixture-date collision. `NavigationRegressionTest`
and `UiInteractionTest` were not rerun in this milestone; prior attempts
failed the sidebar assertion and timed out, respectively. No standalone Swing
launch, live Supabase operation, or database write was performed.
`git diff --check` passed.

## Data lifetime and future MySQL work

The files `data/users.txt`, `data/doctors.txt` and `data/appointments.txt` remain **demo fixtures** for local login, signup, the legacy fixture schedule, administrator doctor management, and test coverage. They are not the source of truth for migrated patient booking, patient/doctor appointments, proposals, availability, time off, appointment-change decisions, profiles, or notifications. The fixture-backed administrator and authentication flows remain local and are not real persistent account management.

## Remaining production work

- Firebase project setup, real-user authentication, backend migration of demo login/signup and administrator doctor management, multi-PC synchronization, encrypted accounts, and full audit/security controls.
- Sending email/SMS, external AI/LLM connection, clinical record management, medical decision-making.
- Automatic national/local holiday calendar; currently Saturday and Sunday are disabled by default.
- Professionally deployed healthcare use. This is a local academic demonstration.
#   C L Y V R - R e p o s i t o r y  
 