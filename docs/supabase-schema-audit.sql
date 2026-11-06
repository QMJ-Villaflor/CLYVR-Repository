-- CLYVR schema audit: read-only SELECT statements for manual execution.
-- Run catalog queries first. If they show that a referenced table or column
-- does not exist, adjust or skip the dependent data query rather than guessing.
-- No statement in this file changes database state.

-- 1. Column definitions for every table referenced by the backend repository.
-- Review data types, nullability, defaults, and identity-generated fields.
SELECT table_schema, table_name, ordinal_position, column_name, data_type,
       udt_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN (
      'users', 'doctors', 'patient_profiles', 'appointments',
      'appointment_change_requests', 'time_off_requests', 'notifications'
  )
ORDER BY table_name, ordinal_position;

-- 2. Primary, foreign, unique, and check constraints on those tables.
-- A missing row means that constraint type is not declared for that table.
SELECT n.nspname AS table_schema, c.relname AS table_name,
       con.conname AS constraint_name,
       CASE con.contype
           WHEN 'p' THEN 'PRIMARY KEY'
           WHEN 'f' THEN 'FOREIGN KEY'
           WHEN 'u' THEN 'UNIQUE'
           WHEN 'c' THEN 'CHECK'
           WHEN 'x' THEN 'EXCLUSION'
           ELSE con.contype::text
       END AS constraint_type,
       pg_get_constraintdef(con.oid, true) AS definition,
       con.convalidated AS is_validated
FROM pg_constraint con
JOIN pg_class c ON c.oid = con.conrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relname IN (
      'users', 'doctors', 'patient_profiles', 'appointments',
      'appointment_change_requests', 'time_off_requests', 'notifications'
  )
ORDER BY c.relname, constraint_type, con.conname;

-- 3. Column types on each proposed foreign-key pair.
-- same_type only reports exact equality; PostgreSQL may also support a foreign
-- key between compatible integer types. Review the displayed types and any
-- existing constraint definition before deciding whether a pair is compatible.
WITH pairs (source_table, source_column, target_table, target_column) AS (
    VALUES
      ('patient_profiles', 'user_id', 'users', 'user_id'),
      ('doctors', 'user_id', 'users', 'user_id'),
      ('appointments', 'patient_id', 'users', 'user_id'),
      ('appointments', 'doctor_id', 'doctors', 'doctor_id'),
      ('appointment_change_requests', 'appointment_id', 'appointments', 'appointment_id'),
      ('appointment_change_requests', 'requester_id', 'users', 'user_id'),
      ('appointment_change_requests', 'reviewed_by', 'users', 'user_id'),
      ('time_off_requests', 'doctor_id', 'doctors', 'doctor_id'),
      ('time_off_requests', 'reviewed_by', 'users', 'user_id'),
      ('notifications', 'recipient_id', 'users', 'user_id')
)
SELECT p.source_table, p.source_column,
       s.data_type AS source_type, s.udt_name AS source_udt,
       s.is_nullable AS source_nullable,
       p.target_table, p.target_column,
       t.data_type AS target_type, t.udt_name AS target_udt,
       t.is_nullable AS target_nullable,
       (s.data_type = t.data_type AND s.udt_name = t.udt_name) AS same_type
FROM pairs p
LEFT JOIN information_schema.columns s
  ON s.table_schema = 'public'
 AND s.table_name = p.source_table
 AND s.column_name = p.source_column
LEFT JOIN information_schema.columns t
  ON t.table_schema = 'public'
 AND t.table_name = p.target_table
 AND t.column_name = p.target_column
ORDER BY p.source_table, p.source_column;

-- 4. Index definitions for the backend tables.
-- Primary/unique indexes often already satisfy lookup needs; compare before
-- proposing another index with the same leading columns and predicate.
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN (
      'users', 'doctors', 'patient_profiles', 'appointments',
      'appointment_change_requests', 'time_off_requests', 'notifications'
  )
ORDER BY tablename, indexname;

