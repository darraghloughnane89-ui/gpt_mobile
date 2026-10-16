# Google Drive backup setup

Drive backup needs an **Android** OAuth client in Google Cloud. Darragh creates that client. The app does not contain a client id or a client secret. Google Play services matches the installed app by package name and signing certificate SHA-1.

Until that client exists for the build you are running, Drive backup shows a not-configured message and does not upload. Export JSON and Import JSON keep working.

Package name for every client below:

`dev.chungjungsoo.gptmobile`

Scope for the consent screen:

`https://www.googleapis.com/auth/drive.appdata`

That scope only lets the app read and write files in its hidden Drive app-data folder. Do not add the full Drive scope.

## 1. Create the Google Cloud project

1. Open [Google Cloud Console](https://console.cloud.google.com/).
2. Sign in with the Google account that will own the project.
3. Click the project picker in the top bar (it shows the current project name, or "Select a project").
4. Click **New project**.
5. Project name: `GPT Mobile`.
6. Leave Organization and Location as they are, unless you were told to use a company folder.
7. Click **Create**.
8. Wait until the bell notification says the project was created.
9. Click **Select project** on that notification so the top bar shows `GPT Mobile`.

## 2. Enable the Google Drive API

1. Confirm the top bar still says `GPT Mobile`.
2. Open the navigation menu (☰) at the top left.
3. Click **APIs & Services**.
4. Click **Library**.
5. Click the search box and type `Google Drive API`.
6. Click the result named **Google Drive API** (provider: Google).
7. Click **Enable**.
8. Wait until the button changes to **Manage** or the page shows the API as enabled.

Do not enable a different Drive product in place of this one.

## 3. Configure the OAuth consent screen

The console is moving from "OAuth consent screen" to "Google Auth platform". Use whichever labels you see. The fields are the same.

1. Open the navigation menu.
2. Click **APIs & Services**.
3. Click **OAuth consent screen**. If that item is gone, click **Google Auth platform** and then **Branding**.
4. If the page asks for a user type, choose **External**, then click **Create**.
5. App name: `GPT Mobile`.
6. User support email: your Google account.
7. Leave the app logo empty.
8. Leave the app home page, privacy policy, and terms of service empty unless the form refuses to save without them.
9. Leave Authorized domains empty. An Android client does not use a redirect domain.
10. Developer contact email: your Google account.
11. Click **Save and continue** (or **Save** on the Branding page).
12. Open **Scopes**, or **Data Access** in the Auth platform.
13. Click **Add or remove scopes**.
14. Paste this into the filter box: `https://www.googleapis.com/auth/drive.appdata`
15. Check the row for that scope. The description is about the app seeing, creating, and deleting its own configuration data in Google Drive.
16. Click **Update**.
17. Click **Save and continue**.
18. Open **Test users**, or **Audience** in the Auth platform.
19. Click **Add users**.
20. Add the Google account you will use on the phone.
21. Click **Add**, then **Save**.
22. Leave the publishing status as **Testing**. Do not click **Publish app**.

While the app is in Testing, only those test users can sign in. A missing test user makes Play services fail sign-in (status 12500). The backup screen treats that as not configured. Local export still works.

## 4. Copy the debug SHA-1

The debug certificate is created on the computer that builds the debug app. This repository does not contain a keystore, and there is no fingerprint to paste from the source tree.

On the machine you use for debug installs, run:

```bash
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android
```

Copy the **SHA-1** line. It is a colon-separated hex string. Ignore SHA-256.

If `~/.android/debug.keystore` does not exist yet, install a debug build once from Android Studio, or run `./gradlew assembleDebug` on that machine, then run `keytool` again. Android creates the debug keystore the first time it signs a debug APK there.

You can also run:

```bash
./gradlew signingReport
```

Use the SHA-1 under **Variant: debug** and **Config: debug**.

A different computer has a different debug keystore. Register each computer's debug SHA-1, or share one debug keystore. Do not commit that keystore.

## 5. Copy the release SHA-1

Register the certificate that signs the APK people actually install.

Play App Signing:

1. Open [Google Play Console](https://play.google.com/console).
2. Select the GPT Mobile app.
3. In the left menu, open **Test and release**.
4. Click **App integrity**.
5. Open the **App signing** tab.
6. Under **App signing key certificate**, copy the **SHA-1 certificate fingerprint**.

That is the certificate on builds installed from Play. The upload key is a different certificate. Play re-signs with the app signing key, so the Play fingerprint is the one those installs present.

If you also sideload a release APK signed with your upload keystore, register that SHA-1 too:

```bash
keytool -list -v -alias YOUR_UPLOAD_ALIAS -keystore /path/to/upload-keystore
```

`./gradlew signingReport` prints a release SHA-1 only after `signingConfigs.release` is configured in Gradle. This project does not commit a release keystore. Do not invent a fingerprint.

## 6. Create the Android OAuth clients

Create one Android client per SHA-1. They share the package name. Google picks the client that matches the installed signature. There is nothing to copy into the app.

1. In Cloud Console, with `GPT Mobile` selected, open the navigation menu.
2. Click **APIs & Services**.
3. Click **Credentials**.
4. Click **Create credentials**.
5. Click **OAuth client ID**.
6. If the page tells you to configure the consent screen first, finish section 3, then come back.
7. Application type: **Android**.
8. Name: `GPT Mobile debug`.
9. Package name: `dev.chungjungsoo.gptmobile`.
10. SHA-1 certificate fingerprint: the debug SHA-1 from section 4.
11. Click **Create**.
12. Click **Create credentials** again.
13. Click **OAuth client ID**.
14. Application type: **Android**.
15. Name: `GPT Mobile release`.
16. Package name: `dev.chungjungsoo.gptmobile`.
17. SHA-1 certificate fingerprint: the Play app-signing SHA-1 from section 5.
18. Click **Create**.
19. If you sideload upload-key release builds, repeat the same steps with Name `GPT Mobile upload` and that third SHA-1. Package name stays `dev.chungjungsoo.gptmobile`.

Do not create a Web application client for this flow. Do not download a client-secret JSON file. Do not put a client id in `BuildConfig`, `local.properties`, or source.

The credentials list should show two (or three) OAuth clients of type Android. An Android client has no secret.

## 7. What the app does before this is finished

1. Open GPT Mobile.
2. Open Settings, then **Backup**.
3. **Export JSON** and **Import JSON** work with no Google account.
4. **Back up to Drive** asks for the vault unlock, then asks Google to authorize the `drive.appdata` scope.
5. If Play services is missing, or the Android client does not match this install, authorization fails with status 10 (`DEVELOPER_ERROR`) or 12500 (sign-in failed). The screen says Drive is not set up. Nothing is uploaded.
6. After the matching Android client exists, sign in with a test user from section 3. The first time, Google shows a consent screen. After you accept, the app uploads `gptmobile-backup.bin`.

A debug build only matches the debug client. A Play build only matches the app-signing client. A mismatch looks the same as "not configured".

## 8. What is stored

- Drive file name: `gptmobile-backup.bin`.
- Location: the hidden `appDataFolder` for this app. It does not appear in the user's My Drive list.
- Contents: AES-GCM ciphertext of the JSON archive.
- Key: 32 random bytes in the on-device secret vault, reference `drive_backup_key`. The key is never uploaded, never written into the JSON export, and never sent to a model.
- Drive backup and restore require the vault unlock. The vault session lasts 5 minutes.
- Restore of the Drive file works on the same install, because the key stays in that install's vault.
- Another phone cannot decrypt the Drive file. On the source phone use **Export JSON**, copy the file, and on the other phone use **Import JSON**.
- The JSON file includes chats, messages, platform settings, and memories. It omits API tokens, vault secret references, and local attachment files.
- Import adds chats. It does not delete what is already on the phone. A platform uid that already exists is left alone, so existing keys are not replaced. A new uid is inserted with an empty token.

Room schema version for the sync row is 17 (`drive_sync_state` only). Versions 15 and 16 are memory and usage. Migrations 1–11 are unchanged.
