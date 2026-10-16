# Set up Robin

One-time setup for Darragh’s Pixel 9 Pro. Do these in order. Skip a section you have already finished.

The app package is `dev.chungjungsoo.gptmobile`. The Mac profile is `cbre-admin`. Nothing in this list belongs in git: not the keystore, not a token, not an API key.

## 1. Release signing keystore

Google Drive matches the installed app by package name and signing certificate. Make the keystore before you create the Drive client. This repo does not contain one.

1. On the computer you build from, create a keystore outside the repo. When asked for a name, your own is enough. Press Enter through the rest, then type `yes`.
```bash
keytool -genkeypair -v -keystore "$HOME/robin-release.jks" -alias robin -keyalg RSA -keysize 2048 -validity 10000
```
2. Use the same password for the store and the key. The release workflow has one password secret.
3. Copy the SHA-1. Ignore SHA-256. You need this for Drive.
```bash
keytool -list -v -alias robin -keystore "$HOME/robin-release.jks"
```
4. On GitHub, open `darraghloughnane89-ui/gpt_mobile` → Settings → Secrets and variables → Actions. Add three repository secrets:
   - `APP_KEYSTORE`: the keystore as one line of base64
   - `KEY_ALIAS`: `robin`
   - `KEY_PASSWORD`: the password from step 2
```bash
# Mac
base64 -i "$HOME/robin-release.jks" | tr -d '\n'
# Linux
base64 -w0 "$HOME/robin-release.jks"
```
5. Keep a copy of `robin-release.jks` and the password somewhere you can recover. A lost keystore cannot be recreated. Do not commit the file.
6. A release APK or AAB is signed in GitHub Actions (`Generate Release Version`) with those secrets. Install that signed build on the Pixel when you want Drive to match the release certificate.
7. A debug install uses a different certificate. If you also sideload debug builds, copy that SHA-1 too. Each computer has its own debug keystore.
```bash
keytool -list -v -alias androiddebugkey -keystore "$HOME/.android/debug.keystore" -storepass android -keypass android
```

## 2. Google Drive backup

Drive backup needs an Android OAuth client in Google Cloud. The app does not contain a client id or a client secret. Until the client matches the build on the phone, backup says it is not set up. Export JSON and Import JSON still work.

