# Direct send to Hermes

Robin hands a message, a chat, a file, or a short task to Darragh's Hermes Agent
(`cbre-admin` on his Mac) and shows the streamed reply in a Hermes thread. The
same endpoint can also be turned on as a normal chat model.

Sources read on 15–16 October 2026 from [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent)
`main`:

- [API Server](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server)
  (`website/docs/user-guide/features/api-server.md`)
- `gateway/platforms/api_server.py` (`DEFAULT_PORT = 8642`, `has_usable_secret(..., min_length=16)`,
  `_direct_model_requests` default `False`)
- [Webhooks](https://hermes-agent.nousresearch.com/docs/user-guide/messaging/webhooks)
  (`website/docs/user-guide/messaging/webhooks.md`)
- [Messaging gateway](https://hermes-agent.nousresearch.com/docs/user-guide/messaging)
  (`website/docs/user-guide/messaging/index.md`)

## How Hermes accepts inbound messages

Hermes has three different inbound surfaces. They are not interchangeable.

### 1. OpenAI-compatible API server (the path this app uses)

The gateway can expose the agent as an HTTP API. It is off until enabled.
The listener defaults to `http://127.0.0.1:8642`, which a phone cannot reach.

Authenticated calls use `Authorization: Bearer <API_SERVER_KEY>`. The process
refuses to start unless `API_SERVER_KEY` is present and at least 16 characters
(`gateway/platforms/api_server.py`, `has_usable_secret(..., min_length=16)`).
Generate one with `openssl rand -hex 32`.

Public liveness (no token):

- `GET /health` and `GET /v1/health` return `{"status":"ok"}`.

Authenticated:

- `GET /v1/models` advertises one model id: the profile name, or `hermes-agent`
  for the default profile. Override with `API_SERVER_MODEL_NAME`.
- `GET /v1/capabilities` describes the stable surface.
- `POST /v1/chat/completions` is the OpenAI Chat Completions route. It is
  **stateless**: the client sends the `messages` array every time.
  `"stream": true` returns SSE `chat.completion.chunk` frames. Answer text is
  `choices[0].delta.content`. Tool starts arrive as a custom SSE event
  `event: hermes.tool.progress` (the JSON is not assistant text). A `: keepalive`
  comment is sent whenever the stream has been idle for 10 seconds. The stream
  ends with `data: [DONE]`.
- `POST /v1/responses` keeps server-side history (`previous_response_id` or
  `conversation`).
- `POST /v1/runs` starts a run and returns a `run_id`. Progress is a separate
  SSE subscription. An `Idempotency-Key` here is reserved for 24 hours.

Chat Completions also accept `Idempotency-Key`. The API server docs say that
header's responses are cached for about 5 minutes (CORS section of the API
server page). That is shorter than a WorkManager retry window, so this app
does not rely on it as the only duplicate guard.

Session headers, both max 256 characters:

- `X-Hermes-Session-Id` continues a transcript the API server owns. It cannot
  attach to a CLI, TUI, Desktop, or messaging session (HTTP 409
  `permission_denied`).
- `X-Hermes-Session-Key` is a stable memory scope, independent of the transcript
  id. The documented example is `agent:main:webui:dm:user-42`.

Images may be inlined as `image_url` parts, including `data:image/...` URLs.
`file`, `input_file`, `file_id`, and non-image `data:` URLs return
`400 unsupported_content_type`. There is no general file upload.

A bare `model` string on Chat Completions is **ignored** unless the gateway
sets `gateway.platforms.api_server.direct_model_requests: true`. Profile
selection is which listener (or which `/p/<profile>` prefix) received the
request, not the model name. This app still sends the advertised model id so
`/v1/models` and the chat picker agree. With the default flag, Hermes runs the
profile's configured model.

### 2. Profile selection

A profile is an isolated Hermes home (config, memory, skills, `.env`).

Two ways to reach `cbre-admin`:

1. **Dedicated gateway (recommended).** `API_SERVER_*` lives in
   `~/.hermes/profiles/cbre-admin/.env`. Start that profile's own listener
   with `hermes -p cbre-admin gateway`. The phone calls `/v1/...` on that port.
   `/v1/models` advertises `cbre-admin` unless `API_SERVER_MODEL_NAME` is set.
2. **Multiplex.** The default gateway sets `gateway.multiplex_profiles: true`
   and serves `/p/<profile>/v1/...`. The named prefix requires **that profile's**
   `API_SERVER_KEY` from `~/.hermes/profiles/<profile>/.env`. The default
   profile's key on a named prefix is HTTP 401 (documented July 2026 breaking
   change). A named profile with no key of its own fails closed.

Environment variables win over the same keys under `gateway.api_server` in
`config.yaml` (`enabled`, `port`, `host`, `key`, `cors_origins`, `model_name`).

The connection test decides which route to cache:

