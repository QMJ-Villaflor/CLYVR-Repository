# CLYVR — Revised 60% Progress

**Java 21 • Swing • MVC • VSCode • No Maven/Gradle**

### October 21 authentication and navigation refinement

- **Login** and **Sign up** use the supplied white/cyan mockup: centered logo, white outlined card, email/password fields, cyan action button, and bottom text link.
- Existing demo accounts may log in with **email address OR username**; their passwords have not changed.
- Sign-up first asks for **email and password**. Clicking Sign up opens a compact legal/contact information dialog, preserving the original requirement to record a patient name, date of birth, sex, address, and phone number before activation.
- The sidebar has **exactly five** links: **Dashboard, Schedule, Doctors, Services, About Us**. **Log out** is now in the top-right profile menu.
- Role-specific appointment navigation is found in **Schedule**: patients see **My Appointments / Doctor Proposals**, doctors see **My Appointments**, and receptionists see **Review Requests / Appointments**. Appointment-related screens remain their own View files.
- The Administrator can manage doctor accounts using the **Add / remove doctors** button on **Doctors**.
- The rest of the approved 60% workflows and in-memory data model are unchanged.


This revision uses the **modified 30% CLYVR UI** as the visual reference: turquoise header, bright cyan sidebar, light blue content background, cyan cards, and dark teal text. Unlike the earlier 60% build, this edition was **rendered and screenshot-tested under a desktop display** before packaging.

## Quick start

### Windows / VSCode

1. Extract the entire ZIP. Do not open or run individual `.java` files outside the extracted project.
2. Install **JDK 21** and the VSCode Java Extension Pack.
3. Open the extracted `CLYVR_60_Revised` folder in VSCode.
4. Double-click **`run.bat`**, or open PowerShell in the project folder and run:

```powershell
.\run.ps1
```

Alternatively, compile and run manually from the project's top folder:

```powershell
New-Item -ItemType Directory -Force out | Out-Null
$files = Get-ChildItem .\src -Recurse -Filter *.java | ForEach-Object FullName
javac -encoding UTF-8 -d out $files
java -cp out clyvr.controller.AppController
```

### Linux / macOS

```bash
./run.sh
```

Or:

```bash
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name '*.java')
java -cp out clyvr.controller.AppController
```

**Important:** Launch from the project root so the `data/*.txt` startup files can be found. On some systems the app may need a desktop display rather than a headless terminal.

## Sample accounts

| Role | Username | Password |
| --- | --- | --- |
| Patient (Angela) | `patient` | `patient123` |
| Patient (Marco) | `patient2` | `patient123` |
| Doctor (General Medicine) | `doctor` | `doctor123` |
| Doctor (Pediatrics) | `doctor2` | `doctor123` |
| Doctor (OB-GYN) | `doctor3` | `doctor123` |
| Doctor (Odontology) | `doctor4` | `doctor123` |
| Receptionist | `reception` | `reception123` |
| Administrator | `admin` | `admin123` |

These credentials are solely for a **local demonstration**; passwords are stored in plain text in the startup files and are not suitable for real patients.

## Features and test flows

### Patient

- Log in using email (or seeded username) and use the dashboard actions or the five-link sidebar.
- Open **Schedule**. Filter by **Service OR Doctor**; choosing **Unsure** leads to General Medicine physicians.
- Select an enabled weekday on the month calendar. The next screen contains **eight appointment slots**, not a vertically scrolling list.
- Choose a consultation slot; complete the distinct **Book Appointment** form and enter a reason for visit. This reserves the slot while Reception reviews it.
- Open **Schedule → My Appointments** for status, cancellation, and actual reschedule requests. A reschedule request reserves the new slot while retaining the old one until Reception approves.
- Open **Schedule → Doctor Proposals** to approve doctor-proposed follow-up/procedure, choose an alternative slot, or decline entirely.

### Doctor

- Review their own appointments on **Dashboard** and **Schedule → My Appointments**.
- For an unoccupied slot: request time off for Reception approval, or propose a follow-up/procedure to a patient already seen in a completed consultation.
- For a confirmed future appointment: submit a cancellation request or send a rescheduling notice to the patient.
- After the appointment's scheduled end: mark **Completed** or **No-show**. Medical details are limited to patients assigned to that doctor.

### Receptionist

- Open **Schedule → Review Requests** for pending consultations, time-off requests, cancellation requests, and reschedule requests.
- Approve a valid request, or reject with **both** a dropdown reason and written explanation.
- Reschedule approval moves the appointment and releases the original slot.

### Administrator

- Open **Doctors → Add / remove doctors** to add or permanently remove doctors.
- Doctor removal is blocked while appointments or proposals are active. Historical appointment records and the former doctor's name are retained.
- The old **Admins directory** and administrator request-review privileges are removed.

### Shared

- Role-specific dashboards, five-link authenticated sidebar, doctors list, services list, about page, notification popover and full notifications screen, account information, and informational help assistant.
- One patient per doctor per slot. Eight slots are four 30-minute consultations/follow-ups and four 60-minute procedure/treatment visits, with transition breaks and lunch.

## Project structure

```text
CLYVR_60_Revised/
  src/clyvr/controller/AppController.java      Application navigation and UI coordination
  src/clyvr/model/Entities.java               Data classes / statuses
  src/clyvr/model/ClinicStore.java            In-memory session state and workflow rules
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

## Visual and functional checks performed

- Java 21 compilation with no build tool: **passed**.
- `SmokeTest`: **passed**.
- `WorkflowTest`: **passed**, including overlap protection, receptionist approval, real rescheduling, doctor proposals, patient alternate-time approval, time-off conflicts, doctor removal and retained history.
- `UiInteractionTest` run under Xvfb: **passed**. It clicks through login → Schedule → a calendar day → consultation slot → booking form → request submission → receptionist review and approval.
- `UiCapture` screenshots: **captured and inspected** for login, signup, each role's dashboard, schedule, timeslots, booking, request queue, approvals, doctor management, and narrower **1080 × 720** layouts. These files are in `previews/`.

### Re-running tests (Linux with Xvfb)

```bash
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name '*.java')
javac -encoding UTF-8 -cp out -d out tests/*.java
java -cp out SmokeTest
java -cp out WorkflowTest
xvfb-run -a -s '-screen 0 1440x900x24' java -cp out NavigationRegressionTest
xvfb-run -a -s '-screen 0 1440x900x24' java -cp out UiInteractionTest
xvfb-run -a -s '-screen 0 1440x900x24' java -cp out UiCapture
```

The app was **not** visually tested on your specific Windows PC, DPI scaling, or fonts. The layout uses Swing layout managers instead of manual absolute positioning to reduce platform-specific defects.

## Data lifetime and future MySQL work

The files `data/users.txt`, `data/doctors.txt` and `data/appointments.txt` are read on startup as **text-file fixtures**. The application operates on its own mutable in-memory copies. Buttons update screens during the session; everything resets when you restart. Changes do **not** rewrite the source text files.

This is deliberate for the 60% deliverable. For production, the in-memory `ClinicStore` needs to be refactored into transactional repository/DAO operations backed by MySQL or another online database. Text files should **not** be treated as a concurrent multi-user database.

## Explicitly out of scope at 60%

- MySQL/Firebase integration, multi-PC synchronization, encrypted accounts, full audit/security controls.
- Sending email/SMS, external AI/LLM connection, clinical record management, medical decision-making.
- Automatic national/local holiday calendar; currently Saturday and Sunday are disabled by default.
- Professionally deployed healthcare use. This is a local academic demonstration.
#   C L Y V R - R e p o s i t o r y  
 