1. Open [Google Cloud Console](https://console.cloud.google.com/) with the Google account that will own the project.
2. Create a project named `GPT Mobile` and select it.
3. APIs & Services → Library. Enable **Google Drive API**. Do not enable a different Drive product instead.
4. APIs & Services → OAuth consent screen (or Google Auth platform → Branding). User type **External**. App name `GPT Mobile`. Support email and developer contact: your Google account. Leave logo, home page, privacy policy, terms, and authorized domains empty unless the form will not save.
5. Add the scope `https://www.googleapis.com/auth/drive.appdata` only. That scope is the app’s hidden Drive folder. Do not add full Drive.
6. Add the Google account on the Pixel as a test user. Leave the app in **Testing**. Do not publish it. A missing test user fails sign-in.
7. APIs & Services → Credentials → Create credentials → OAuth client ID. Application type **Android**. Name `GPT Mobile release`. Package name `dev.chungjungsoo.gptmobile`. SHA-1: the release fingerprint from section 1. Create it.
8. Create a second Android client named `GPT Mobile debug` with the same package name and the debug SHA-1, if you install debug builds.
9. Do not create a Web client. Do not download a client-secret JSON. Do not put a client id in the app.
10. On the Pixel: Settings → Backup → Back up to Drive. Unlock the vault, then sign in with the test user and accept the consent screen. The app uploads `gptmobile-backup.bin` into the hidden app-data folder.
11. A debug build only matches the debug client. The signed release build only matches the release client. A mismatch looks the same as “not set up”.
12. The backup key stays on that phone. Another phone cannot decrypt the Drive file. Use Export JSON, copy the file, then Import JSON. The JSON leaves out API tokens, vault secrets, and local attachments.

## 3. Obsidian vault

GitHub is the sync. The phone, the Mac, and Hermes share one repository. This does not use Obsidian Sync.

1. On GitHub, open `darraghloughnane89-ui/knowledge-vault`. If it is missing, create a private repository with that name. Branch `main`.
2. Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token.
3. Resource owner `darraghloughnane89-ui`. Repository access: only `knowledge-vault`.
4. Permissions: Contents **Read and write**, Metadata **Read**. Do not grant Administration. Do not use a token that can force-push.
5. On the Pixel: Settings → Robin vault. Leave owner `darraghloughnane89-ui`, repository `knowledge-vault`, and branch `main`. That is `darraghloughnane89-ui/knowledge-vault`.
6. Paste the token and tap Save and sync. The token is stored in the Android Keystore (`obsidian_github_token`). It is not written to git or to DataStore.
7. On the Mac, clone the same repository and open it as an Obsidian vault. Hermes should read and write the same `Robin/` tree.
8. Notes land under `Robin/` (Chats, Memory, Skills, Bots, Projects). Sync runs about every 6 hours, and from Sync now.
9. A folder already on the phone is optional. Settings can point at that folder. Switch back with Use GitHub instead.

## 4. Hermes on the Mac

The phone talks to the Hermes API server over Tailscale. Do not publish port 8642 on the router. The key is a full agent credential. `127.0.0.1` on the Pixel is the phone, not the Mac.

1. Install Tailscale on the Mac and on the Pixel. Sign both into the same tailnet.
2. On the Mac, run this once. It writes the `cbre-admin` profile and starts that gateway. If `.env` already has `API_SERVER_KEY`, skip the script and copy the key that is already there. Do not commit the key.
```bash
install -d ~/.hermes/profiles/cbre-admin
umask 077
cat >> ~/.hermes/profiles/cbre-admin/.env <<EOF
API_SERVER_ENABLED=true
API_SERVER_HOST=$(tailscale ip -4)
API_SERVER_PORT=8642
API_SERVER_KEY=$(openssl rand -hex 32)
API_SERVER_MODEL_NAME=cbre-admin
EOF
hermes -p cbre-admin gateway install
hermes -p cbre-admin gateway restart
```
3. The key must be at least 16 characters. `openssl rand -hex 32` already is. Leave `direct_model_requests` unset.
4. On the Mac, check health and models. `/v1/models` should list `cbre-admin`. The log should show the Tailscale address, not only `127.0.0.1`.
```bash
curl -sS "http://127.0.0.1:8642/health"
curl -sS -H "Authorization: Bearer $API_SERVER_KEY" "http://127.0.0.1:8642/v1/models"
```
5. On the Pixel: Settings → Hermes. Endpoint `http://<tailscale-ipv4>:8642` with no `/v1` suffix. Paste the API key. Profile `cbre-admin`. Tap Test connection. That stores the key and checks the Mac.
6. Turn on **Show Hermes as a chat model** if you want Hermes in the model list.
7. If the Mac is unreachable, Telegram on the Mac is the fallback (`hermes gateway setup`, then `TELEGRAM_BOT_TOKEN` and `TELEGRAM_ALLOWED_USERS`). Robin does not send that message for you.

## 5. Plugins

Settings → Plugins. Official rows are filled in. OAuth rows open the browser when you tap Enable. Bearer rows need a token in the field first. Leave read tools on. Turn a write tool on only if you want it. Chat asks before a write runs.

Do not point a plugin at `127.0.0.1` unless the server is actually on the phone.

1. Gmail. In the same Cloud project, enable `gmail.googleapis.com` and `gmailmcp.googleapis.com`. Settings → Plugins → Gmail → Enable. Finish the browser consent. Tap Refresh tools if the list stays empty.
2. Google Calendar. Enable `calendarmcp.googleapis.com`. Enable the Calendar row. URL `https://calendarmcp.googleapis.com/mcp/v1`.
3. Google Drive files (this is the plugin, not the backup in section 2). Enable `drive.googleapis.com` and `drivemcp.googleapis.com`. Enable the Drive row. URL `https://drivemcp.googleapis.com/mcp/v1`.
4. GitHub. Leave the token blank and tap Enable for OAuth, or paste a personal access token and tap Enable. The token is sent as `Authorization: Bearer` to `https://api.githubcopilot.com/mcp/`.
5. Notion. Tap Enable, finish OAuth, and pick the workspace. On a Business or Enterprise plan you can paste an MCP token into Bearer token before Enable.
6. Smartsheet. In Smartsheet, create an API token. Paste it without the word Bearer. Tap Enable, then Refresh tools. For EU or AU, add a custom server with `https://mcp.smartsheet.eu` or `https://mcp.smartsheet.au` and that region’s token.
7. Vercel. Confirm the URL is `https://mcp.vercel.com`. Tap Enable and grant the teams you want.
8. Cloudflare. Tap Enable and finish OAuth, or paste an API token first. Account tokens need Account Resources: Read. Tokens locked to a client IP will not work.
9. Slack and Box are not finished in this app. Both want a client secret, and Robin does not store one. Do not treat Enable on those rows as done.
10. checkit, FMFlow, Gridwell, and Loughnane Lifts stay empty until that app serves MCP. Paste the real URL, an optional bearer token, then Enable. Enable stays off while the URL is blank.
11. Hermes is not a plugin. Section 4 is how the phone reaches it.

## 6. Phone control

Robin can drive apps you allow. It will not tap a banking or finance app, even if you list it. It will not type a password into the model, the log, a screenshot, or memory.

1. Install the app and open Settings → Phone control.
2. Android Settings → Accessibility → Installed apps → the service labelled with the app name. Turn it on. Confirm the system warning.
3. Android Settings → Notifications → Notification access. Allow this app. A reply still asks before a message is sent.
4. Android Settings → Passwords and accounts → Autofill service. Choose this app only if you want Robin vault fills. Leave Google Password Manager selected if you want Robin to request a fill from Google or 1Password.
5. On the allowlist screen, enable each app Robin may touch. Leave finance apps off the list. Listing one does not make Robin tap it.
6. Optional: approve a background routine on the phone control screen. Without that, a run stops when you leave.
7. While a run is active, a bar at the top says **Robin is controlling** and has **Stop**.

## 7. Import the CBRE project

The project zip is separate from the built-in CBRE Field Reports bot. Import adds the project, its files, and its chats. Importing the same zip again updates that project. Chats you added on the phone stay.

1. Copy the CBRE project zip onto the Pixel (USB, Nearby Share, or Drive). A Robin zip has `manifest.json` with `"format": "robin-project"`, plus `instructions.md`, `files/`, and `chats/`. A ChatGPT, Claude, or Grok export of the same work imports from the same button.
2. Open Projects.
3. Tap Import, then Import zip.
4. Pick the zip. Wait until the app reports how many projects and chats came in.
5. Open the project and one chat. Send a new message if you want to confirm it continues.
6. Files over 8 MB are skipped. An archive that is not a Robin, ChatGPT, Claude, or Grok export is refused.
