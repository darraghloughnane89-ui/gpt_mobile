# Plugins, routines, and agent handoff

This stream adds remote MCP plugins, scheduled routines, and agent-to-agent handoff. Brand strings stay in `AppBrand`.

## What was reused

- `io.modelcontextprotocol:kotlin-sdk-client` 0.15.0 speaks Streamable HTTP and SSE. `McpClientManager` opens `StreamableHttpClientTransport` or `SseClientTransport`. There is no hand-rolled JSON-RPC client.
- AppAuth (`net.openid:appauth`) builds the authorization request, carries the PKCE verifier, and exchanges the code with `AuthorizationService.performTokenRequest`. Discovery, protected-resource metadata, and RFC 7591 dynamic client registration stay in `McpOAuthClient`. AppAuth does not implement those MCP steps.
- JetBrains Koog (Apache-2.0) was considered for the agent loop and for routines. It is not a dependency. The existing `AgentRunner` already owns provider adapters, compaction, and tool traces. Routines use WorkManager, which is already wired through `HiltWorkerFactory`. Koog does not schedule Android work.
- Plugin tokens and OAuth credentials go through the existing Keystore vault (`AndroidSecretVault`).

Licences are recorded in the root `NOTICE`.

## Catalog

Every official preset needs a user token or an OAuth grant. None of them ship a token in the app.

| Preset | Endpoint | Auth |
| --- | --- | --- |
| Gmail | `https://gmailmcp.googleapis.com/mcp/v1` | OAuth. Enable the Gmail MCP API in the user's Google Cloud project. |
| Google Calendar | `https://calendarmcp.googleapis.com/mcp/v1` | OAuth. Enable the Calendar MCP API. |
| Google Drive | `https://drivemcp.googleapis.com/mcp/v1` | OAuth. Enable the Drive MCP API. |
| GitHub | `https://api.githubcopilot.com/mcp/` | OAuth, or a personal access token sent as a bearer token. |
| Notion | `https://mcp.notion.com/mcp` | OAuth against a Notion workspace. |
| Smartsheet | `https://mcp.smartsheet.com` | Bearer API token. EU: `https://mcp.smartsheet.eu`. AU: `https://mcp.smartsheet.au`. Paste the regional URL as a custom server if it is not the US host. |
| Slack | `https://mcp.slack.com/mcp` | OAuth for the workspace. |
| Box | `https://mcp.box.com` | OAuth. |
| Vercel | `https://mcp.vercel.com` | OAuth. |
| Cloudflare | `https://mcp.cloudflare.com/mcp` | OAuth, or an API token sent as a bearer token. |

Personal presets are configurable HTTP MCP endpoints. This app does not run those servers.

| Preset | Default URL | Notes |
| --- | --- | --- |
| Hermes | `http://127.0.0.1:8765/mcp` | Hermes Agent on Darragh's Mac (`~/.hermes`, profile `cbre-admin`). Cleartext is allowed. A bearer token is optional. |
| checkit | `http://127.0.0.1:8781/mcp` | Generic MCP bridge. |
| FMFlow | `http://127.0.0.1:8782/mcp` | Generic MCP bridge. |
| Gridwell | `http://127.0.0.1:8783/mcp` | Generic MCP bridge. |
| Loughnane Lifts | `http://127.0.0.1:8784/mcp` | Generic MCP bridge. |

A custom server accepts a URL, Streamable HTTP or SSE, and no token, a bearer token, or OAuth.

## What each personal server must expose

The server speaks MCP over Streamable HTTP (preferred) or SSE:

1. `initialize` with protocol version `2025-06-18` (or another version the Kotlin SDK accepts), then the `notifications/initialized` notification.
2. `tools/list`, including `nextCursor` when the list is paged.
3. `tools/call` for each advertised tool.

Hermes, for agent handoff, also exposes a tool named `delegate`:

```json
{ "task": "string", "profile": "cbre-admin" }
```

The app calls that tool when a chat delegates to Hermes. The Hermes Agent itself stays at `~/.hermes` with profile `cbre-admin`. Stream (i) is the bridge that publishes this endpoint. This app only stores the URL.

