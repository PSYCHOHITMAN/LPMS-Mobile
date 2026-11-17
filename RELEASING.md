# Publishing LPMS to Google Play

Package: `com.pointblank.lpms` · Version: 1.0.0 (versionCode 2). Raise `versionCode` for every upload.

## 1. Create the upload key (once, keep it safe, never commit it)
```
keytool -genkeypair -v -keystore lpms-release.jks -alias lpms -keyalg RSA -keysize 2048 -validity 10000
```
Write the passwords down. If you lose this file you cannot update the app under the same key.

## 2. Add the release SHA-1 to Firebase (Google sign-in breaks without this)
```
keytool -list -v -keystore lpms-release.jks -alias lpms
```
Firebase console > Project settings > your Android app > **Add fingerprint** (SHA-1 and SHA-256).
After Play App Signing is on, also add the **App signing key** SHA-1 from Play Console > Setup > App signing.
Download the new `google-services.json` into `app/`.

## 3. Build the signed bundle
Locally: create `keystore.properties` in the project root (git-ignored):
```
storeFile=lpms-release.jks
storePassword=...
keyAlias=lpms
keyPassword=...
```
then `./gradlew bundleRelease assembleRelease`.
Outputs: `app/build/outputs/bundle/release/app-release.aab` (upload this) and `.../apk/release/app-release.apk` (screenshot/evidence).

Or with GitHub Actions: add repo secrets `GOOGLE_SERVICES_JSON` (base64), `LPMS_KEYSTORE_BASE64` (`base64 -w0 lpms-release.jks`),
`LPMS_KEYSTORE_PASSWORD`, `LPMS_KEY_ALIAS`, `LPMS_KEY_PASSWORD`, then run **Android Release** (Actions tab) and download the `lpms-release` artifact.

## 4. Play Console (https://play.google.com/console)
1. Pay the once-off USD 25 developer fee and verify identity (can take days, do it first).
2. **Create app**: name, English, App, Free, accept declarations.
3. Dashboard tasks: Privacy policy URL (required, the app handles personal data of children and uses Firebase Auth/Firestore), App access (give a demo login, e.g. `teacher@school.org` / `password123`), Ads (none), Content rating questionnaire, Target audience (not designed for children; users are staff and parents), Data safety (email, name, user IDs, child learning records; encrypted in transit; deletion on request), Government apps (no).
4. **Store listing**: short description (80 chars), full description, 512x512 icon, 1024x500 feature graphic, 2+ phone screenshots.
5. **Testing > Internal testing** (or Closed testing) > Create release > upload the `.aab` > paste release notes > Save > Review > Roll out. Add your tester emails. Internal testing is available within minutes and gives you the opt-in link for your evidence.
6. Production needs review (a few days). Personal accounts created after Nov 2023 must run a closed test with 12+ testers for 14 days before Production access.

## 5. Evidence for the portfolio
Signed APK/AAB, Play Console screenshot of the release (Internal/Closed test or Production), store listing screenshots, link to the video and README.
