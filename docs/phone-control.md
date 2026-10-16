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
| Screen text says to tap Pay, open a bank, or autofill | Screen and notification text is wrapped as untrusted and cannot be the source of an action. A fenced tool call, including one that closes the fence early, is not parsed. Obfuscated instructions (zero-width characters, odd spaces, homoglyphs) are folded and removed. If injection is still detected, a silent tap is not allowed. Pay, send, delete, and install need the confirmation sheet. A fingerprint does not replace that sheet. | `PhoneControlAttackTest`, `PhoneControlSafetyTest`, `PhoneAgentLoopTest.run_screenSourcedAction_isNotExecuted` |
| Notification or share says to ignore instructions and email the vault | Tool markup, JSON tool calls, and instruction phrases are stripped before the model sees the text. Vault reads throw if anything asks the model for the secret. The model only receives `fill requested`. | `untrustedText_stripsToolMarkupAndMarksSource`, `untrustedBreakout_jsonToolCall_isNotParsed`, `AutofillAndVaultTest.vaultCapability_modelView_hidesSecret` |
| Overlay says to autofill and submit | Autofill runs only after an autofill-UI tap or field focus plus a fingerprint. The password is not typed by the accessibility service. Package name and certificate SHA-256 must match. A vault entry with no domain is not offered to a website. | `AutofillAndVaultTest`, `credentialBinding_missingDomain_doesNotMatchAWebsite` |
| Invisible or low-opacity instruction text | Those nodes are removed from the tree sent to the model and flagged. A low-confidence, vision-only, or coordinate gesture needs confirmation. Automated actions are rate limited to 12 per minute. | `observation_hiddenInstruction_isDroppedFromTheTreeAndFlagged`, `safetyGate_coordinateTap_needsConfirmation`, `safetyGate_rateLimit_deniesTheThirteenthAction` |
| Screenshot of a secure, finance, or password screen | Capture stays off unless a non-secure capture was verified. `FLAG_SECURE` and screenshot error 6 (`ERROR_TAKE_SCREENSHOT_SECURE_WINDOW`) refuse capture even when the tree is empty. Finance apps and password fields do too. | `observation_flagSecure_blocksVerifiedCapture`, `observation_secureScreenshotError_blocksCapture` |
| Banking app, or a launch that only names the bank in an intent | Known packages, brand names (Revolut, Venmo, Cash App, and the rest of the denylist), and packages embedded in intents are finance. Robin returns `USER_MUST_TAP` even after confirm. The accessibility root package wins over a stale event package. | `safetyGate_venmo_requiresUserTapEvenWhenConfirmed`, `safetyGate_openIntentToBank_requiresUserTap`, `observation_rootPackageBeatsStaleForeground` |
| Allowlist bypass via the text field, a lookalike package, or a view intent | `launch_app`, `open_intent`, and `app_function` must name a package, and every package they name must be on the allowlist. A Play Store `id=` install intent is included. | `safetyGate_launchViaTextField_checksAllowlist`, `safetyGate_launchLookalikePackage_isDenied`, `safetyGate_marketInstallIntent_isDenied` |
| Confirmation sheet skipped for send, pay, delete, or install | Those actions, including obfuscated labels and synonyms (wire, trash, download, upgrade), need `userConfirmed`. A biometric flag alone does not skip the sheet. A tap or swipe with no element index and no label needs the sheet too. | `safetyGate_paymentBiometricAlone_doesNotSkipTheSheet`, `safetyGate_obfuscatedSendPayDeleteInstall_needConfirmation` |
| Password copied into a sibling, a log, a prompt, or memory | Password fields include editable nodes whose id or hint says password. The value is removed from every node, the action log, and the model prompt. `password=`, JSON `"password":`, and `sk-` keys are scrubbed. Memory admission refuses text that still contains a secret. | `observation_passwordCopiedToSibling_isRedacted`, `redaction_jsonPasswordAndApiKey_areScrubbed`, `secretMemory_rejectsPasswordAndApiKey` |
| Stop overlay missing, untappable, or late | The run does not start unless the overlay was added. The window is not-focusable, not touch-modal, laid out in screen, and watches outside touch, so Stop can be tapped and the rest of the screen still works. Stop before the gesture, including during the confirmation wait, cancels the action and releases the sheet. | `stopOverlay_currentFlagsCannotReceiveStopTap`, `controlSession_refusesToStartWhenOverlayIsMissing`, `run_stopDuringConfirmation_doesNotAct` |
| Silent background run | Background actions require a routine the user approved. | `routinePolicy_backgroundWithoutApproval_denies` |
| Vault sync or plaintext export | Robin vault entries stay in `noBackupFilesDir` under a biometric Keystore key. GitHub, Obsidian, and Drive are blocked. Plaintext export is disabled. | `AutofillAndVaultTest` |

## Review findings

Adversarial pass on the phone-control gate, October 2026. Each item below was a bypass of the previous gate. `PhoneControlAttackTest` covers them.

- **Untrusted fence breakout.** Screen or notification text could emit `</untrusted>` and then a JSON tool call. The parser treats that JSON as a model action. Wrapping now folds confusable characters, strips instruction phrases and tool-call JSON, and escapes brackets so a payload cannot close the fence.
- **Obfuscated instructions.** Zero-width characters, non-breaking spaces, and Cyrillic lookalikes got past the exact phrase list, including on Send, Pay, Delete, and Install. Matching now uses a canonical fold.
- **Allowlist checked the wrong package.** `launch_app` with the package only in `text`, `open_intent` to another package, `app_function` aimed at a second package, and `market://details?id=` ran while the foreground app was allowlisted. Destinations are parsed and every package must be listed. An intent with no package is denied.
- **Finance denylist was exact package names plus a few generic words.** Venmo, Cash App, a Revolut-branded wrapper, and `package=com.revolut.revolut` inside an intent were not finance. Brand and embedded-package checks return `USER_MUST_TAP`. The tree root's package is used when the last accessibility event was from another process.
- **Confirmation sheet bypass.** `biometricConfirmed` alone allowed pay, send, delete, and install. Coordinate taps and swipes with no label were allowed. The sheet is required for those actions; a fingerprint still authorizes autofill only.
- **FLAG_SECURE.** An empty tree with vision requested was treated as a screenshot even when the window was secure, and screenshot error 6 was never consulted. Capture is attached only after a verified non-secure capture, and never for `FLAG_SECURE`, that error, a finance app, or a password field.
- **Password leakage.** A revealed password on a sibling node, an editable field whose resource id contains `password` but is not `isPassword`, JSON `"password":"..."`, and `api_key` / `sk-` values reached the tree, the log, or memory. Those values are redacted, and memory admission rejects the original text. A vault entry with no domain is not filled into a website.
- **Stop overlay.** The overlay used only `FLAG_NOT_FOCUSABLE`, so it could sit under the status bar and the window could eat touches around the button. The run also performed the planned gesture after Stop, including when Stop happened while the confirmation sheet was open. The run stays stopped until the overlay is showing, and Stop is checked again before each gesture and after confirmation.

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