-- 5. RLS enabled/forced state.
-- RLS is effective for ordinary roles only when enabled; FORCE also applies
-- policies to the table owner. Superusers and BYPASSRLS roles still bypass it.
SELECT n.nspname AS table_schema, c.relname AS table_name,
       c.relrowsecurity AS rls_enabled,
       c.relforcerowsecurity AS rls_forced,
       pg_get_userbyid(c.relowner) AS table_owner
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p')
  AND c.relname IN (
      'users', 'doctors', 'patient_profiles', 'appointments',
      'appointment_change_requests', 'time_off_requests', 'notifications'
  )
ORDER BY c.relname;

-- 6. Existing RLS policies.
-- No rows means no policies are defined for the listed tables.
SELECT schemaname, tablename, policyname, permissive, roles,
       cmd, qual, with_check
FROM pg_policies
WHERE schemaname = 'public'
  AND tablename IN (
      'users', 'doctors', 'patient_profiles', 'appointments',
      'appointment_change_requests', 'time_off_requests', 'notifications'
  )
ORDER BY tablename, policyname;

-- 7. Table privileges recorded for these tables, including PUBLIC grants.
-- Compare grantee and privilege_type with the least privileges the backend
-- actually needs; this does not reveal the backend credential or its password.
SELECT table_schema, table_name, grantee, privilege_type, is_grantable
FROM information_schema.table_privileges
WHERE table_schema = 'public'
  AND table_name IN (
      'users', 'doctors', 'patient_profiles', 'appointments',
      'appointment_change_requests', 'time_off_requests', 'notifications'
  )
ORDER BY table_name, grantee, privilege_type;

-- 8. Effective table privileges for the role executing this script.
-- The SQL Editor role may differ from the backend role; results do not prove
-- what privileges the backend connection has unless it uses this same role.
SELECT current_database() AS database_name,
       current_user AS current_user_name,
       current_role AS current_role_name,
       session_user AS session_user_name,
       t.table_name,
       has_table_privilege(current_user, 'public.' || quote_ident(t.table_name), 'SELECT')
           AS can_select,
       has_table_privilege(current_user, 'public.' || quote_ident(t.table_name), 'INSERT')
           AS can_insert,
       has_table_privilege(current_user, 'public.' || quote_ident(t.table_name), 'UPDATE')
           AS can_update,
       has_table_privilege(current_user, 'public.' || quote_ident(t.table_name), 'DELETE')
           AS can_delete
FROM (VALUES
      ('users'), ('doctors'), ('patient_profiles'), ('appointments'),
      ('appointment_change_requests'), ('time_off_requests'), ('notifications')
) AS t(table_name)
ORDER BY t.table_name;

-- 9. Role attributes for the SQL Editor role and visible table grantees.
-- Superuser and BYPASSRLS roles bypass row policies; compare names with the
-- backend's configured database role using a secure administrative process.
WITH relevant_grantees AS (
    SELECT DISTINCT grantee
    FROM information_schema.table_privileges
    WHERE table_schema = 'public'
      AND table_name IN (
          'users', 'doctors', 'patient_profiles', 'appointments',
          'appointment_change_requests', 'time_off_requests', 'notifications'
      )
)
SELECT r.rolname, r.rolsuper, r.rolbypassrls, r.rolcreaterole,
       r.rolcreatedb, r.rolcanlogin
FROM pg_roles r
WHERE r.rolname IN (current_user, session_user)
   OR r.rolname IN (SELECT grantee FROM relevant_grantees WHERE grantee <> 'PUBLIC')
ORDER BY r.rolname;

-- 10. Sequence privileges for the executing role.
-- Identity/serial columns may require sequence access when inserting generated IDs.
SELECT n.nspname AS sequence_schema, c.relname AS sequence_name,
       has_sequence_privilege(current_user, c.oid, 'USAGE') AS can_use,
       has_sequence_privilege(current_user, c.oid, 'SELECT') AS can_select,
       has_sequence_privilege(current_user, c.oid, 'UPDATE') AS can_update
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind = 'S'
ORDER BY c.relname;

-- 11. Duplicate patient-profile associations.
-- Any returned user_id has more than one profile; the repository requires
-- exactly one profile for a patient and currently detects duplicates at read time.
SELECT user_id, count(*) AS profile_count,
       array_agg(profile_id ORDER BY profile_id) AS profile_ids
FROM public.patient_profiles
GROUP BY user_id
HAVING count(*) > 1 OR user_id IS NULL
ORDER BY profile_count DESC, user_id;

