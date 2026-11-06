# CLYVR authenticated backend

This Java 17+ Spring Boot service owns database access and account
authentication for the Java Swing client. Login verifies the existing
`public.users.password_hash` values server-side with the Firebase-compatible
Scrypt implementation; this is password-hash compatibility, not Firebase
Authentication. Protected APIs accept opaque eight-hour bearer sessions and
resolve the active database user and role on every request.

The Swing process receives neither the database credentials nor password
hashes. Registration creates a patient user and profile in one transaction.
Doctor and staff accounts must already exist in `public.users` and have the
appropriate doctor association.

## Configuration

Set environment variables in the backend process. Do not copy Firebase service
account credentials or Supabase database credentials into the Swing client.

| Variable | Required | Purpose |
| --- | --- | --- |
| `BACKEND_PORT` | No | HTTP port; defaults to `8080`. |
| `BACKEND_BIND_ADDRESS` | No | Listen address; defaults to `127.0.0.1`. |
| `CLYVR_BACKEND_URL` | No | Swing client base URL; defaults to `http://127.0.0.1:8080`. |
| `SUPABASE_DB_URL` | Yes | Server-side PostgreSQL JDBC URL. |
| `SUPABASE_DB_USER` | Yes | Server-side database username. |
| `SUPABASE_DB_PASSWORD` | Yes | Server-side database password. |
| `CLYVR_HASH_SIGNER_KEY` | Yes for password login/registration | Base64 Scrypt signer key matching the existing stored hash parameters. |
| `CLYVR_HASH_SALT_SEPARATOR` | Yes for password login/registration | Base64 Scrypt salt separator matching the existing stored hash parameters. |
| `CLYVR_DEMO_DOCTOR_ID_D1` … `CLYVR_DEMO_DOCTOR_ID_D4` | For the current doctor directory | Server-side doctor directory aliases; these do not authenticate users. |
| `FIREBASE_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS` | Legacy token-verification endpoint only | Not used for the normal username/password login path. Keep any service-account file outside the repository. |

The supplied launch scripts keep database and hash parameters in the backend
process and remove them before starting Swing. The backend still needs a
least-privilege database role and production HTTPS/session-storage design;
do not expose a privileged database role over the network.

## Build, test, and run

From the repository root:

```powershell
.\run.ps1 backend
```

```bash
./run.sh backend
```

For backend tests and packaging:

```powershell
cd backend
mvn test
mvn package
```

`GET /health` reports service availability. Login and registration are public;
all database workflow routes require `Authorization: Bearer <session-token>`.
The session token is random and opaque, stored only as a SHA-256 fingerprint
in the backend process, and expires after eight hours. Restarting the backend
invalidates sessions. Role and database identity are queried from PostgreSQL,
not trusted from Swing request data. The local lockout is five failed attempts
per login for five minutes; it is in-memory and therefore not shared across
multiple backend instances.

Endpoints implemented:

- `POST /api/v1/auth/login` — username/email and password; returns session token, expiry, user ID, role, and display profile (never the password hash).
- `POST /api/v1/auth/register` — creates a patient account and profile; returns a registration result.
- `POST /api/v1/auth/logout` — revokes the bearer session.
- `POST /api/v1/auth/password` — changes the signed-in user's password and revokes that user's sessions.
- `GET /api/v1/doctors`
- `GET /api/v1/patient/availability?doctorKey=D1&date=YYYY-MM-DD`
- `GET /api/v1/doctor/availability?date=YYYY-MM-DD`
- `GET/POST /api/v1/doctor/time-off`
- `GET /api/v1/receptionist/time-off/pending`
- `POST /api/v1/receptionist/time-off/{timeOffId}/decision`
- `GET /api/v1/receptionist/consultations/pending`
- `POST /api/v1/patient/consultations`
- `POST /api/v1/patient/appointment-holds` — creates a five-minute hold and returns its ID and expiry.
- `POST /api/v1/patient/appointment-holds/{holdId}/confirm` — transactionally converts the caller's hold into a consultation.
- `DELETE /api/v1/patient/appointment-holds/{holdId}` — releases only the caller's hold.
- `POST /api/v1/doctor/proposals`
- `POST /api/v1/patient/appointments/{appointmentId}/proposal-decision`
- `GET /api/v1/patient/profile` and `PUT /api/v1/patient/profile`
- `GET /api/v1/appointments` and `GET /api/v1/appointments/{appointmentId}`
- `POST /api/v1/receptionist/appointments/{appointmentId}/decision`
- `PUT /api/v1/patient/appointments/{appointmentId}/status`
- `PUT /api/v1/doctor/appointments/{appointmentId}/status`
- `POST /api/v1/appointments/{appointmentId}/changes`
- `GET /api/v1/receptionist/appointment-changes/pending`
- `POST /api/v1/receptionist/appointment-changes/{requestId}/decision`
- `GET /api/v1/notifications`
- `PUT /api/v1/notifications/{notificationId}/read`
- `POST /api/v1/doctor/appointments/{appointmentId}/notify-patient`
- `POST /api/v1/auth/verify` is a separate legacy Firebase-token verification route and is not used for desktop sign-in.

