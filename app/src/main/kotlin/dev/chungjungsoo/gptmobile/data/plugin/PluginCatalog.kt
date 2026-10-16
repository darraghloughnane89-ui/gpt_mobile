package dev.chungjungsoo.gptmobile.data.plugin

object PluginTransport {
    const val STREAMABLE_HTTP = "STREAMABLE_HTTP"
    const val SSE = "SSE"
}

object PluginAuth {
    const val NONE = "NONE"
    const val BEARER = "BEARER"
    const val OAUTH = "OAUTH"
}

object PluginStatus {
    const val CONNECTED = "CONNECTED"
    const val NEEDS_AUTH = "NEEDS_AUTH"
    const val ERROR = "ERROR"
    const val DISCONNECTED = "DISCONNECTED"
}

object PluginScopeType {
    const val BOT = "BOT"
    const val PROJECT = "PROJECT"
    const val PLATFORM = "PLATFORM"
}

enum class PluginGroup {
    OFFICIAL,
    PERSONAL
}

data class PluginPreset(
    val id: String,
    val name: String,
    val endpointUrl: String,
    val transport: String,
    val authType: String,
    val allowsBearerFallback: Boolean,
    val requiresUserToken: Boolean,
    val allowCleartext: Boolean,
    val configurableUrl: Boolean,
    val authNote: String,
    val group: PluginGroup
)

object PluginCatalog {
    const val HERMES_ID = "hermes"
    const val HERMES_TARGET = "hermes"
    const val HERMES_PROFILE = "cbre-admin"
    const val HERMES_DELEGATE_TOOL = "delegate"

    val gmail = official(
        id = "gmail",
        name = "Gmail",
        endpointUrl = "https://gmailmcp.googleapis.com/mcp/v1",
        authType = PluginAuth.OAUTH,
        authNote = "Official Google Workspace remote MCP. OAuth consent. Enable the Gmail MCP API in the user's Google Cloud project. No pasted API key."
    )
    val calendar = official(
        id = "google-calendar",
        name = "Google Calendar",
        endpointUrl = "https://calendarmcp.googleapis.com/mcp/v1",
        authType = PluginAuth.OAUTH,
        authNote = "Official Google Calendar remote MCP. OAuth consent. Enable the Calendar MCP API in the user's Google Cloud project."
    )
    val drive = official(
        id = "google-drive",
        name = "Google Drive",
        endpointUrl = "https://drivemcp.googleapis.com/mcp/v1",
        authType = PluginAuth.OAUTH,
        authNote = "Official Google Drive remote MCP. OAuth consent. Enable the Drive MCP API in the user's Google Cloud project."
    )
    val github = official(
        id = "github",
        name = "GitHub",
        endpointUrl = "https://api.githubcopilot.com/mcp/",
        authType = PluginAuth.OAUTH,
        allowsBearerFallback = true,
        authNote = "Official GitHub remote MCP. OAuth, or a personal access token sent as a bearer token. A user token is required."
    )
    val notion = official(
        id = "notion",
        name = "Notion",
        endpointUrl = "https://mcp.notion.com/mcp",
        authType = PluginAuth.OAUTH,
        authNote = "Official Notion remote MCP. OAuth connects a Notion workspace. A user grant is required."
    )
    val smartsheet = official(
        id = "smartsheet",
        name = "Smartsheet",
        endpointUrl = "https://mcp.smartsheet.com",
        authType = PluginAuth.BEARER,
        allowsBearerFallback = true,
        authNote = "Official Smartsheet remote MCP for the US region (EU: https://mcp.smartsheet.eu, AU: https://mcp.smartsheet.au). Paste an API token. Smartsheet OAuth is used by some clients; this app sends the token as a bearer."
    )
    val slack = official(
        id = "slack",
        name = "Slack",
        endpointUrl = "https://mcp.slack.com/mcp",
        authType = PluginAuth.OAUTH,
        authNote = "Official Slack remote MCP. OAuth consent for the workspace. A user token is required."
    )
    val box = official(
        id = "box",
        name = "Box",
        endpointUrl = "https://mcp.box.com",
        authType = PluginAuth.OAUTH,
        authNote = "Official Box remote MCP. OAuth consent. A user token is required."
    )
    val vercel = official(
        id = "vercel",
        name = "Vercel",
        endpointUrl = "https://mcp.vercel.com",
        authType = PluginAuth.OAUTH,
        authNote = "Official Vercel remote MCP. OAuth consent. A user token is required."
    )
    val cloudflare = official(
        id = "cloudflare",
        name = "Cloudflare",
        endpointUrl = "https://mcp.cloudflare.com/mcp",
        authType = PluginAuth.OAUTH,
        allowsBearerFallback = true,
        authNote = "Official Cloudflare remote MCP. OAuth, or an API token sent as a bearer token. A user token is required."
    )

    val hermes = personal(
        id = HERMES_ID,
        name = "Hermes",
        endpointUrl = "http://127.0.0.1:8765/mcp",
        authNote = "Hermes Agent on this Mac (~/.hermes, profile cbre-admin). Point the URL at the stream (i) HTTP bridge. Cleartext is allowed for a local address."
    )
    val checkit = personal(
        id = "checkit",
        name = "checkit",
        endpointUrl = "http://127.0.0.1:8781/mcp",
        authNote = "checkit MCP bridge. Set the URL and optional bearer token. This app does not run the checkit server."
    )
    val fmflow = personal(
        id = "fmflow",
        name = "FMFlow",
        endpointUrl = "http://127.0.0.1:8782/mcp",
        authNote = "FMFlow MCP bridge. Set the URL and optional bearer token. This app does not run the FMFlow server."
    )
    val gridwell = personal(
        id = "gridwell",
        name = "Gridwell",
        endpointUrl = "http://127.0.0.1:8783/mcp",
        authNote = "Gridwell MCP bridge. Set the URL and optional bearer token. This app does not run the Gridwell server."
    )
    val loughnaneLifts = personal(
        id = "loughnane-lifts",
        name = "Loughnane Lifts",
        endpointUrl = "http://127.0.0.1:8784/mcp",
        authNote = "Loughnane Lifts MCP bridge. Set the URL and optional bearer token. This app does not run that server."
    )

    val officialPresets = listOf(gmail, calendar, drive, github, notion, smartsheet, slack, box, vercel, cloudflare)
    val personalPresets = listOf(hermes, checkit, fmflow, gridwell, loughnaneLifts)
    val all = officialPresets + personalPresets

    fun find(id: String): PluginPreset? = all.firstOrNull { it.id == id }

    private fun official(
        id: String,
        name: String,
        endpointUrl: String,
        authType: String,
        authNote: String,
        allowsBearerFallback: Boolean = false
    ) = PluginPreset(
        id = id,
        name = name,
        endpointUrl = endpointUrl,
        transport = PluginTransport.STREAMABLE_HTTP,
        authType = authType,
        allowsBearerFallback = allowsBearerFallback,
        requiresUserToken = true,
        allowCleartext = false,
        configurableUrl = false,
        authNote = authNote,
        group = PluginGroup.OFFICIAL
    )

    private fun personal(
        id: String,
        name: String,
        endpointUrl: String,
        authNote: String
    ) = PluginPreset(
        id = id,
        name = name,
        endpointUrl = endpointUrl,
        transport = PluginTransport.STREAMABLE_HTTP,
        authType = PluginAuth.BEARER,
        allowsBearerFallback = true,
        requiresUserToken = false,
        allowCleartext = true,
        configurableUrl = true,
        authNote = authNote,
        group = PluginGroup.PERSONAL
    )
}