-- 12. Duplicate doctor-to-user associations.
-- More than one doctor row for one user makes backend doctor identity resolution
-- ambiguous. Null user_id values also need review.
SELECT user_id, count(*) AS doctor_count,
       array_agg(doctor_id ORDER BY doctor_id) AS doctor_ids
FROM public.doctors
GROUP BY user_id
HAVING count(*) > 1 OR user_id IS NULL
ORDER BY doctor_count DESC, user_id;

-- 13. Duplicate usernames/emails, case-insensitively.
-- username is read from the row JSON so this query still runs if that optional
-- column is absent. The backend reads email but does not authenticate by it.
WITH user_names AS (
    SELECT user_id,
           lower(btrim(to_jsonb(u) ->> 'username')) AS normalized_value
    FROM public.users u
)
SELECT 'username' AS field_name, normalized_value, count(*) AS row_count,
       array_agg(user_id ORDER BY user_id) AS user_ids
FROM user_names
WHERE normalized_value IS NOT NULL AND normalized_value <> ''
GROUP BY normalized_value
HAVING count(*) > 1
UNION ALL
SELECT 'email' AS field_name,
       lower(btrim(to_jsonb(u) ->> 'email')) AS normalized_value,
       count(*) AS row_count,
       array_agg(u.user_id ORDER BY u.user_id) AS user_ids
FROM public.users u
WHERE to_jsonb(u) ->> 'email' IS NOT NULL
  AND btrim(to_jsonb(u) ->> 'email') <> ''
GROUP BY lower(btrim(to_jsonb(u) ->> 'email'))
HAVING count(*) > 1
ORDER BY field_name, normalized_value;

-- 14. Orphaned non-null relationship candidates and null reference counts.
-- Orphans should be investigated before adding the corresponding foreign key.
-- Null counts are not inherently errors for the optional reviewed_by references.
SELECT 'patient_profiles.user_id -> users.user_id' AS relationship,
       count(*) FILTER (WHERE p.user_id IS NOT NULL AND u.user_id IS NULL) AS orphan_count,
       count(*) FILTER (WHERE p.user_id IS NULL) AS null_reference_count
FROM public.patient_profiles p
LEFT JOIN public.users u ON u.user_id = p.user_id
UNION ALL
SELECT 'doctors.user_id -> users.user_id',
       count(*) FILTER (WHERE d.user_id IS NOT NULL AND u.user_id IS NULL),
       count(*) FILTER (WHERE d.user_id IS NULL)
FROM public.doctors d
LEFT JOIN public.users u ON u.user_id = d.user_id
UNION ALL
SELECT 'appointments.patient_id -> users.user_id',
       count(*) FILTER (WHERE a.patient_id IS NOT NULL AND u.user_id IS NULL),
       count(*) FILTER (WHERE a.patient_id IS NULL)
FROM public.appointments a
LEFT JOIN public.users u ON u.user_id = a.patient_id
UNION ALL
SELECT 'appointments.doctor_id -> doctors.doctor_id',
       count(*) FILTER (WHERE a.doctor_id IS NOT NULL AND d.doctor_id IS NULL),
       count(*) FILTER (WHERE a.doctor_id IS NULL)
FROM public.appointments a
LEFT JOIN public.doctors d ON d.doctor_id = a.doctor_id
UNION ALL
SELECT 'appointment_change_requests.appointment_id -> appointments.appointment_id',
       count(*) FILTER (WHERE r.appointment_id IS NOT NULL AND a.appointment_id IS NULL),
       count(*) FILTER (WHERE r.appointment_id IS NULL)
FROM public.appointment_change_requests r
LEFT JOIN public.appointments a ON a.appointment_id = r.appointment_id
UNION ALL
SELECT 'appointment_change_requests.requester_id -> users.user_id',
       count(*) FILTER (WHERE r.requester_id IS NOT NULL AND u.user_id IS NULL),
       count(*) FILTER (WHERE r.requester_id IS NULL)
FROM public.appointment_change_requests r
LEFT JOIN public.users u ON u.user_id = r.requester_id
UNION ALL
SELECT 'appointment_change_requests.reviewed_by -> users.user_id',
       count(*) FILTER (WHERE r.reviewed_by IS NOT NULL AND u.user_id IS NULL),
       count(*) FILTER (WHERE r.reviewed_by IS NULL)
