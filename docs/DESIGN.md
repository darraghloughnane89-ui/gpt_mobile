# Robin design system

The product name lives in one place: `app_name` in `app/src/main/res/values/strings.xml`. Kotlin reads it through `Brand.name()`. A rename is that one string. Do not hardcode the name, and do not override `app_name` in locale files.

The launcher mark is a name-agnostic ember crescent and amber spark (`ic_gpt_mobile_foreground`, monochrome layer, splash `ic_brand_splash`). It is not a letter and not a bird, so it still works if the name changes. The in-app character is separate: `Mascot` draws the geometric bird. Swap the character by editing `Mascot.kt`. Leave the launcher mark alone.

Package and `applicationId` stay `dev.chungjungsoo.gptmobile`.

## Voice

Short, dry, a little craic. Confident, never corporate, never “I’m an AI”, and never a list of brand adjectives in the UI. Loading lines are observational. Errors say what failed, then one human beat. Onboarding gets the person moving. Tight unless the user asks for more.

Copy lives in `values/strings.xml` so every stream uses the same lines. Locale files do not override these keys; translators replace the default later rather than shipping the old slogan beside the new voice.

| Job | Resource |
| --- | --- |
| Loading | `loading_lines` (40). `LoadingCaption(seed)` picks one. |
| Empty chats | `empty_chats_title`, `empty_chats_body` |
| Empty projects, bots, skills, memory, review, usage, Obsidian | `shell_*_title`, `shell_*_body` |
| Empty routines | `empty_routines_title`, `empty_routines_body` |
| Empty plugins | `empty_plugins_title`, `empty_plugins_body` |
| Search miss | `no_search_results`, `no_search_results_description` |
| Load failure | `chats_load_error`, `chats_load_error_description` |
| Feature errors | `error_model_unreachable`, `error_biometric_required`, `error_plugin_timeout`, `error_autofill_field`, `error_phone_control_stopped`, `error_vault_locked`, `error_routine_failed`, `error_model_offline`, `error_voice_unparsed`, `error_search_unuseful`, `error_bot_handoff`, `error_camera_denied`, `error_backup_failed`, `error_token_limit`, `error_autofill_blocked`, `error_risky_screen`, `error_untrusted_notification`, `error_skill_failed`, `error_obsidian_conflict`, `error_unexpected` |
| Onboarding | `onboarding_welcome` (`%1$s` is the brand), `onboarding_nest`, `onboarding_biometrics`, `onboarding_allowlist`, `onboarding_autofill`, `onboarding_tools`, `onboarding_first_bot`, `onboarding_memory`, `onboarding_routines`, `onboarding_voice`, `onboarding_obsidian`, `onboarding_widget`, `onboarding_offline`, `onboarding_usage`, `onboarding_ready` |

`welcome_title` / `welcome_description` and `setup_complete` / `setup_complete_description` are the two lines the current start and setup-complete screens show. They match `onboarding_welcome`, `onboarding_nest`, and `onboarding_ready`. Later setup steps should use the matching `onboarding_*` string instead of writing a new sentence.

Taste notes on the source draft: the UI does not say “quirky, premium”. “Fluffing the feathers” and “slick as a wet leaf” were rewritten. Errors stay specific; the bird is the second clause, not the diagnosis.

## Colour, type, shape

Dark is the brand. `GPTMobileTheme` defaults to `ThemeMode.DARK`. A new install and the first frame before preferences load use true black. A stored `ThemeMode.SYSTEM` still wins after `ThemeViewModel` finishes fetching. `ThemeSetting()` itself stays `SYSTEM` so existing preference tests keep their contract. Dynamic colour stays off until the user turns it on (`dynamic_theme_description`).

| Token | Dark | Light |
| --- | --- | --- |
| Scheme | `AmoledDarkScheme` | `PaperLightScheme` |
| Background | `#000000` | `#FFF8F4` |
| Primary | Ember `#FF5C3A` | `#B42318` |
| Secondary | Amber `#FFB15A` | `#8A4F00` |
| Tertiary | Clay `#E7C4B0` | warm clay container |
| On surface | `#F6EFEA` | `#1C1210` |

`contrastRatio(foreground, background)` is available when a stream checks a new pair. Ember on true black and the deep primary on paper both clear body-text contrast.

Type is Outfit (UI) and JetBrains Mono (code), both OFL, variable, in `res/font`. Use `MaterialTheme.typography` and `CodeTextStyle`. Do not set a one-off font.

`Space` is 2, 4, 8, 12, 16, 24, 32, 48 dp. `Elevation` is 0, 1, 3, 8, 12 dp. `BrandShapes` is 10 / 14 / 22 / 28 / 36. User bubbles use `userBubbleShape()` (22, 22, 8, 22). Assistant bubbles use `assistantBubbleShape()`.

## Motion

Springs, not long tweens, for anything the user caused.

