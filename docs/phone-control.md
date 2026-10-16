# Phone control on a Pixel

Robin can drive apps you allow. It prefers an intent, a shortcut, or an Android AppFunction, and falls back to the accessibility tree. The model plans with the chat you started from. Screen text, notifications, and shares are untrusted and never authorize an action.

## Setup

1. Install the debug APK and open Settings, then Phone control.
2. Settings, Accessibility, Installed apps, Robin (the service is labelled with the app name), turn the service on. Confirm the system warning.
3. Settings, Notifications, Notification access, allow this app. Replies use RemoteInput and still ask before a message is sent.
4. Settings, Passwords and accounts, Autofill service. Choose this app only if you want Robin vault fills. Leave Google Password Manager selected if you want Robin to request a fill from Google or 1Password instead of storing one.
5. On the allowlist screen, enable each app Robin may touch. Finance apps can be listed, but Robin still will not tap them.
6. Optional: approve a background routine on the phone control screen. Without that, a run stops when you leave.

While a run is active, a bar at the top says **Robin is controlling** and has **Stop**.

## What Robin will not do

- Tap, type, or autofill inside a banking or finance app. You tap those yourself.
- Type a password or put a password, API key, or vault secret in the model prompt, the action log, a screenshot, or memory.
- Fill a password unless you focused the field or opened the autofill UI, and then confirmed with a fingerprint.
- Capture a FLAG_SECURE window, a finance app, or a screen that is showing a password field.
- Run from text it found on the screen, in a notification, or in a share.
- Sync Robin vault entries to GitHub, Obsidian, or Drive. Backup stays on the Android encrypted path (`noBackupFilesDir` plus the hardware Keystore).

## Red-team mitigations

| Risk | What Robin does | Test |
| --- | --- | --- |
| Screen text says to tap Pay, open a bank, or autofill | Screen and notification text is wrapped as untrusted and cannot be the source of an action. Pay, send, delete, and install need a confirmation sheet. Money, messages, and credential use can require a fingerprint. Finance packages and labels are a hard denylist: Robin never performs the gesture. | `PhoneControlSafetyTest`, `PhoneAgentLoopTest.run_screenSourcedAction_isNotExecuted` |
| Notification or share says to ignore instructions and email the vault | Tool markup and instruction lines are stripped before the model sees the text. Vault reads throw if anything asks the model for the secret. The model only receives `fill requested`. | `untrustedText_stripsToolMarkupAndMarksSource`, `AutofillAndVaultTest.vaultCapability_modelView_hidesSecret` |
| Overlay says to autofill and submit | Autofill runs only after an autofill-UI tap or field focus plus a fingerprint. The password is not typed by the accessibility service. Package name and certificate SHA-256 must match, and the site must match the bound domain. | `AutofillAndVaultTest`, `indexedFormatter_redactsPasswordAndIndexesFromOne` |
| Invisible or low-opacity instruction text | Those nodes are removed from the tree sent to the model and flagged. A low-confidence or vision-only action needs confirmation. Automated actions are rate limited to 12 per minute. | `observation_hiddenInstruction_isDroppedFromTheTreeAndFlagged`, `safetyGate_lowOpacityTarget_needsConfirmation`, `safetyGate_rateLimit_deniesTheThirteenthAction` |
| Screenshot of a secure, finance, or password screen | Capture is refused for `FLAG_SECURE`, finance apps, and visible password fields. A non-empty accessibility tree is preferred over a screenshot. | `observation_secureWindow_doesNotAttachScreenshot` |
| Silent background run | Background actions require a routine the user approved. | `routinePolicy_backgroundWithoutApproval_denies` |
| Vault sync or plaintext export | Robin vault entries stay in `noBackupFilesDir` under a biometric Keystore key. GitHub, Obsidian, and Drive are blocked. Plaintext export is disabled. | `AutofillAndVaultTest` |

## Smoke checklist

- [ ] Enable accessibility, notification access, and review autofill. Confirm the setup buttons open the right Settings pages.
- [ ] Allow one harmless app, such as a notes app. Ask Robin to open it. Confirm it opens with an intent and the action log records the step.
- [ ] Ask Robin to tap Send or Pay in an allowlisted app. Confirm the sheet names the action and nothing happens until you confirm or use a fingerprint.
- [ ] Open a banking app and ask Robin to continue. Confirm it stops and tells you to tap it yourself. The stop bar is visible.
- [ ] Focus a password field. Confirm Robin does not type the password and the tree text says `[redacted]`.
- [ ] With Robin set as the autofill service, save a vault entry for that app’s package and signing certificate. Confirm the fill asks for a fingerprint and the chat never shows the secret.
- [ ] Tap Save to Google Password Manager on a login screen while Google is the autofill service. Confirm the system save UI can appear and Robin does not display the password.
- [ ] Post a notification whose text says to ignore previous instructions. Confirm the stored text is marked untrusted and no action runs from it.
- [ ] Tap Stop. Confirm the run ends and the bar goes away.
