# Release notes

## 1.0.0 (versionCode 2): first store release
Changes since the prototype (0.1.0):
- Firebase Authentication (email/password and Google sign-in) and Cloud Firestore replace the in-memory data.
- Role-based access: admin, coordinator, teacher, parent and learner, with an approval step for new sign-ups.
- Privacy scoping and `firestore.rules`: children's records are visible only to staff, the school's teacher and linked parents.
- Attendance registers record who saved them and when, and can be closed.
- Offline banner; saves made without signal are uploaded later.
- Funder report with CSV export, school visit log and READ's three-year programme cycle.
- Release signing, app icon, and a GitHub Actions workflow that builds the signed AAB.

Innovative features added in this version: <list yours here: e.g. learner insights, offline saves, funder CSV export>.