| Spec | Damping | Stiffness | Use |
| --- | --- | --- | --- |
| `spatialSpring` | 0.78 | 420 | Screen and shared bounds |
| `playfulSpring` | 0.62 | 520 | Mascot-scale moments, send morph |
| `snapSpring` | 0.86 | 900 | Chips, icon swaps |
| `fadeSpring` | 1 | 2800 | Opacity |

`defaultSpatialSpec`, `fastSpatialSpec`, and `fastEffectsSpec` delegate to these. Navigation enter/pop is a short scale plus the existing slide. Predictive back is the platform one (`enableOnBackInvokedCallback`). Do not add a second `PredictiveBackHandler`.

`SharedTransitionLayout` wraps the nav host. `Modifier.sharedChatContainer(chatId)` shares the chat row and the open chat. Skip it when `chatId <= 0`.

`rememberHaptic()` returns a function of `HapticEvent`: `Tick` (selection), `Confirm` (send, new chat), `Reject` (stop, delete).

Screenshot and unit tests must pass `animated = false` into `Mascot`, `VoiceOrb`, `TypingIndicator`, `StreamingText`, `Skeleton`, and `EmptyState`. Infinite transitions never go idle.

### Mascot, not Lottie

`Mascot` is drawn in Compose: tail, body, ember breast, clay wing, cream head, amber beak, blink, hop, and a wing flap on `MascotMood.Wave`. Moods are `Idle`, `Wave`, `Peek`, `Think`. A Lottie runtime was considered and skipped. The bird is a few paths; a JSON player would add a few hundred kilobytes to redraw the same mark. Pass `animated = false` for a still frame. The launcher asset stays static.

## Information architecture

Phone width under 600dp: modal drawer. At 600dp and above: navigation rail. Both list the same destinations. The drawer header has the mascot, the brand, and **Jump to** (`CommandPalette`), a sheet that filters every destination, including ones that are not in the drawer.

`AppNavScaffold(current, onDestination) { openDrawer, openCommand -> }` is the shell. Home’s menu icon calls `openDrawer`. Search stays in the top-bar action slot. Settings is not a gear on the chat list.

| Place | Section | How you get there | What to build with |
| --- | --- | --- | --- |
| Chats | Talk | Start destination. Drawer and rail. | `ChatBubble`, `ComposerBar`, `sharedChatContainer`, `EmptyState`, `ChatListSkeleton` |
| Voice | Talk | Drawer, rail, and the composer mic when the field is empty | `VoiceOrb`, `Disclosure`. Route `design/voice` until the voice stream replaces it |
| Projects | Library | Drawer and rail | `SurfaceCard`, `EmptyState`. Route `design/projects` |
| Bots | Library | Drawer and rail | `SurfaceCard`, `EmptyState`, `LoadingMoment`. Route `bots` (list, editor, switcher) |
| Skills | Library | Drawer and rail | `SkillCard` / `SkillItem`. Route `design/skills` |
| Memory | Library | Drawer and rail | `EmptyState`, `Disclosure`, `KeyValueRow`. Route `design/memory` |
| Review | Inbox | Drawer and rail | `ReviewInboxItem`, `ReviewActionBar`. Route `design/review` |
| Settings | You | Drawer and rail | Existing `SettingScreen`. Route `SETTING_ROUTE` |
| Usage | You | Settings row and Jump to. Not in the drawer | `KeyValueRow`, collapsed `Disclosure`. Route `design/usage` |
| Obsidian sync | You | Settings row and Jump to. Not in the drawer | `Disclosure` (“What syncs”). Route `design/obsidian` |
| Hermes | — | Not a destination. The composer’s trailing control | `HermesSendAction`, `hermesSendState` |
| Routines | Library, later | Strings only until that stream has a screen | `empty_routines_*`, `onboarding_routines`, `error_routine_failed` |
| Plugins | Settings | Existing tool connections. Not a new root | `empty_plugins_*`, `onboarding_tools`, `error_plugin_timeout` |

`AppDestination.route()` is the seam. Point it at the real screen and delete the shell composable for that destination. `designNavigation()` is the single call in `NavigationGraph`. Do not add feature packages, Room migrations, or a second drawer.

Shells are honest empty states. They do not invent chats, skills, or review items. Sample data belongs in screenshot tests.

### Progressive disclosure

The drawer is seven destinations plus Review, not every feature. Usage, Obsidian, plugins, dynamic colour, and sync detail start collapsed or one level down in Settings. `Disclosure` is collapsed by default. The composer shows the mic only when there is nothing to send, and Hermes only when there is. Voice detail, usage counting, and what Obsidian syncs are disclosures, not paragraphs on the empty state.

### Gap list that belongs to design

These are the gaps from the product comparison that change navigation or components. The rest (native assistant APIs, social context, health data as a capability, image-model parity) are product work and do not get a new root destination.