FROM public.appointment_change_requests r
LEFT JOIN public.users u ON u.user_id = r.reviewed_by
UNION ALL
SELECT 'time_off_requests.doctor_id -> doctors.doctor_id',
       count(*) FILTER (WHERE t.doctor_id IS NOT NULL AND d.doctor_id IS NULL),
       count(*) FILTER (WHERE t.doctor_id IS NULL)
FROM public.time_off_requests t
LEFT JOIN public.doctors d ON d.doctor_id = t.doctor_id
UNION ALL
SELECT 'time_off_requests.reviewed_by -> users.user_id',
       count(*) FILTER (WHERE t.reviewed_by IS NOT NULL AND u.user_id IS NULL),
       count(*) FILTER (WHERE t.reviewed_by IS NULL)
FROM public.time_off_requests t
LEFT JOIN public.users u ON u.user_id = t.reviewed_by
UNION ALL
SELECT 'notifications.recipient_id -> users.user_id',
       count(*) FILTER (WHERE n.recipient_id IS NOT NULL AND u.user_id IS NULL),
       count(*) FILTER (WHERE n.recipient_id IS NULL)
FROM public.notifications n
LEFT JOIN public.users u ON u.user_id = n.recipient_id
ORDER BY relationship;

-- 15. Appointment references that exist but have an unexpected account role.
-- Repository joins establish that patient_id identifies users.user_id and that
-- doctor_id identifies doctors.doctor_id. Historical inactive accounts are shown,
-- not treated as broken references.
SELECT a.appointment_id, a.patient_id, p.role AS patient_role,
       a.doctor_id, d.user_id AS doctor_user_id, du.role AS doctor_role,
       p.is_active AS patient_active, du.is_active AS doctor_active
FROM public.appointments a
LEFT JOIN public.users p ON p.user_id = a.patient_id
LEFT JOIN public.doctors d ON d.doctor_id = a.doctor_id
LEFT JOIN public.users du ON du.user_id = d.user_id
WHERE p.user_id IS NULL
   OR p.role IS DISTINCT FROM 'PATIENT'
   OR d.doctor_id IS NULL
   OR du.user_id IS NULL
   OR du.role IS DISTINCT FROM 'DOCTOR'
ORDER BY a.appointment_id;

-- 16. Appointment status values and null count.
-- The application currently treats these eight values as business statuses.
-- EXPIRED is accepted by the desktop/backend mapper for compatibility but is
-- not in the supplied current database value list; any other value is unexpected.
SELECT status, count(*) AS row_count
FROM public.appointments
GROUP BY status
ORDER BY status NULLS FIRST;

SELECT count(*) FILTER (WHERE status IS NULL) AS null_status_count,
       count(*) FILTER (
           WHERE status IS NOT NULL
             AND status NOT IN (
                 'PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED',
                 'CANCELLED', 'COMPLETED', 'NO_SHOW', 'REJECTED', 'DECLINED'
             )
       ) AS status_outside_current_business_set
FROM public.appointments;

-- 17. NULL counts in fields the backend expects for identity, ownership, and
-- list/decision operations. A nonzero count should be compared with the
-- nullability results in query 1 and the business meaning of each field.
SELECT 'users.user_id' AS field_name, count(*) FILTER (WHERE user_id IS NULL) AS null_count
FROM public.users
UNION ALL
SELECT 'users.role', count(*) FILTER (WHERE role IS NULL) FROM public.users
UNION ALL
SELECT 'users.is_active', count(*) FILTER (WHERE is_active IS NULL) FROM public.users
UNION ALL
SELECT 'doctors.doctor_id', count(*) FILTER (WHERE doctor_id IS NULL) FROM public.doctors
UNION ALL
SELECT 'doctors.user_id', count(*) FILTER (WHERE user_id IS NULL) FROM public.doctors
UNION ALL
SELECT 'patient_profiles.profile_id', count(*) FILTER (WHERE profile_id IS NULL)
FROM public.patient_profiles
UNION ALL
SELECT 'patient_profiles.user_id', count(*) FILTER (WHERE user_id IS NULL)
FROM public.patient_profiles
UNION ALL
SELECT 'appointments.appointment_id', count(*) FILTER (WHERE appointment_id IS NULL)
FROM public.appointments
UNION ALL
SELECT 'appointments.patient_id', count(*) FILTER (WHERE patient_id IS NULL)
FROM public.appointments
UNION ALL
SELECT 'appointments.doctor_id', count(*) FILTER (WHERE doctor_id IS NULL)
FROM public.appointments
UNION ALL
SELECT 'appointments.appointment_type', count(*) FILTER (WHERE appointment_type IS NULL)
FROM public.appointments
UNION ALL
SELECT 'appointments.appointment_date', count(*) FILTER (WHERE appointment_date IS NULL)
FROM public.appointments
UNION ALL
SELECT 'appointments.slot_number', count(*) FILTER (WHERE slot_number IS NULL)
FROM public.appointments
UNION ALL
SELECT 'appointments.status', count(*) FILTER (WHERE status IS NULL)
FROM public.appointments
UNION ALL
SELECT 'appointment_change_requests.request_id',
       count(*) FILTER (WHERE request_id IS NULL)
