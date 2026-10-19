# LPMS Mobile: how the system works

LPMS (Literacy Programme Management System) is an Android app for READ Educational Trust. READ supports under-resourced
schools for three years: it trains teachers, delivers a box library that is upgraded each year, and sends field staff to
monitor and mentor. The app helps the people involved record sessions, attendance and reading progress, and talk to each other.

This page is for the team. The end-user version is `USER_GUIDE.md`; the Firebase set-up is `FIREBASE_SETUP.md`.

## 1. The layers (and why there are so many)

```
  Phone                                                       Google (Firebase)
  ┌────────────────────────────────────────────────────┐      ┌──────────────────────────┐
  │ Screens (Jetpack Compose)       ui/                │      │ Authentication           │
  │      │ show state, send taps                       │      │  email/password, Google  │
  │ View models                     ui/**/…ViewModel   │      │                          │
  │      │ ask for data, apply rules                   │      │ Cloud Firestore          │
  │ Repositories (interfaces)       data/repository    │◄────►│  the database            │
  │      │ Firebase versions        data/firebase      │      │                          │
  │ Core rules (plain Kotlin)       core/              │      │ Security rules           │
  └────────────────────────────────────────────────────┘      │  firestore.rules         │
                                                              └──────────────────────────┘
```

Each layer has one job, which is what lets us test the important logic without a phone:

| Layer | Job | Tested by |
|---|---|---|
| `core/` | The business rules: grades, attendance %, session lifecycle, privacy scoping, messaging rules, programme cycle, report maths | `app/src/test` (130 automated checks) |
| `data/repository` | The list of things the app can ask for or save (interfaces) and an in-memory version for tests | same tests |
| `data/firebase` | The real versions: Firebase Auth and Cloud Firestore, with queries filtered by who is asking | manual tests on the phone |
| `ui/` | Screens and view models; no business rules of their own, they call `core/` | manual tests on the phone |
| `firestore.rules` | The server's own copy of the privacy rules, so a modified app still cannot break them | Rules Playground |

If you only remember one thing: **rules live in `core/` and in `firestore.rules`; screens just display them.**

## 2. The people (roles)

| Role | How they get an account | What they do |
|---|---|---|
| **Administrator** | Created by you in the Firebase console (set `role` to ADMIN and `approved` to true) | Approve and edit accounts, set roles, schools and grades, suspend, see the activity log, load demo data |
| **Coordinator** (field staff) | Added by an administrator | Schools, enrol learners, link parents, schedule and close sessions, log school visits, reports |
| **Teacher** | Signs up themselves (email or Google); an admin or coordinator approves | Their class, mark attendance, record reading levels, schedule sessions at their school, message parents |
| **Parent / Guardian** | Signs up themselves; approved, then linked to their child by a coordinator | See their child's attendance and reading level, message teachers and coordinators |
| **Learner** | Optional "assisted" login added by an administrator; most learners are only records | See their own attendance and progress |

Nobody becomes a coordinator or administrator by signing up: the sign-up screen only offers Teacher and Parent, and the
security rules refuse anything else.

## 3. How the data fits together

```
School ──< Programme
   │
   ├──< Learner ──< guardianIds (parent accounts)      userId (optional learner login)
   │        │
   │        ├──< Attendance   (one per learner per session)
   │        └──< Progress     (reading levels over time)
   │
   ├──< Session (programme, date, facilitator, status)
   ├──< Teacher account (schoolId + grade)
   └──< Visit (field staff notes)

Message (two people)      Feedback (author)      Resource      Report      Audit log (admin actions)
```

Key points:

- **A person's school is stored as an id** (`schoolId`), so renaming a school cannot break links.
- **Attendance and progress records carry the learner's `schoolId` and `viewerIds`** (parents and the learner login). This is
  what lets the security rules limit who can read each record.
- **Grade R is stored as 0** and shown as "Grade R".

## 4. How does a teacher get their learners?

There is no manual "assign learner to teacher" step. A teacher's class is **worked out** from two facts:

1. The **teacher's account** has a school and a grade (for example *Soshanguve Primary, Grade 3*).
2. The **learner's record** has a school and a grade, set by a coordinator when they enrol the learner.

A teacher's class is every learner at **the same school in the same grade**. So:

