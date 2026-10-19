# Firebase setup (about 15 minutes)

The app uses **Firebase Authentication** (email/password and Google sign-in) and **Cloud Firestore** (the database).
The free Spark plan is enough; no billing details are needed.

## 1. Create the project
1. Go to <https://console.firebase.google.com> > **Add project** (name it e.g. `lpms-pointblank`; Analytics can stay off).

## 2. Register the Android app
1. Project overview > **Android** icon. Package name: `com.pointblank.lpms`.
2. Add your **debug SHA-1** (needed for Google sign-in). In Android Studio open the Gradle panel >
   `app > Tasks > android > signingReport` and copy the `SHA1` of the `debug` variant.
   Every teammate who runs the app needs their own SHA-1 added (Project settings > your app > Add fingerprint).

## 3. Turn on sign-in
**Build > Authentication > Get started > Sign-in method**: enable **Email/Password** and **Google** (pick a support email).

## 4. Download google-services.json
Do this *after* steps 2 and 3 so it contains the Google client ID. Save it as `app/google-services.json`.
It is git-ignored on purpose; each teammate downloads their own copy from the same project.

## 5. Create the database and rules
1. **Build > Firestore Database > Create database**. Choose production mode and a nearby region.
2. **Rules** tab: paste the contents of `firestore.rules` and **Publish**.

## 6. Make yourself the first admin
1. Run the app and **Register** with your own email (choose Teacher or Learner).
2. The app will say the account needs approval. In the Firestore console open `users`, click your document, change `role` to `ADMIN` and set `approved` to `true` (edit the field, or add it as a boolean).
3. Sign in. You now have the Admin dashboard. From now on new sign-ups appear in User Management with an **Approve** button.
4. Tap **Load demo data**. This writes the sample schools, programmes, learners, sessions, attendance, progress, reports,
   feedback and resources, and creates the demo accounts below.

Demo accounts (password `password123`): `coordinator@read.org.za`, `teacher@school.org` (Soshanguve Primary, Grade 3),
`mpho@school.org`, `thandi@school.org`, `sizwe@school.org` (Grade 2 at Centurion Academy, where the Grade R learner is),
`parent1@email.com`, `parent2@email.com`, `parent3@email.com` (two children), `learner@email.com`.
Delete them from **Authentication > Users** once marking is finished.

## Upgrading a database made with an earlier version
1. Publish the new `firestore.rules`.
2. Sign in as your admin account and tap **Load demo data** once. It adds the new fields (school ids, programme-cycle dates,
   visits), links the demo parents to their children and fills in the school id on older accounts.
3. The staff directory that lets parents find teachers to message is built by the same button, so run it once after upgrading.
4. Old demo learner logins (`learner2` to `learner4`) can stay or be deleted; they are not used any more.
5. Real sign-ups made before the upgrade keep working; their school is matched by name until an admin runs step 2.

## 7. GitHub Actions
The workflow needs `google-services.json` as a secret, because the file is not committed.
1. Encode it: macOS/Linux `base64 -w0 app/google-services.json`; PowerShell `[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\google-services.json"))`.
2. GitHub repo > **Settings > Secrets and variables > Actions > New repository secret**: name `GOOGLE_SERVICES_JSON`, paste the output.

## For the demo video (what is stored online)
- **Authentication service:** Firebase console > Authentication > Users.
- **Database:** Firebase console > Firestore Database > Data (users, learners, attendance, ...).
- **API:** the app calls the Cloud Firestore API; show Google Cloud console > APIs & Services > Cloud Firestore API > Metrics
  to show requests arriving. Check with your lecturer that this satisfies "API".