- `GET /p/<profile>/v1/models` returns 200 → multiplex.
- that prefix returns 401 and unprefixed `/v1/models` returns 200 → the token
  belongs to a different profile. The app reports a mismatch and does not send.
- the prefix returns 404 and unprefixed `/v1/models` returns 200 → dedicated
  profile gateway. The app uses `/v1`.

### 3. Webhooks (not used)

`website/docs/user-guide/messaging/webhooks.md` describes an inbound HTTP
adapter (`WEBHOOK_ENABLED`, default port 8644, `WEBHOOK_SECRET`) that accepts
HMAC-signed events from GitHub, GitLab, and similar producers and turns them
into agent prompts. It is a producer-to-agent trigger, not a chat the phone
can stream a reply from. Robin does not call it.

### 4. Messaging gateways (fallback only)

`hermes gateway` also polls Telegram, Discord, and the other platforms in
`website/docs/user-guide/messaging/index.md`. A person can message the same
agent from Telegram when the phone cannot route to the Mac. That path is
outside the app: there is no Telegram sender in Robin. The bot token and
allowlist stay on the Mac (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_USERS`).

## Chosen path

Robin posts `POST {apiRoot}/chat/completions` with `"stream": true`.

- One connection carries both delivery and the reply, including
  `hermes.tool.progress` notes in the Hermes thread.
- The body is OpenAI-shaped, so the same endpoint can be stored as a
  `ClientType.CUSTOM` platform (`uid` `hermes-direct-chat`, name `Hermes`)
  and picked like any other model. That chat uses the existing OpenAI adapter,
  which keeps `delta.content` and skips the custom tool-progress frames.
- Chat Completions are stateless, so the client resends recent Hermes-thread
  turns (capped at 16,000 characters) plus a system preface that names the
  profile. `X-Hermes-Session-Id` is the thread id and `X-Hermes-Session-Key`
  is `robin:<thread id>` (the brand prefix is the constant `robin` in
  `HermesDefaults`, not a translated string). If Hermes also injects session
  memory for that key, the model may see the recent turns twice. That is
  preferred to dropping history when the session is new.
- Images up to 1.5 MB are inlined as `data:` URLs. Other files are named in
  the prompt. PDFs are not extracted here.
- `Idempotency-Key` is the assistant turn id. After response headers arrive,
  the turn is marked accepted and a later automatic retry will not POST it
  again. A dropped stream becomes **Interrupted**. Retry from the thread
  mints a new key. Network failures before accept stay **Queued**.
- Offline handoff is WorkManager unique work `hermes-delivery`
  (`NetworkType.CONNECTED`, exponential backoff 30 seconds, at most 6
  attempts, then **Failed**). No endpoint or token leaves the item queued and
  the worker succeeds, so a later save sends it. The worker runs as a
  `dataSync` foreground service so a long agent turn is less likely to be
  killed. Thread JSON is under `noBackupFilesDir` (`hermes/threads.json`).
  The API key is only in the existing Android Keystore vault
  (`hermes_api_server_key`). Endpoint and profile are DataStore preferences
  (`hermes_settings`). No Room migration.

## What to enable on the Mac

Recommended: a dedicated gateway for profile `cbre-admin`, bound to the Mac's
Tailscale address. Do not put this key in `~/.hermes/.env` unless that file
is the `cbre-admin` profile.

```bash
# Tailscale IPv4 of this Mac. Install Tailscale on the phone too.
tailscale ip -4

install -d ~/.hermes/profiles/cbre-admin
umask 077
cat >> ~/.hermes/profiles/cbre-admin/.env <<EOF
API_SERVER_ENABLED=true
API_SERVER_HOST=$(tailscale ip -4)
API_SERVER_PORT=8642
API_SERVER_KEY=$(openssl rand -hex 32)
API_SERVER_MODEL_NAME=cbre-admin
EOF

# Confirm the key is at least 16 characters, then start this profile only.
hermes -p cbre-admin gateway
# or, to keep it running:
hermes -p cbre-admin gateway install
hermes -p cbre-admin gateway restart
```

Copy `API_SERVER_KEY` into Robin once (Settings → Hermes → API key). The
endpoint is `http://<tailscale-ipv4>:8642` with no `/v1` suffix.

Equivalent `~/.hermes/profiles/cbre-admin/config.yaml` keys, if you prefer
YAML. Env still wins when both are set:

```yaml
gateway:
  api_server:
    enabled: true
    host: 100.x.y.z
    port: 8642
    key: <the same API_SERVER_KEY>
    model_name: cbre-admin
```

Leave `gateway.platforms.api_server.direct_model_requests` unset. Robin does
not ask Hermes to switch the underlying provider.

Check from the Mac:

```bash
curl -sS "http://127.0.0.1:8642/health"
curl -sS -H "Authorization: Bearer $API_SERVER_KEY" \
  "http://127.0.0.1:8642/v1/models"
```

`/v1/models` should list `cbre-admin`. Then run **Test connection** in the app.

### Multiplex alternative

Only if the default gateway should serve every profile:

```yaml
# ~/.hermes/config.yaml of the default profile
gateway:
  multiplex_profiles: true
```

`cbre-admin` still needs its own `API_SERVER_KEY` in
`~/.hermes/profiles/cbre-admin/.env`. The phone endpoint stays the default
listener (`http://<host>:8642`). Robin will call `/p/cbre-admin/v1/...`.
The default profile's key will not authenticate that prefix.

## Reachability

The API server binds to `127.0.0.1` unless `API_SERVER_HOST` is changed.
`usesCleartextTraffic` is already enabled, so an `http://` Tailscale or LAN
URL works. Prefer Tailscale over publishing port 8642 on the router. The API
server can run terminal tools; the key is a full agent credential.

| Path | What to set | When |
| --- | --- | --- |
| Same Wi-Fi | `API_SERVER_HOST` to the Mac's LAN address, or `0.0.0.0`. Phone endpoint `http://<lan-ip>:8642`. macOS firewall must allow the port. | Phone and Mac are on one network. Breaks off that network. |
| Tailscale (use this) | Install Tailscale on both. `API_SERVER_HOST` to `tailscale ip -4`. Phone endpoint `http://<tailscale-ip>:8642`. | Phone should reach the Mac from anywhere on the tailnet. |
| Telegram fallback | `hermes gateway setup`, then `TELEGRAM_BOT_TOKEN` and `TELEGRAM_ALLOWED_USERS` (see the messaging gateway doc). | The phone cannot route to the Mac. Send the text yourself in Telegram. Robin will not deliver it. |

WorkManager retries while the phone has any network. It cannot tell a captive
portal from a missing Mac, so a configured but unreachable agent stays queued
until the attempt cap, then shows **Failed** with "Could not reach Hermes."
Use **Retry** on that turn after the Mac is back.

## In the app

- Settings → Hermes: endpoint, API key (Keystore), profile (default
  `cbre-admin`), Test connection, and a switch that adds Hermes as a chat model.
- Long-press a user message, or use **Send to Hermes** on an assistant reply.
- Chat menu **Send chat to Hermes** sends the transcript or a short summary.
- Composer **Ask Hermes** queues a task, including any attachments staged on
  the composer.
- The share sheet target **Send to Hermes** accepts text and files
  (`HermesShareActivity`). Files are copied into the app's no-backup inbox.
- Status on each Hermes turn: Queued, Sending, Streaming, Delivered, Failed,
  Interrupted. Open the thread from Hermes settings.

The visible product name is the `brand_name` string resource (`Robin`). Hermes
stays "Hermes". Changing the brand later is that one string.

## Smoke test

1. On the Mac, `curl` `/health` and `/v1/models` as above. Confirm the process
   log says it is listening on the Tailscale address, not only `127.0.0.1`.
2. In Robin, set endpoint `http://<tailscale-ip>:8642`, paste the key, leave
   profile `cbre-admin`, and tap **Test connection**. Expect a success that
   names the route (direct or multiplex) and the model id.
3. Turn on **Chat with Hermes**. Start a new chat, pick Hermes, and send
   "Reply with the word pong." The reply should stream in that chat.
4. In another chat, long-press a message and choose **Send to Hermes**. Open
   the Hermes thread and confirm Queued → Streaming → Delivered, with the
   reply text.
5. Use **Ask Hermes** with a sentence and, if you have one, a small PNG.
   The thread should mention the image. A PDF should be named in the prompt
   and not uploaded as a document.
6. Share a note into Robin with the system share sheet (**Send to Hermes**)
   and confirm a queued turn.
7. Enable airplane mode, send another message to Hermes, and confirm it stays
   **Queued**. Disable airplane mode and confirm it sends without tapping Retry.
8. Stop the gateway mid-reply. The turn should become **Interrupted**, not a
   second automatic POST. Tap **Retry** and confirm one new reply.

## Tests

Unit tests (`app/src/test/.../data/hermes/HermesClientTest.kt`) use
MockWebServer:

- endpoint parsing (LAN, Tailscale host without a scheme, `/p/cbre-admin`)
- SSE deltas, `: keepalive`, `hermes.tool.progress`, and `[DONE]`
- image data URLs versus a PDF that is only named
- queue recovery and a new idempotency key after an accepted failure
- a streamed "Hello Hermes" delivery with `Authorization`, `Idempotency-Key`,
  and `X-Hermes-Session-Key: robin:<thread>`
- profile mismatch when `/p/cbre-admin` is 401 and `/v1/models` is 200
- a connection failure that leaves the turn queued
- a handoff with no settings that stays queued

`HermesShareRequestTest` checks the share-sheet request builder.
`HermesUiTest` is a Compose androidTest (settings default profile, queued
status, long-press **Send to Hermes**, **Ask Hermes**). It is compiled with
the androidTest sources and runs on a device; `testDebugUnitTest` does not
start an emulator.