Patient profile routes are scoped to the resolved patient identity. They read and
update `users` plus exactly one `patient_profiles` row in a transaction; email
is read-only and `password_hash` is never selected. A missing profile returns
404 and duplicate profiles return 409 rather than guessing which record to use.
Patient registration creates the initial profile with its user in one
transaction.

Appointment lists are scoped by the role and database identity resolved on the
backend: patients see their own records, doctors see records assigned to their
linked doctor row, and receptionists see pending consultation requests.
Appointment details apply the same ownership scope. Receptionists can change a
pending consultation to `CONFIRMED` or `REJECTED`; patients can request
cancellation for their own confirmed appointment; doctors can mark an assigned confirmed appointment
`COMPLETED` or `NO_SHOW` after its scheduled end. Each status update locks and
rechecks the current row in a transaction. The current schema has no field for a receptionist rejection explanation, so
the backend saves the `REJECTED` status without a persisted explanation. The
verified status column is unconstrained `varchar`, so the status transition is
supported; its notification is committed in the same transaction.

The doctor schedule endpoint resolves the doctor ID from the authenticated
database identity and returns slot states based on that doctor's active
appointments, approved time off, and pending reschedule holds. It is
read-only. Time-off submission and receptionist decisions use the
`time_off_requests` table. Approval rechecks appointment and pending-reschedule
conflicts while holding the same transaction advisory lock used by booking.
Time-off decisions persist their status, reviewer, and review timestamp.

Patients can submit cancellation or rescheduling requests for their own
confirmed appointments; doctors can submit cancellation requests for their
assigned confirmed appointments. The appointment-change request and any
approved appointment change are committed in one transaction. Cancellation
does not alter the appointment until approval; approved cancellation sets
`CANCELLED`, and approved rescheduling updates the appointment date and slot.
Approvals reject stale requests, conflicting slots, and replacement slots that
are no longer future clinic timeslots. Receptionists can review pending
requests; the stored request statuses remain lowercase while appointment
statuses remain uppercase.

Notifications are stored using the verified `notifications` columns. Reads and
read-status updates are scoped to the backend-resolved user. Workflow events
create notices only where supported by the existing UI and workflow; no
additional notification fields or tables are assumed.

Patient consultation booking is persisted by
`POST /api/v1/patient/consultations`. The backend resolves the patient, checks
the mapped active doctor and valid consultation date/slot, and rechecks
appointment, approved-time-off, pending-reschedule, and active-hold conflicts
under transaction-level advisory locks before inserting.

The booking screen first creates a five-minute hold. Hold creation and
confirmation use the same lexicographically ordered patient-slot and
doctor-slot advisory locks. Confirmation verifies the authenticated owner and
expiry, rechecks all booking conflicts, inserts the appointment and notifications,
and converts the hold in one transaction. A failed confirmation rolls back the
appointment and conversion, preserving a still-valid hold. Abandoned holds
expire based on `expires_at` even when their status has not yet been updated;
expired rows are marked when relevant transactions encounter them. Ordinary
booking and doctor proposals also reject a slot occupied by a live hold.

