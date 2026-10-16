# Phone deep links

Phone integration adds camera capture, a share-sheet target, and a home-screen widget. None of these change the Room schema. Shared text and files stay in memory until the user sends them through the existing chat composer.

Scheme and host, same scheme as the MCP OAuth callback and a different host:

```
dev.chungjungsoo.gptmobile://phone/new-chat
dev.chungjungsoo.gptmobile://phone/camera
dev.chungjungsoo.gptmobile://phone/voice
dev.chungjungsoo.gptmobile://phone/chat/{chatId}?platforms={url-encoded comma-separated platform uids}
```

`MainActivity` is `singleTask` and handles `ACTION_VIEW` for host `phone`. The widget and the share activity use an explicit `MainActivity` intent so a tap still works if the filter is ignored.

| Link | Behavior |
| --- | --- |
| `phone/new-chat` | Opens a new chat with every enabled platform. If none are enabled, stays put and explains that an assistant is required. |
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

## Widget

`GptMobileWidgetReceiver` places a Glance widget with New chat, Voice, and up to three recent chats. Recent chats refresh when the system updates the widget (at most every 30 minutes) or when the widget is added again.

## Examples

```
adb shell am start -a android.intent.action.VIEW -d "dev.chungjungsoo.gptmobile://phone/new-chat"
adb shell am start -a android.intent.action.VIEW -d "dev.chungjungsoo.gptmobile://phone/camera"
adb shell am start -a android.intent.action.VIEW -d "dev.chungjungsoo.gptmobile://phone/voice"
```
