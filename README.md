# LPMS Mobile

Android companion app for the Literacy Programme Management System (READ Educational Trust).
Kotlin, Jetpack Compose, Material 3. XISD6329 Task 2, Team Point Blank.

## Getting started

1. Follow **FIREBASE_SETUP.md** first (Firebase project, `app/google-services.json`, Firestore rules). The app will not build without that file.
2. Open the folder in Android Studio (Ladybug or newer) and let Gradle sync.
3. Run on a physical Android phone (the brief does not allow an emulator for the demo).

## Documentation

- `docs/SYSTEM_OVERVIEW.md`: how the system works (layers, roles, data, sessions and attendance, messaging, privacy). Start here.
- `docs/USER_GUIDE.md`: what each role does in the app.
- `FIREBASE_SETUP.md`: set-up and upgrading an existing database.
- `LPMS_Test_Plan.xlsx`: tests to run on a physical phone.

## What the app does

- **Roles:** admin, coordinator (field staff), teacher, parent / guardian, and learner (assisted login). Teachers and parents sign themselves up and an admin or coordinator approves them. Learners are enrolled by staff.
- **Teachers:** My Learners, schedule sessions, Mark Attendance (records who saved it and when) and close the register, Record progress, message parents.
- **Coordinators:** schedule, close, cancel and reopen sessions; Schools with READ's three-year cycle and box-library upgrades; enrol learners and link parents; log school visits; funder report with CSV export.
- **Admins:** user management (approve, edit role, school and grade, suspend), activity log.
- **Parents:** dashboard, My Children (attendance, reading level, progress) and messages to teachers and coordinators.
- **Privacy:** children's records are only visible to staff, the teacher at that school, and linked parents (queries and `firestore.rules`).
- **Offline:** an offline banner, and saves made without signal are kept on the phone and uploaded later.

## Structure

```
app/src/main/java/com/pointblank/lpms/
  core/model        Entities
  core/util         Validators, Grades (Grade R = 0), LearnerInsights, ProgrammeCycle, ReportMetrics, Scoping
  data/repository   AuthRepository + LpmsRepository interfaces, in-memory versions (used by unit tests)
  data/firebase     FirebaseAuthRepository (Auth + users/{uid}) and FirestoreLpmsRepository (scoped queries)
  data/net          NetworkMonitor (offline banner)
  data/seed         Demo data set + DemoDataSeeder (admin "Load demo data")
  ui/               Compose screens and view models
firestore.rules     Security rules to publish in the Firebase console
```

The UI only talks to the two repository interfaces. `AppContainer.kt` picks the Firebase implementations; the unit tests use the in-memory ones.

## Tests

`app/src/test` holds the unit tests (privacy scoping, approval, Grade R, programme cycle, report figures, demo seeder).
`LPMS_Test_Plan.xlsx` lists the manual tests to run on a physical phone.

## CI

`.github/workflows/android-ci.yml` writes `google-services.json` from the `GOOGLE_SERVICES_JSON` secret, runs unit tests and builds a debug APK on every push and pull request to `main` or `develop`.