Doctors can create follow-up and procedure proposals through
`POST /api/v1/doctor/proposals` only for patients with a completed consultation
with that doctor. Proposals are stored as `appointments` rows with
`PENDING_PATIENT` status. The patient-only proposal decision endpoint approves
the proposal, optionally moving it to a valid alternative date/slot, and writes
the related notifications in the same transaction. Proposal rejection is
persisted as `DECLINED`; the patient and doctor are notified in the same
transaction. The locked patient-owned row must still be a pending
`FOLLOW_UP` or `PROCEDURE` proposal. A repeated or stale decision conflicts.
Declined and rejected rows do not occupy slots because availability and
conflict queries include only the pending and confirmed states.

Appointment creation, holds, time-off, rescheduling, proposals, and their
related notifications use transactions and transaction advisory locks for
patient/doctor slots. All CLYVR appointment writers must use this backend lock
protocol for it to serialize their writes. It does not protect against external
writers that ignore the locks; a database-enforced constraint or equivalent
shared transaction design is still required before production booking.

## Swing client contract

The desktop calls the implemented endpoints with Java's asynchronous
`HttpClient.sendAsync`; Swing controller workers wait off the EDT and update
views on the EDT. Sign-in obtains an opaque backend session; subsequent
database calls send its bearer token, logout clears it, and an unauthorized
response returns the UI to sign-in. Swing does not send development identity
headers or password hashes. Patient profiles, appointment lists/details, doctor
appointments and availability, patient cancellation/rescheduling requests,
doctor cancellation requests and appointment status updates, persisted
receptionist consultation/time-off/appointment-change decisions, doctor
time-off requests and history, notifications, patient consultation booking,
and doctor proposal creation/patient approval use backend operations.
Consultation fixture entries in the receptionist queue, legacy fixture
schedule, and administrator doctor management remain local-only. Firebase
Authentication is not used. The existing Firebase-compatible Scrypt format is
used only to verify and create database password hashes.

## Security status

Password authentication and patient registration use the backend. Existing
database hashes are not overwritten or reset; hashes not in the supported
`fbscrypt$` format produce a service-unavailable response until an approved
backward-compatible verifier is implemented. Confirm that the configured
Scrypt parameters match the existing database hashes before enabling login.
Administrator doctor management and some consultation fixture entries remain
demo-only.
The verified `appointments.status` column is `character varying NOT NULL`
with no CHECK constraints. Its current values did not yet include `REJECTED`
or `DECLINED`; both are valid application-level values that this workflow now
writes. No schema change is required. Receptionist rejection transitions only
a locked `PENDING_RECEPTIONIST` consultation to `REJECTED`. Proposal decline
transitions only a locked, patient-owned `PENDING_PATIENT` follow-up/procedure
to `DECLINED`. Both paths write notifications in the same transaction.

The backend mapper recognizes `REJECTED`, `DECLINED`, and `EXPIRED` for
compatibility with the desktop model. Availability and booking-conflict
queries reserve only `PENDING_RECEPTIONIST`, `PENDING_PATIENT`, and
`CONFIRMED`; rejected, declined, expired, cancelled, completed, and no-show
records do not hold slots. Rejection/decline terminology remains distinct:
`REJECTED` is the receptionist's consultation decision, while `DECLINED` is a
patient's proposal decision.

The verified schema has no separate time-off review explanation field, so the
receptionist's time-off decision note is included in the doctor's notification
but cannot be persisted as a review note.

Appointment/time-off concurrency relies on backend advisory
locks; external writers that do not follow that protocol can still race. The
earlier schema inspection reported no foreign keys for the relevant identity
and appointment relationships, no unique constraint on
`patient_profiles.user_id`, and no database-enforced active-slot uniqueness.
Those findings, along with the earlier report that the backend role was
privileged/BYPASSRLS, have not been independently re-verified in this
read-only source audit. The backend detects missing or duplicate patient
profiles, but the lack of a database uniqueness guarantee would still allow
concurrent external profile writers to create duplicates. Do not deploy the
local role setup until its actual privileges and policies have been checked.