FROM public.appointment_change_requests
UNION ALL
SELECT 'appointment_change_requests.appointment_id',
       count(*) FILTER (WHERE appointment_id IS NULL)
FROM public.appointment_change_requests
UNION ALL
SELECT 'appointment_change_requests.requester_id',
       count(*) FILTER (WHERE requester_id IS NULL)
FROM public.appointment_change_requests
UNION ALL
SELECT 'appointment_change_requests.kind', count(*) FILTER (WHERE kind IS NULL)
FROM public.appointment_change_requests
UNION ALL
SELECT 'appointment_change_requests.status', count(*) FILTER (WHERE status IS NULL)
FROM public.appointment_change_requests
UNION ALL
SELECT 'time_off_requests.time_off_id', count(*) FILTER (WHERE time_off_id IS NULL)
FROM public.time_off_requests
UNION ALL
SELECT 'time_off_requests.doctor_id', count(*) FILTER (WHERE doctor_id IS NULL)
FROM public.time_off_requests
UNION ALL
SELECT 'time_off_requests.request_date', count(*) FILTER (WHERE request_date IS NULL)
FROM public.time_off_requests
UNION ALL
SELECT 'time_off_requests.slot_number', count(*) FILTER (WHERE slot_number IS NULL)
FROM public.time_off_requests
UNION ALL
SELECT 'time_off_requests.status', count(*) FILTER (WHERE status IS NULL)
FROM public.time_off_requests
UNION ALL
SELECT 'notifications.notification_id', count(*) FILTER (WHERE notification_id IS NULL)
FROM public.notifications
UNION ALL
SELECT 'notifications.recipient_id', count(*) FILTER (WHERE recipient_id IS NULL)
FROM public.notifications
UNION ALL
SELECT 'notifications.message', count(*) FILTER (WHERE message IS NULL)
FROM public.notifications
UNION ALL
SELECT 'notifications.created_at', count(*) FILTER (WHERE created_at IS NULL)
FROM public.notifications
UNION ALL
SELECT 'notifications.is_read', count(*) FILTER (WHERE is_read IS NULL)
FROM public.notifications
ORDER BY field_name;

-- 18. Appointment field values the backend cannot process safely.
-- Slot range and appointment types are checked by application code; this query
-- reports rows outside that observed application contract, without proposing
-- any new appointment type or status.
SELECT appointment_id, appointment_type, appointment_date, slot_number, status
FROM public.appointments
WHERE appointment_type IS NULL
   OR appointment_type NOT IN ('CONSULTATION', 'FOLLOW_UP', 'PROCEDURE')
   OR appointment_date IS NULL
   OR slot_number IS NULL
   OR slot_number NOT BETWEEN 0 AND 7
   OR status IS NULL
ORDER BY appointment_id;

-- 19. Active appointment slot conflicts.
-- The active set is the exact set used by backend availability and booking SQL.
-- Returned groups indicate that duplicate active slots already exist.
SELECT doctor_id, appointment_date, slot_number,
       count(*) AS appointment_count,
       array_agg(appointment_id ORDER BY appointment_id) AS appointment_ids,
       array_agg(status ORDER BY appointment_id) AS statuses