checkit, FMFlow, Gridwell, and Loughnane Lifts have no required tool names. Whatever `tools/list` returns becomes a toggle in Plugins. Read tools start enabled. Write tools start disabled and still ask for confirmation when enabled.

Tool effect uses the SDK `ToolAnnotations`: `readOnlyHint` is read, `destructiveHint` is write. Otherwise the tool name and description are tokenised. Unknown tools are treated as writes.

## Scopes, confirmation, and handoff

- Bot and project directories are stubbed until those streams land. The selection tables exist. With no selections, every enabled plugin is available to the chat's platform.
- A routine with a blank plugin list gets no plugin tools. A routine with ids gets only those plugins.
- Write tools in a chat show a confirmation dialog. Routines run as unattended work, so write tools are denied instead of waiting.
- `delegate_task` can hand a task to another installed platform or to Hermes. The nested reply is the tool result in the same thread. Nested runs do not get `delegate_task`, so handoff does not recurse.

OAuth returns to `dev.chungjungsoo.gptmobile://oauth/plugin/{pluginUid}`. Tool-connection OAuth stays on `/mcp/`.

## Room

This branch registers database version 20 and migration `11 → 20`. The migration only creates `plugins`, `plugin_tools`, `plugin_scope_selections`, `routines`, and `routine_runs`. Versions 12–19 belong to other streams. When those versions are on main, replace this migration's start version with 19. Do not keep both an `11 → 20` path and a `19 → 20` path: Room can pick the direct path and skip the other streams' tables.

## Setup Darragh must do

1. Google Cloud: enable the Gmail, Calendar, and Drive MCP APIs, and configure the OAuth consent screen for the account that will sign in.
2. GitHub, Notion, Slack, Box, Vercel, and Cloudflare: complete each provider's OAuth grant from Plugins, or paste a user token where the preset allows a bearer fallback.
3. Smartsheet: paste an API token. Change the URL only by adding a custom server for EU or AU.
4. Hermes: run the stream (i) bridge so `http://127.0.0.1:8765/mcp` (or the Mac's reachable address) serves MCP, with tool `delegate` and profile `cbre-admin`. Point the Hermes preset at that URL. The phone must be able to reach the Mac.
5. checkit, FMFlow, Gridwell, and Loughnane Lifts: run each app's MCP endpoint and paste its URL.
6. Allow notifications so routine results can post. Android 13+ asks for `POST_NOTIFICATIONS`.

## Tests

- `McpSdkMockWebServerTest` — official SDK Streamable HTTP client against MockWebServer (`initialize`, `tools/list`, `tools/call`).
- `PluginOAuthFlowTest` — discovery, dynamic client registration, PKCE material copied into the AppAuth launch spec, bearer validation, plugin redirect path.
- `RoutineScheduleTest` — daily, weekly, and cron next-run instants, including a slot equal to now.
- `ToolConfirmationGateTest` — annotation hints, token classification, confirmation, unattended denial, disabled tools.
- `PluginPresetRowInstrumentedTest` — Enable on the Gmail preset row.

`./gradlew testDebugUnitTest` runs the unit tests. The Compose test compiles with the debug test sources and runs on a device.

## Smoke checklist

1. Install the debug build over a version-11 database and open Settings → Plugins. The official and personal rows are listed.
2. Enable Gmail and finish OAuth in the browser. The redirect returns to the app and the status becomes connected after tools refresh. Cancel the browser and confirm the plugin stays unsigned-in.
3. Paste a Smartsheet API token, enable the preset, and refresh tools. Read tools are on. A write tool is off until its switch is on, and chat then asks before running it.
4. Enable Hermes with the bridge URL and no token. Ask a bot to delegate a task to Hermes and confirm the reply lands in the same thread.
5. Create a daily routine with a read-only plugin id, tap Run now, and confirm a notification and a "Routine: …" chat. Confirm a write tool on that routine is refused.
6. Add a custom SSE URL only if that server is actually speaking SSE. Streamable HTTP is the default.