## Historical Milestone 7 workflow audit (pre-session-authentication)

The inventory below records the earlier development-identity implementation.
Its statements about `X-CLYVR-DEV-IDENTITY`, local login/signup, and the lack
of appointment holds are historical and are superseded by the current
authentication and endpoint descriptions above.

The following inventory traces each persisted workflow through its HTTP route,
JDBC repository operation, and Swing screen. `DatabaseController` resolves
role/identity before calling the repository; the desktop sends only its local
development identity key.

| Workflow | API endpoint(s) | Repository operation(s) | Swing caller |
|---|---|---|---|
| Patient profile read/update | `GET/PUT /api/v1/patient/profile` | `findPatientProfile`, `updatePatientProfile` | `ProfileView` |
| Doctor directory | `GET /api/v1/doctors` | `findMappedDoctors` | `DoctorsView`, patient `ScheduleView` |
| Patient / doctor availability | `GET /api/v1/patient/availability`, `GET /api/v1/doctor/availability` | `findAvailability`, `findDoctorAvailability` | patient `ScheduleView`/`TimeslotView`; doctor `TimeslotView` |
| Patient consultation booking | `POST /api/v1/patient/consultations` | `insertConsultation` | `BookingView` |
| Appointment list/details | `GET /api/v1/appointments`, `GET /api/v1/appointments/{appointmentId}` | `findAppointments`, `findAppointment` | `AppointmentsView`, `PatientDashboardView`, `DoctorDashboardView`, `RequestQueueView`, `TimeslotView`, `PatientApprovalsView` |
| Patient cancellation request | `POST /api/v1/appointments/{appointmentId}/changes` (`kind=cancellation`); receptionist pending/decision routes | `submitAppointmentChange`, `findPendingAppointmentChanges`, `decideAppointmentChange` | `AppointmentsView`, `RequestQueueView` |
| Direct patient cancellation transition | `PUT /api/v1/patient/appointments/{appointmentId}/status` | `cancelPatientAppointment` | No active Swing screen caller; retained API/client operation |
| Receptionist consultation queue/decision | `GET /api/v1/appointments`, `POST /api/v1/receptionist/appointments/{appointmentId}/decision` | `findAppointments`, `decidePendingConsultation` | `ReceptionistDashboardView`, `RequestQueueView` |
| Receptionist appointment-change queue/decision | `GET /api/v1/receptionist/appointment-changes/pending`, `POST /api/v1/receptionist/appointment-changes/{requestId}/decision` | `findPendingAppointmentChanges`, `decideAppointmentChange` | `RequestQueueView`, `ReceptionistDashboardView` |
| Doctor appointment status | `PUT /api/v1/doctor/appointments/{appointmentId}/status` | `finishDoctorAppointment` | `AppointmentsView` |
| Doctor time-off request/history; receptionist decision | `GET/POST /api/v1/doctor/time-off`, `GET /api/v1/receptionist/time-off/pending`, `POST /api/v1/receptionist/time-off/{timeOffId}/decision` | `findDoctorTimeOff`, `submitTimeOff`, `findPendingTimeOff`, `decideTimeOff` | `TimeslotView`, `RequestQueueView`, `ReceptionistDashboardView` |
| Patient reschedule request; receptionist decision | `POST /api/v1/appointments/{appointmentId}/changes`, receptionist pending/decision routes above | `submitAppointmentChange`, `findPendingAppointmentChanges`, `decideAppointmentChange` | `RescheduleView`, `RequestQueueView` |
| Doctor proposal/patient approval | `POST /api/v1/doctor/proposals`, `POST /api/v1/patient/appointments/{appointmentId}/proposal-decision` | `createDoctorProposal`, `decideDoctorProposal` | `TimeslotView`, `PatientApprovalsView` |
| Notification retrieval/read | `GET /api/v1/notifications`, `PUT /api/v1/notifications/{notificationId}/read` | `findNotifications`, `markNotificationRead` | `NotificationsView` |
| Doctor-to-patient notification | `POST /api/v1/doctor/appointments/{appointmentId}/notify-patient` | `notifyUser` | `AppointmentsView` |