FROM public.appointments
WHERE status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
GROUP BY doctor_id, appointment_date, slot_number
HAVING count(*) > 1
ORDER BY appointment_date, doctor_id, slot_number;

SELECT patient_id, appointment_date, slot_number,
       count(*) AS appointment_count,
       array_agg(appointment_id ORDER BY appointment_id) AS appointment_ids,
       array_agg(status ORDER BY appointment_id) AS statuses
FROM public.appointments
WHERE status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
GROUP BY patient_id, appointment_date, slot_number
HAVING count(*) > 1
ORDER BY appointment_date, patient_id, slot_number;

-- 20. Active appointments overlapping approved or pending time-off requests.
-- Approved overlaps indicate a current conflict. Pending overlaps are also
-- shown because backend booking ignores pending time off, while review checks it.
SELECT t.time_off_id, t.doctor_id, t.request_date, t.slot_number,
       t.status AS time_off_status,
       a.appointment_id, a.status AS appointment_status
FROM public.time_off_requests t
JOIN public.appointments a
  ON a.doctor_id = t.doctor_id
 AND a.appointment_date = t.request_date
 AND a.slot_number = t.slot_number
 AND a.status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
WHERE lower(t.status) IN ('pending', 'approved')
ORDER BY t.request_date, t.doctor_id, t.slot_number, t.time_off_id;

-- 21. Pending reschedule requests that overlap another active appointment.
-- This mirrors the backend's proposed-slot conflict check. Appointment and
-- request status literals follow the repository's observed SQL.
SELECT r.request_id, r.appointment_id AS source_appointment_id,
       r.proposed_date, r.proposed_slot, a.patient_id, a.doctor_id,
       conflict.appointment_id AS conflicting_appointment_id,
       conflict.status AS conflicting_status
FROM public.appointment_change_requests r
JOIN public.appointments a ON a.appointment_id = r.appointment_id
JOIN public.appointments conflict
  ON conflict.appointment_date = r.proposed_date
 AND conflict.slot_number = r.proposed_slot
 AND conflict.appointment_id <> r.appointment_id
 AND (conflict.doctor_id = a.doctor_id OR conflict.patient_id = a.patient_id)
 AND conflict.status IN ('PENDING_RECEPTIONIST', 'PENDING_PATIENT', 'CONFIRMED')
WHERE lower(r.kind) = 'reschedule'
  AND lower(r.status) = 'pending'
ORDER BY r.proposed_date, r.proposed_slot, r.request_id;

-- 22. Request status/type values and NULL counts.
-- Review unexpected values against the backend literals before adding checks.
SELECT 'appointment_change_requests.status' AS field_name, status AS value,
       count(*) AS row_count
FROM public.appointment_change_requests
GROUP BY status
UNION ALL
SELECT 'appointment_change_requests.kind', kind, count(*)
FROM public.appointment_change_requests
GROUP BY kind
UNION ALL
SELECT 'time_off_requests.status', status, count(*)
FROM public.time_off_requests
GROUP BY status
ORDER BY field_name, value NULLS FIRST;

-- 23. Potential duplicate pending appointment-change requests.
-- The repository prevents duplicate pending requests of the same kind for an
-- appointment through a locked application transaction; this reveals existing
-- duplicates before considering a partial unique index.
SELECT appointment_id, lower(kind) AS normalized_kind,
       count(*) AS pending_request_count,
       array_agg(request_id ORDER BY request_id) AS request_ids
FROM public.appointment_change_requests
WHERE lower(status) = 'pending'
GROUP BY appointment_id, lower(kind)
HAVING count(*) > 1
ORDER BY appointment_id, normalized_kind;

-- 24. Duplicate pending/approved time-off requests for one doctor slot.
-- Results identify potential data cleanup and an optional uniqueness rule.
SELECT doctor_id, request_date, slot_number,
       count(*) AS request_count,
       array_agg(time_off_id ORDER BY time_off_id) AS time_off_ids,
       array_agg(status ORDER BY time_off_id) AS statuses
FROM public.time_off_requests
WHERE lower(status) IN ('pending', 'approved')
GROUP BY doctor_id, request_date, slot_number
HAVING count(*) > 1
ORDER BY request_date, doctor_id, slot_number;
