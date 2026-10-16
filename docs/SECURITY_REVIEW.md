# Security review: non-phone-control surfaces

Adversarial review of prompt injection, secret leakage, WebView escape, archive import, token scope, and unattended sends. Phone control is out of scope.

Fixes are on the existing pull-request branches. Project import already landed on `main`, so that fix and this note are the follow-up.

## MCP plugins, routines, and agent handoff

Branch: `cursor/mcp-plugins-routines-agents-2e9b`

- A server `readOnlyHint` no longer hides a send or other write. Tool names are split on camel case, and a write verb or `destructiveHint` wins over the hint. Unattended routine runs still refuse write tools.
- `delegate_task` is refused when the run is unattended, and an interactive handoff requires confirmation before Hermes or another agent is called.
- MCP tool text is wrapped as untrusted output so a tool result is not treated as a new instruction to call tools, send, hand off, or reveal secrets.
- OAuth token responses, including the AppAuth exchange, are rejected when the granted scope is wider than the scopes that were requested. An omitted refresh scope is not treated as an upgrade.

Read-only MCP tools still run during routines. That is the point of an unattended read. A routine cannot send or hand off.

## Send to Hermes

Branch: `cursor/hermes-direct-send-9650`

- Handoff text, inlined files, transcript turns, and prior history are redacted (`sk-`, bearer, `x-api-key`, GitHub, and Google key shapes) before they are placed in the chat body.
- The system preface tells the model that the handoff is untrusted data and must not be followed as instructions to call tools, send, change settings, or reveal secrets.
- Attachment reads are limited to the chat attachments directory and `hermes-inbox`. An empty allow-list remains open only for callers that do not supply roots. The delivery worker supplies both roots.

The share sheet is user-initiated. Delivery still runs from the queue the user confirmed. On this branch the Hermes chat client still reads `platform.token`, so Key Manager cannot null that copy without breaking chat. Backup already clears `platform_v2.token`. The plaintext platform copy is a residual until the vault header from the memory branch is on this line.

## Memory, vault, Key Manager, and Drive backup

Branch: `cursor/memory-usage-security-184c`

- Secret redaction now covers GitHub (`ghp_`, `gho_`, `ghu_`, `ghs_`, `ghr_`, `github_pat_`), Google (`AIza`), Slack (`xoxb-` and the other `xox` prefixes), and AWS access-key IDs (`AKIA`), in addition to bearer tokens, `sk-` keys, and API-key headers.
- Facts copied into the prompt are flattened, cut at a `MEMORY_SUGGESTION:` marker, and redacted. The same policy runs on explicit saves, confirmed suggestions, and edits. The suggestion protocol line in the prompt is unchanged. A fact that is only a redacted secret is dropped.
- Key Manager writes a provider secret with `secretVault.put` and stores `token = null` plus `secretRef`. Deleting a platform key also deletes the vault record.
- The staged chat backup still clears provider tokens, and now redacts `messages_v2.content` and `memories.text` when those columns exist.
- The local JSON export and the encrypted Drive archive redact message text, thoughts, timelines, system prompts, and memory facts. Platform tokens and secret refs are left out of the archive. Skill and import writes use the same fact sanitizer as explicit memory.

Vault files stay in `noBackupFilesDir`. Message text can still hold a secret that does not match these patterns. Suggestions are saved only after `confirmMemorySuggestion`.

## Artifact preview

Branch: `cursor/chat-library-artifacts-467f`

- Preview markup strips script, frames, event handlers, `javascript:` URLs, and breakout tags (`meta`, `base`, `link`, `form`, `foreignObject`, `html`, `head`, `body`). URL attributes are entity-decoded and removed unless they are empty, a fragment, `about:`, or `data:`.
- The document the app controls adds `Content-Security-Policy: default-src 'none'`.
- The WebView keeps JavaScript off, file and content access off, and network loads blocked. `shouldOverrideUrlLoading` blocks navigations. `shouldInterceptRequest` allows only `about:` and `data:` resources, including the `about:blank` document load.

## Obsidian sync

Branch: `cursor/obsidian-vault-sync-68fa`

- A path is inside the vault only when it has no empty, `.`, or `..` segment, no leading slash, and no colon, and the normalized path is the vault root or a child of it. `Robin/../.github/workflows/pwn.md` is outside `Robin`.
- GitHub and local commits refuse any file outside the vault before writing. GitHub error text replaces the token so a response cannot echo it. Blob and local note bodies are redacted for `sk-`, bearer, GitHub, and Google key shapes.

Bot notes pulled from the vault are instructions by design. A collaborator who can write the vault can change those notes. Path confinement stops that write from landing in `.github/workflows` outside the vault folder.

## Project import

Branch: `cursor/security-review-1d25` (archive import is already on `main`, so this branch cannot be updated in place)

- Every zip entry counts toward the entry cap. Unsafe entries are still read under the per-entry byte cap, then the archive is rejected as unreadable. A parent-directory path is not skipped and then inflated with `closeEntry`.
- Re-export writes a file under `files/` only when that relative path is safe. Otherwise the display name is sanitized into `files/`.
- Stored file names reject blank, `.`, `..`, separators, and a canonical path that leaves the project directory.
- Project file text is prefixed, inside the context budget, with a notice that the files are untrusted data and are not instructions, tool grants, or permission to send.

## Residuals

- Hermes `platform.token` stays populated on the Hermes branch because that chat client does not read `secretRef`.
- Read-only plugin tools still execute inside routines.
- Vault bot instructions are applied as instructions.
- Redaction is pattern-based. A secret that does not match these shapes can still sit in a chat, a memory, or a backup.
- The share sheet and phone control were not part of this pass.