The dedicated `GET /api/v1/receptionist/consultations/pending` route and
`findPendingConsultations` repository method remain implemented but are not the
active queue caller: the Swing queue currently uses the receptionist-scoped
`GET /api/v1/appointments` result and filters pending consultations in
`RequestQueueView`. This is redundant API surface, not a local persistence
path. It can be removed only in a separate cleanup after confirming no
external client depends on it.

### Appointment status transition audit

| Current state | Actor/action | Result | Status contract |
|---|---|---|---|
| new patient consultation | patient booking | `PENDING_RECEPTIONIST` | Observed |
| `PENDING_RECEPTIONIST` consultation | receptionist approve | `CONFIRMED` | Observed |
| `PENDING_RECEPTIONIST` consultation | receptionist reject | `REJECTED` | Supported by verified unconstrained `varchar` column |
| `CONFIRMED` appointment | patient cancellation request approved by receptionist | `CANCELLED` | Observed |
| `CONFIRMED` appointment | assigned doctor after scheduled end | `COMPLETED` or `NO_SHOW` | Observed |
| new doctor follow-up/procedure proposal | doctor creates | `PENDING_PATIENT` | Observed |
| `PENDING_PATIENT` proposal | patient approves | `CONFIRMED` | Observed |
| `PENDING_PATIENT` proposal | owning patient declines | `DECLINED` | Supported by verified unconstrained `varchar` column |

The legacy `ClinicStore` enum also has `DECLINED` and `EXPIRED`. Both
`REJECTED` and `DECLINED` are now used by the persisted database workflows.
Appointment mapping recognizes all nine application statuses. Availability
and booking conflict SQL treats only `PENDING_RECEPTIONIST`, `PENDING_PATIENT`,
and `CONFIRMED` as slot-occupying states, so rejected and declined records
release their slots.

### Remaining desktop JDBC access

`Database.getConnection()` can open a direct JDBC connection. It is called by
the three legacy `usingConfiguredDatabase()` factories and by
`DatabaseConnectionTest`. Searches found no production Swing screen calling
those repository factories: `AppointmentRepository`,
`AvailabilityRepository`, and `DoctorRepository` remain for mocked unit tests.
`run.ps1 db-test` and `run.sh db-test` run the separate read-only
`DatabaseConnectionTest`; this explicit developer diagnostic is outside the
normal app launch, but it does connect from the desktop project directly to
PostgreSQL. Keeping it as a clearly separated development-only utility is
reasonable for now; if the requirement becomes no desktop JDBC whatsoever,
replace it with a backend health/connection-check route and retain mocked
repository tests.

Fixture account records and administrator doctor creation/removal remain
local demo features. Normal Swing login/signup now use the database session
API; Firebase Authentication is not part of this flow.

Milestone 7 verification: `mvn -f backend/pom.xml package` passed all 76
backend tests. All 51 Swing source files and 12 test-source files compiled.
`SmokeTest`, `BackendApiClientTest`, `DemoIdMappingTest`,
`DoctorScheduleSelectionTest`, `AppointmentRepositoryTest`,
`AvailabilityRepositoryTest`, `DoctorRepositoryTest`, and
`PendingConsultationRepositoryTest` passed. `WorkflowTest` fails at its
fixture-date collision. `NavigationRegressionTest` and `UiInteractionTest`
were not rerun in this milestone; previous attempts failed the sidebar
assertion and timed out, respectively. The Swing application was not launched.
`git diff --check` passed. No live database query or write was performed in
this milestone; status support used the database facts supplied for this
task.

## Read-only Supabase schema audit

[`docs/supabase-schema-audit.sql`](../docs/supabase-schema-audit.sql) is a
manual, read-only audit script for the schema, constraints, indexes, policies,
grants, and data-integrity checks used by this backend. Run it in the Supabase
SQL Editor only after reviewing the query notes in the script. It has not been
run against the database as part of this source audit; its results are needed
before approving any schema hardening.