- To put a learner in a teacher's class, a coordinator enrols the learner with that school and grade (Learners > Enrol learner).
- To move a teacher, an administrator edits the teacher (User Management > Edit) and changes their school or grade. Their class
  and the sessions they see change straight away.
- A teacher whose account has **no school** sees no learners and no sessions, and the app says so. This is what happens to
  someone who signs in with Google without choosing a school: the admin fixes it with **Edit**.

Limits today: one grade per teacher, and several teachers at the same school and grade share the same class.

## 5. Sessions and attendance

```
 Schedule ──► SCHEDULED ──► Teacher marks attendance (as often as needed)
                  │
                  ├──► Close session ──► COMPLETED   (register locked)
                  └──► Cancel session ─► CANCELLED   (no attendance possible)

 Staff can reopen a completed or cancelled session, and can correct a completed register.
```

| Who | Can |
|---|---|
| Coordinator / admin | Schedule a session at any school, close, cancel, reopen; correct a completed register |
| Teacher | Schedule at their own school; mark attendance, close or cancel their own school's scheduled sessions |
| Parent / learner | Only see results |

Rules the app enforces (in `core/SessionRules.kt`, and again in `firestore.rules` for the register):

- A new session needs a school, a programme and a date between 30 days ago and a year ahead.
- The same programme cannot be scheduled twice at the same school on the same day (cancelled ones do not count).
- A teacher can only change attendance while the session is **Scheduled**.

**Marking attendance (teacher):** Mark Attendance > choose the session (only your school's) > set each learner Present or
Absent > **Save attendance** > **Close session** when the register is final. Saving records who saved it and when. Everyone
starts as Present, and unsaved changes are kept if you switch sessions.

**How the numbers are worked out** (one place, `core/LearnerInsights.kt`): a learner's attendance % is *present records ÷
all records*, rounded to the nearest whole number. Every screen uses it, so the same learner shows the same number everywhere.

## 6. Messaging

- A parent can message the **teachers at their child's school** and the **coordinators and administrators**.
- A teacher can message coordinators, administrators, colleagues at their school and the **parents of their learners**.
- Coordinators and administrators can message anyone with an approved account.
- Learners do not message.
- Only the two people in a conversation can read it. Administrators cannot read other people's messages.
- To let parents find teachers without reading anyone's profile, the app keeps a **staff directory** (names only, no emails)
  in the `directory` collection. It is kept in step automatically when someone is approved, edited or suspended; **Load demo data**
  repairs it for accounts made earlier.

## 7. Privacy (POPIA)

Children's records are personal information. They are visible only to:

- staff (administrators and coordinators),
- the teacher at that learner's school,
- the learner's own login and the parents linked to that learner.

Feedback is private to its author and staff. School visits are staff-only. The activity log is admin-only. The app asks for
data with matching filters, and `firestore.rules` refuses anything else even from a modified app.

## 8. Working offline

Many READ schools have poor signal. The app shows an offline banner and keeps working. Anything you save while offline is
kept on the phone and uploaded when the connection returns (the screen says "saved on this phone"). Registering and signing
in for the first time still need a connection.

## 9. Where to find things in the code

```
app/src/main/java/com/pointblank/lpms/
  core/model          the data classes (User, Learner, Session, Message ...)
  core/util           the rules: Grades, LearnerInsights, SessionRules, Messaging, Scoping, ProgrammeCycle, ReportMetrics
  data/repository     what the app can ask for (interfaces) + in-memory versions for tests
  data/firebase       Firebase Auth and Firestore versions, and how objects are stored
  data/seed           the demo data and the "Load demo data" button's logic
  ui/auth             sign in and register
  ui/home             dashboards and the menu
  ui/screens          schools, learners, sessions, reports, user management and the other screens
  ui/teacher          My Learners and Mark Attendance
  ui/messages         inbox and conversations
firestore.rules       the server's privacy rules
```

**Adding a feature, in order:** (1) the rule goes in `core/` with a unit test; (2) add the data to the repository interface,
the in-memory version and the Firestore version; (3) add the screen and view model; (4) update `firestore.rules`;
(5) add a row to `LPMS_Test_Plan.xlsx`.

## 10. Known limits and ideas

- One grade per teacher; there is no explicit timetable.
- Reading levels are a single 1 to 5 scale, not tied to a language or an assessment rubric.
- Messages are text only, with no push notifications.
- The interface is English only.
- Report records are snapshots of when they were saved, but the figures on the Reports screen are always live.