- **Phone control.** Allowlist, risky-action confirmation, and the stop overlay are a Settings group under You, plus an overlay that exists only while control is active. Always show a stop affordance. Use `error_phone_control_stopped`, `error_risky_screen`, `onboarding_allowlist`. Do not put “phone control” in the drawer.
- **Autofill and vault.** Fingerprint copy is `onboarding_autofill`, `onboarding_biometrics`, `error_biometric_required`, `error_vault_locked`, `error_autofill_blocked`, `error_autofill_field`. The model never sees the secret; the UI must not render a secret to confirm it.
- **Routines.** Library section, but no drawer row until the screen exists. Empty and error strings are ready.
- **Plugins and OAuth.** Stay on the tool-connections screen. Empty copy is `empty_plugins_*`. A polished connector is that screen’s job, not a new tab.
- **Generation.** If image or video generation arrives, it is a composer attachment disclosure, not a destination next to Chats.
- **Widget, share sheet, overlay.** They are entry points outside the app (`onboarding_widget`). In-app, they are explained from Settings. Untrusted share or notification text uses `error_untrusted_notification` and must be visually marked as untrusted before it is sent to a model.
- **Background work.** A routine that runs while the app is closed still needs a visible stop and a review inbox row when it wants a decision. Do not invent a second home for that status.
- **Health data.** If it is added, it is a Settings disclosure. It is not a section.

## Components

All of these are in `dev.chungjungsoo.gptmobile.presentation.ui.designsystem` unless noted.

### Brand and theme

- `Brand.name()` and `Brand.name(context)`
- `AmoledDarkScheme`, `PaperLightScheme`, `contrastRatio`
- `Space`, `Elevation`, `BrandShapes`, `userBubbleShape()`, `assistantBubbleShape()`
- `GPTMobileTheme(dynamicTheme, themeMode)` in `presentation.theme`
- `AppTypography`, `CodeTextStyle`, `OutfitFamily`, `MonoFamily`

### Navigation

- `AppDestination`, `AppSection`, `DesignRoute`
- `drawerDestinations()` omits Usage and Obsidian
- `paletteDestinations()` is every destination
- `shellDestinations()` is everything that still has a placeholder screen
- `AppNavScaffold`, `CommandPalette`
- `NavHostController.openAppDestination`, `NavGraphBuilder.designNavigation`
- `FeatureShellScreen` is the placeholder. Replace the route, do not restyle the shell into a fake product
- `AppDestination.title()`, `emptyTitle()`, `shellBody()`, `icon()`, `AppSection.title()`

### Chat

- `ChatBubble(text, role: BubbleRole)` and `MarkdownMessage` for the simple paragraph/code split
- `parseSimpleMarkdown` → `MarkdownBlock.Paragraph` or `MarkdownBlock.Code`
- The live chat still uses `ChatMarkdown`. Its code fence delegates to `CodeBlock`
- `CodeBlock(code, language, onCopy, content)` shows the language, a copy button, and either mono text or a highlighted `content` slot
- `ComposerBar(canSend, isSending, onSend, onStop, field, onAttach, onMic, attachments)`. Attach content description stays `Attach File`. `onMic` defaults to null so existing tests do not see a mic
- `hermesSendState(canSend, isSending, sent)` → `HermesSendState.Idle`, `Armed`, `Sending`, `Sent`
- `HermesSendAction`. Sending is announced as `cancel_active_runs` (“Cancel active runs”). Every other state is “Send”. Enabled only for `Armed` and `Sending`
- `TypingIndicator`, `StreamingText(text, shimmer, animated)`
- `ModelChip`, `ModelOption`, `ModelSwitcherSheet`. `PlatformButton` delegates here and keeps the model name

### Surfaces

- `SurfaceCard`, `SectionLabel`, `KeyValueRow`
- `EmptyState(title, body, mood, showMascot, animated, actionLabel, onAction)`
- `Disclosure(title, initiallyExpanded = false)`
- `Skeleton`, `SkeletonBlock`, `ChatListSkeleton` (`animated`)
- `LoadingMoment(seed, caption, animated)` — mascot plus a caption for loading screens
- `BlurTopBar`, `frostTopAppBarColors()`
- `LoadingCaption(seed)`

### Character and voice

- `Mascot(mood, animated)`
- `VoiceOrb(amplitude, state: VoiceOrbState, onClick, animated)`. States: `Idle`, `Listening`, `Speaking`, `Thinking`. `amplitude` is 0..1

### Skills and review

- `SkillItem`, `SkillCard(skill, onClick, onEnabledChange)`
- `ReviewItem`, `ReviewInboxItem`
- `ReviewActionBar` with labels passed in (`review_approve` Keep, `review_dismiss` Dismiss, `review_later` Later)

### Motion helpers

- `spatialSpring`, `playfulSpring`, `snapSpring`, `fadeSpring`
- `rememberHaptic`, `HapticEvent`
- `LocalSharedTransitionScope`, `LocalAnimatedVisibilityScope`, `sharedChatContainer`

## What this stream does not own

Room version stays 11. Do not edit `ChatDatabaseV2Migrations` or add tables. Do not rewrite `ChatViewModel`. Do not add permissions. Voice, bots, projects, providers, phone control, and memory/sync replace shell routes and consume the components above. Hermes is the send control they already have, not a new route.
