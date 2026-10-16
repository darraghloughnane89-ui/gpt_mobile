# Phone deep links

Phone integration adds camera capture, a share-sheet target, and a home-screen widget. None of these change the Room schema. Shared text and files stay in memory until the user sends them through the existing chat composer.

Scheme and host, same scheme as the MCP OAuth callback and a different host:

```
dev.chungjungsoo.gptmobile://phone/new-chat
dev.chungjungsoo.gptmobile://phone/ask?input=text
dev.chungjungsoo.gptmobile://phone/ask?input=mic
dev.chungjungsoo.gptmobile://phone/camera
dev.chungjungsoo.gptmobile://phone/voice
dev.chungjungsoo.gptmobile://phone/chat/{chatId}?platforms={url-encoded comma-separated platform uids}
```

`MainActivity` is `singleTask` and handles `ACTION_VIEW` for host `phone`. The widget and the share activity use an explicit `MainActivity` intent so a tap still works if the filter is ignored.

| Link | Behavior |
| --- | --- |
| `phone/new-chat` | Opens a new chat with every enabled platform. If none are enabled, stays put and explains that an assistant is required. |
| `phone/ask?input=text` | Opens the composer for a new chat. |
| `phone/ask?input=mic` | Opens the composer and starts speech input when the device has a recognizer. |
| `phone/camera` | Same as new chat, then launches the system camera. The photo is copied into app storage and attached with the existing image path. |
| `phone/voice` | Navigates to `voice_session` only when that destination is already in the nav graph. Otherwise shows “Voice mode isn’t available yet.” This stream does not register the voice screen. |
| `phone/chat/{id}` | Opens that chat. `platforms` is the chat’s enabled platform list, used to build the existing chat route. |

`Route.VOICE_SESSION` (`voice_session`) is the contract for the voice stream. Register that route and the widget’s Voice button will open it. Until then the button is safe.

The in-app “Ask about a photo” button on the chat list uses the same camera handoff as `phone/camera`, after the usual platform picker. The composer also has a camera button.

## Share sheet

`ShareTargetActivity` handles:

- `ACTION_SEND` for `text/plain`, `image/*`, and `application/pdf`
- `ACTION_SEND_MULTIPLE` for those types, plus `*/*` so a mixed share still arrives

PDF text is extracted on device. If a PDF has no extractable text, the first three pages are rendered to JPEG and attached through the image path. Provider uploads still send images only. A PDF is not uploaded as a fake image.

## Widgets

The phone widget from the first home-screen pass is the Quick Ask widget (`GptMobileWidgetReceiver`). It keeps that receiver so an already placed widget updates in place. Text opens the composer. Mic opens the composer and requests speech input.

Two more Glance widgets share the same theme, deep links, and lock policy:

| Widget | Receiver | Shows | Tap |
| --- | --- | --- | --- |
| Quick ask | `GptMobileWidgetReceiver` | Text field and Mic button. No chat content. | Opens the composer |
| Routine | `RoutineResultWidgetReceiver` | Latest routine result, such as a morning briefing summary | Opens that chat |
| Pinned chats | `PinnedChatsWidgetReceiver` | Chats pinned from the chat list | Opens that chat |

All three follow Material You dynamic colour and the system dark theme, and can be resized on the home screen or the lock screen.

Pinned titles and routine summaries stay hidden while the device is locked, and also on a widget placed on the lock screen, until Settings → Widgets → “Show chat content on the lock screen” is turned on. API keys and other credential-shaped text are removed from widget text even then. Widget state is stored in the preferences file that backup already excludes.

Publish the current chat’s latest reply from the chat menu (“Show on routine widget”). Pin or unpin from the chat list selection bar. The routine widget reads that chat’s latest assistant reply the next time it refreshes (when the chat list resumes, the screen locks or unlocks, or the 30 minute update runs).

## Examples

```
adb shell am start -a android.intent.action.VIEW -d "dev.chungjungsoo.gptmobile://phone/new-chat"
adb shell am start -a android.intent.action.VIEW -d "dev.chungjungsoo.gptmobile://phone/ask?input=text"
adb shell am start -a android.intent.action.VIEW -d "dev.chungjungsoo.gptmobile://phone/ask?input=mic"
adb shell am start -a android.intent.action.VIEW -d "dev.chungjungsoo.gptmobile://phone/camera"
adb shell am start -a android.intent.action.VIEW -d "dev.chungjungsoo.gptmobile://phone/voice"
```
