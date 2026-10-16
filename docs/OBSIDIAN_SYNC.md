# Obsidian vault sync

GitHub is the default and primary sync. The phone, the Mac, and Hermes share one free repository. A folder on the phone is a secondary option only. This feature does not use Obsidian Sync or any other paid sync service.

## Vault that was requested

`darraghloughnane89-ui/knowledge-vault` was not readable from this environment. The GitHub API returned 404 for that repository, and it is not in the public repository list for the account. Nothing in this change writes to that repository. Tests use MockWebServer.

Until the repository can be read, notes live under a configurable root, default `Robin/`, so they can sit beside whatever folders are already in the vault. The mapping follows ordinary Obsidian conventions: YAML frontmatter, wiki-links in the note body, and `SKILL.md` for skills.

## Mapping

Root folder defaults to `Robin` and can be changed in settings.

| Kind | Path | Editable from the vault |
| --- | --- | --- |
| Chat | `Robin/Chats/<project>/<yyyy-mm-dd> <title>.md` | Append-aware. New messages come back. A changed existing message is a conflict. |
| Memory | `Robin/Memory/<title>.md` | Yes |
| Skill | `Robin/Skills/<name>/SKILL.md` | Yes. Frontmatter keeps `name` and `description` plus `id`, `type`, `updated`, `tags`, and `links`. |
| Bot | `Robin/Bots/<name>.md` | Yes |
| Project | `Robin/Projects/<name>.md` | Yes |

Frontmatter always includes `id`, `type`, `updated`, `tags`, and `links`. Wiki-links are repeated in a `robin:links` block so Obsidian’s graph can see them. Unknown frontmatter keys are kept.

Chats without a project use the folder `Inbox`.

## Setup

1. Open Settings, then Robin vault.
2. Leave the default repository as `darraghloughnane89-ui/knowledge-vault` and branch `main`, or point it at the real vault.
3. Create a fine-grained personal access token for that repository only:
   - Contents: Read and write
   - Metadata: Read
   - Do not grant Administration
   - Do not allow force-push
4. Paste the token and tap Save and sync. The token is stored in the Android Keystore vault (`obsidian_github_token`), not in the git repo and not in DataStore.
5. On the Mac, clone the same repository and open it as an Obsidian vault. Hermes should read and write the same `Robin/` tree. Sync runs about every 6 hours and from Sync now.

Folder option: choose a directory that is already on the phone. That path does not call a paid sync service. Switch back with Use GitHub instead.

## Conflicts

Keep phone writes the phone copy. Keep vault pulls the vault copy. Keep both unions chat messages (a changed message is kept twice) and, for other notes, appends the vault text under “Vault copy”.

## Why not JGit

Eclipse JGit is EDL (BSD), which a GPL-3.0 app may depend on. It is not used. A local clone still cannot force its way onto `main`: the server rejects a non-fast-forward push, which this client already does with `force: false` on the Git ref update. JGit's line merge can also join two edited chat messages into one broken note. The app compares content hashes and updated times, merges pure chat appends, and asks on the resolve screen for everything else. That stays reliable without a git object database on the phone.

## Room

Sync cursors are a file in app storage (`obsidian_sync_index.json`), not a `chat_v2` migration. Versions 12–18 belong to other streams and are not on this branch. Version 19 stays available if a later merge wants a sync-metadata table.

## Smoke test

- [ ] Settings shows Robin vault and opens the GitHub form with `darraghloughnane89-ui/knowledge-vault`.
- [ ] Saving a token without a network still reports a failed sync and does not crash. The token is not visible in the log.
- [ ] Sync now with a valid token creates or updates `Robin/` files in one commit on the configured branch.
- [ ] A second sync with no edits does not add another commit.
- [ ] Edit a memory in Obsidian, sync, and see the new text in the app once memory storage is connected. Until then the pull is stored in the sync result and the stub repository keeps nothing.
- [ ] Append a message in Obsidian to a chat file, sync, and see the new message on the phone. Edit an older message on both sides and confirm the conflict card appears.
- [ ] Keep phone, Keep vault, and Keep both each clear that card.
- [ ] Turn the phone off the network, confirm the periodic GitHub job waits. Force-push is never sent: a rejected update stays a conflict in the log.
- [ ] Optional: choose a phone folder and confirm files appear there, then switch back to GitHub.
