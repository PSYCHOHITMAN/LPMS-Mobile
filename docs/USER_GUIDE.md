# LPMS user guide

LPMS helps READ Educational Trust record sessions, attendance and reading progress, and lets parents, teachers and
coordinators talk to each other. Find your role below.

## Signing up and signing in

1. Tap **Register here**.
2. Choose **Teacher** or **Parent / Guardian**.
3. Fill in your name, email and a password (at least 8 characters, with a letter and a number).
4. Pick your **school**. Teachers also pick the **grade** they teach (R, 1, 2 ...). Parents type their **child's name**.
5. Tap **Register**, or **Sign up with Google instead**.
6. An administrator or coordinator must **approve** your account. You will see a message on the sign-in screen. When you are approved, sign in.

If you signed in with Google and see no learners or sessions, your account may not have a school yet. Ask an administrator to set it.

Forgotten something? **Settings** lets you change your name and password. **Help** has the short answers.
If you have no signal, keep working: what you save is kept on the phone and uploads later.

---

## Parents and guardians

- **Dashboard:** a card for each child with attendance and reading level.
- **My Children:** reading progress and the last five sessions for each child.
- **Messages:** write to your child's teacher or the coordinator. Tap **New message**, choose who, and send. A red number shows unread messages.
- **No children showing?** A coordinator links your account to your child after approving you. Ask them to check the name you registered.
- **Feedback:** tell READ how the programme is going. Only you and READ staff can see your feedback.

## Teachers

- **My Learners:** your class (learners at your school in your grade) with reading level, attendance and status. Learners are enrolled by your coordinator, not by you.
- **Sessions:** your school's sessions. Tap **Schedule session** to plan one: choose the programme, pick a date, and it appears for your school.
- **Mark Attendance:**
  1. Choose the session.
  2. Set each learner to **Present** or **Absent**.
  3. Tap **Save attendance**. It records your name and the time.
  4. When the register is final, tap **Close session**. The register is locked.
- **Progress > Record progress:** pick a learner, a programme and a reading level (1 to 5).
- **Messages:** reply to parents or start a conversation with a parent of one of your learners.
- A closed session cannot be changed by teachers. Ask a coordinator to reopen it if you need a correction.

## Coordinators (field staff)

- **Accounts:** approve new teachers and parents. Check the school and grade, or the child's name, first.
- **Learners:** **Enrol learner** (name, school, grade). **Link account** connects a parent (or a learner login) to a child so they can see that child's records.
- **Sessions:** schedule at any school, choose the facilitator, **Close**, **Cancel** or **Reopen** a session.
- **Schools:** see each school's programme year (Year x of 3) and box-library level. **Upgrade library** when it is due.
- **School Visits > Log visit:** record who you observed, what you saw and any follow-up.
- **Reports:** reach, attendance and reading-level gain, and **Export CSV** to share with funders.
- **Messages:** write to any parent or teacher.

## Administrators

Everything coordinators can do, plus:

- **User Management:** **Add user** (for coordinators and administrators), **Edit** to set someone's role, school and grade, the switch to suspend or restore an account, and **Approve**.
- **Activity Log:** who did what and when.
- **Load demo data:** loads sample schools, learners, sessions and demo accounts (password `password123`). Safe to run again.

### Setting up the first administrator
See `FIREBASE_SETUP.md`: register in the app, then in the Firebase console set your `role` to `ADMIN` and `approved` to `true`.

---

## Reading levels

| Level | Meaning |
|---|---|
| 1 or 2 | Needs support |
| 3 | On track |
| 4 or 5 | Excelling |

## Troubleshooting

| Problem | What to try |
|---|---|
| "Needs approval" when signing in | Wait for an administrator or coordinator to approve you. |
| I see no learners | Your account needs a school (and a grade for teachers). Ask an administrator to **Edit** your account. |
| I see no sessions | Sessions are shown for your school. Schedule one, or ask a coordinator. |
| "This session is completed" | The register is locked. A coordinator can reopen it. |
| I cannot find a teacher to message | You can message teachers at your child's school. Make sure your child is linked and the teacher has been approved. |
| Nothing loads | Check your internet connection and open the screen again. |
