# Plugins, routines, and agent handoff

Checked against vendor docs on 16 October 2026. An endpoint is **verified** when the vendor page names that URL and auth method. **Unverified** means the URL is documented, but this app's public OAuth client (PKCE, no client secret) is not what that vendor tells you to configure.

Hermes is not a plugin. A chat hands work to Hermes through the OpenAI-compatible API server already used by direct send. See `docs/HERMES.md` on branch `cursor/hermes-direct-send-9650` and the [Hermes API server guide](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server).

## In the app

1. Open **Settings**.
2. Tap **Plugins**.
3. Official rows are filled in. Personal rows are blank and say **Needs server**.
4. Tap **Enable** on an official row. OAuth rows open the browser. Bearer rows need a token in the field first.
5. After tools appear, leave read tools on. Turn a write tool on only if you want it. Chat asks before a write runs. A routine refuses writes.
6. **Routines** is the next Settings row. A routine runs a prompt on a schedule and posts the result.

`127.0.0.1` on the phone is the phone. Do not point a plugin at localhost unless the server is actually on the phone.

## Official connectors

| Preset | Endpoint | Auth | Status | Doc |
| --- | --- | --- | --- | --- |
| Gmail | `https://gmailmcp.googleapis.com/mcp/v1` | OAuth. Streamable HTTP. | Verified | [Configure Workspace MCP](https://developers.google.com/workspace/guides/configure-mcp-servers), [Gmail](https://developers.google.com/workspace/gmail/api/guides/configure-mcp-server) |
| Google Calendar | `https://calendarmcp.googleapis.com/mcp/v1` | OAuth. Streamable HTTP. | Verified | [Configure Workspace MCP](https://developers.google.com/workspace/guides/configure-mcp-servers) |
| Google Drive | `https://drivemcp.googleapis.com/mcp/v1` | OAuth. Streamable HTTP. | Verified | [Drive MCP](https://developers.google.com/workspace/drive/api/guides/configure-mcp-server) |
| GitHub | `https://api.githubcopilot.com/mcp/` | OAuth, or a personal access token as `Authorization: Bearer`. | Verified | [Remote server](https://github.com/github/github-mcp-server/blob/main/docs/remote-server.md), [Host integration](https://github.com/github/github-mcp-server/blob/main/docs/host-integration.md) |
| Notion | `https://mcp.notion.com/mcp` | OAuth. A Business or Enterprise MCP token may be sent as a bearer token instead. SSE fallback `https://mcp.notion.com/sse` is only for clients that cannot speak Streamable HTTP. | Verified | [Get started](https://developers.notion.com/guides/mcp/get-started-with-mcp), [Build a client](https://developers.notion.com/guides/mcp/build-mcp-client) |
| Smartsheet | `https://mcp.smartsheet.com` | API token as `Authorization: Bearer`. EU `https://mcp.smartsheet.eu`. AU `https://mcp.smartsheet.au`. Partner apps (ChatGPT, Claude, Copilot) can use Smartsheet OAuth. This app uses the token. | Verified | [Install](https://developers.smartsheet.com/ai-mcp/smartsheet/install-the-smartsheet-mcp-server), [Manual config](https://developers.smartsheet.com/ai-mcp/smartsheet/install-the-smartsheet-mcp-server/manually-configure-the-mcp-server) |
| Slack | `https://mcp.slack.com/mcp` | Confidential OAuth: Slack app client id and client secret. | Unverified for this app | [Slack MCP server](https://docs.slack.dev/ai/slack-mcp-server) |
| Box | `https://mcp.box.com` | Admin-created client id, client secret, and an OAuth bearer token. | Unverified for this app | [Setup](https://developer.box.com/guides/box-mcp/setup), [Overview](https://developer.box.com/guides/box-mcp) |
| Vercel | `https://mcp.vercel.com` | OAuth. Streamable HTTP. | Verified | [Vercel MCP](https://vercel.com/docs/agent-resources/vercel-mcp) |
| Cloudflare | `https://mcp.cloudflare.com/mcp` | OAuth, or a Cloudflare API token as a bearer token. | Verified | [Cloudflare MCP servers](https://developers.cloudflare.com/agents/model-context-protocol/cloudflare/servers-for-cloudflare/) |

### Gmail

Doc: [Configure the Gmail MCP server](https://developers.google.com/workspace/gmail/api/guides/configure-mcp-server).

1. Open [Google Cloud Console](https://console.cloud.google.com/) and pick the project you will use.
2. Enable `gmail.googleapis.com` and `gmailmcp.googleapis.com` (APIs & Services → Library, or `gcloud services enable`).
3. Configure the OAuth consent screen for the Google account that will sign in.
4. In Robin, Settings → Plugins → Gmail → **Enable**.
5. Finish the browser consent. Return to the app and tap **Refresh tools** if the list stays empty.
6. Leave write tools off until you want them. Chat will ask before `create_draft` and the other write tools.

Calendar and Drive are the same clicks with these services and URLs:

- Calendar: `calendarmcp.googleapis.com`, URL `https://calendarmcp.googleapis.com/mcp/v1`.
- Drive: `drive.googleapis.com` and `drivemcp.googleapis.com`, URL `https://drivemcp.googleapis.com/mcp/v1`. [Drive guide](https://developers.google.com/workspace/drive/api/guides/configure-mcp-server).

### GitHub

Doc: [remote-server.md](https://github.com/github/github-mcp-server/blob/main/docs/remote-server.md).

1. Settings → Plugins → GitHub.
2. Either leave the token blank and tap **Enable** to start OAuth, or paste a personal access token and tap **Enable**.
3. The token is sent as `Authorization: Bearer`. GitHub's host guide says a PAT is a valid token for `https://api.githubcopilot.com/mcp/`.
4. Refresh tools. The default toolset is the one at that URL. A read-only path (`/readonly`) is a different URL. Add it as a custom server if you want it.

### Notion

Doc: [Get started with Notion MCP](https://developers.notion.com/guides/mcp/get-started-with-mcp).

1. Settings → Plugins → Notion → **Enable**.
2. Complete OAuth in the browser and pick the workspace.
3. On a Business or Enterprise plan you can instead create an MCP token in Notion's developer portal and paste it into **Bearer token** before Enable. The client sends it to `https://mcp.notion.com/mcp`.
4. Refresh tools.

### Smartsheet

Doc: [Install the Smartsheet MCP server](https://developers.smartsheet.com/ai-mcp/smartsheet/install-the-smartsheet-mcp-server).

1. In Smartsheet, create an API token (Account → Apps & Integrations → API Access, or the current "Get started" steps in that doc).
2. Settings → Plugins → Smartsheet.
3. Paste the token. Do not type the word Bearer.
4. Tap **Enable**, then **Refresh tools**.
5. For EU or AU, tap **Add a custom server**, paste `https://mcp.smartsheet.eu` or `https://mcp.smartsheet.au`, choose Bearer, and paste that region's token.

### Slack

Doc: [Slack MCP server overview](https://docs.slack.dev/ai/slack-mcp-server).

The documented URL is `https://mcp.slack.com/mcp`, Streamable HTTP. Slack tells client authors to use a Slack app's `client_id` and `client_secret` (confidential OAuth). This app starts a public PKCE sign-in and does not store a client secret. The row is marked **Unverified**.

1. Create a Slack app and note the client id and client secret from Slack's OAuth page. Slack's doc lists `https://slack.com/oauth/v2_user/authorize` and `https://slack.com/api/oauth.v2.user.access`.
2. Do not expect **Enable** on the Slack row to finish until a confidential client is wired up. If the browser returns an error, that matches this unverified state.
3. A workspace admin may still need to approve the MCP integration. [Slack help](https://slack.com/help/articles/48855576908307-Guide-to-Model-Context-Protocol-in-Slack).

### Box

Doc: [Set up the MCP server](https://developer.box.com/guides/box-mcp/setup).

The documented URL is `https://mcp.box.com`. Box tells you to enable MCP in the Admin Console, copy the integration client id and client secret, and pass an authorization token. This app does not store that client secret. The row is marked **Unverified**.

1. In the Box Admin Console, open Integrations and enable the Box MCP server.
2. Copy the client id, client secret, and the scopes Box lists (`root_readwrite`, `ai.readwrite`, and `docgen.readwrite` if you have Enterprise Advanced).
3. Do not treat **Enable** on the Box row as a finished setup. The public sign-in path was not confirmed against Box's admin credentials.

### Vercel

Doc: [Use Vercel MCP](https://vercel.com/docs/agent-resources/vercel-mcp).

1. Confirm the URL is exactly `https://mcp.vercel.com`.
2. Settings → Plugins → Vercel → **Enable**.
3. Finish the browser OAuth consent and grant the teams you want.
4. Refresh tools. The account's own permissions still apply.

### Cloudflare

Doc: [Cloudflare's own MCP servers](https://developers.cloudflare.com/agents/model-context-protocol/cloudflare/servers-for-cloudflare/).

1. Settings → Plugins → Cloudflare.
2. Tap **Enable** and finish OAuth, or paste an API token into **Bearer token** first.
3. Account tokens need **Account Resources: Read** so the server can see the account. Tokens with client IP filtering are not supported by Cloudflare's server.
4. Refresh tools.

## Your apps

checkit, FMFlow, Gridwell, and Loughnane Lifts are marked **Needs server**. The URL field starts empty. The placeholder is `https://your-server.example/mcp`. This app does not start those servers and does not guess a localhost port.

When one of them actually serves MCP:

1. It must accept Streamable HTTP (or SSE if you pick SSE on a custom server).
2. It must answer `initialize`, `tools/list`, and `tools/call`.
3. Settings → Plugins, paste that URL, optional bearer token, then **Enable**.
4. Refresh tools. Read tools start on. Write tools start off.

**Enable** stays disabled until the URL is not blank.

## Hermes handoff

Hermes Agent stays on the Mac (`~/.hermes`, profile `cbre-admin`). The phone calls the API server, not an MCP `delegate` tool.

1. On the Mac, bind the API server to the Tailscale address and port **8642**, with `API_SERVER_KEY` at least 16 characters. The steps are in `docs/HERMES.md` on branch `cursor/hermes-direct-send-9650`.
2. In Robin, Settings → Hermes (that screen ships with direct send): endpoint `http://<tailscale-ip>:8642`, the same API key, profile `cbre-admin`.
3. In a chat, the model can call `delegate_task` with `target_id` `hermes`. The app reads those settings, probes `/v1/models` (or `/p/cbre-admin/v1/models`), and posts `POST {apiRoot}/chat/completions` with the task. The streamed reply is the tool result in the same thread.
4. `127.0.0.1:8642` from the phone is the phone. Use the Tailscale address.

Other `delegate_task` targets are installed chat platforms, matched by uid or name.

## Routines

1. Settings → Routines → **+**.
2. Name, prompt, platform, and daily, weekly, or a 5-field cron expression.
3. Plugin ids are optional. Blank means no plugin tools. Read-only tool names are a separate allowlist and start empty, so a routine calls no MCP tools until you name them. Write and send tools never run. The reply is a draft in the routine chat.
4. **Run now** posts a notification and a chat titled `Routine: …`.

## Room

Version 20, migration `11 → 20`, creates only plugin and routine tables. When versions 12–19 are on main, retarget the migration to start at 19.

## Tests

- `McpSdkMockWebServerTest` — official SDK Streamable HTTP client.
- `PluginOAuthFlowTest` — discovery, registration, PKCE material for AppAuth.
- `RoutineScheduleTest` — next daily, weekly, and cron slot.
- `ToolConfirmationGateTest` — write confirmation and unattended denial.
- `HermesAgentBridgeTest` — handoff uses the saved API server settings and does not call MCP. Missing settings fail before any request.
- `PluginPresetRowInstrumentedTest` — Enable on the Gmail row.
