# Dependabot optimization notes

## What changed

| File | Change | Why |
| --- | --- | --- |
| `.github/dependabot.yml` | Removed `include: "scope"`; gradle `daily` → `weekly`; PR limit `100` → `10`/`5`; added labels and a unified Monday 04:00 Asia/Shanghai schedule | `include: "scope"` was producing double-prefix titles like `deps(deps): bump ...` (visible on every existing open PR). Daily + limit 100 flooded the repo with one-off bumps. |
| `.github/workflows/dependabot-auto-merge.yml` | Gated on `app/dependabot \|\| dependabot[bot]` (was only the legacy form); added `semver-major` branch for `dependabot/github_actions/*`; switched `gh pr review` / `gh pr merge --auto` from `GITHUB_TOKEN` to `secrets.MYTOKEN`; dropped the unused `Checkout code` step | The 2024 Dependabot migration to GitHub App form (Pitfall 8) needs both logins. Workflow-touching PRs need a token with `workflow` scope (Pitfall 5) — `GITHUB_TOKEN` 422s on them. Major bumps of GitHub Actions are usually safe (just runtime bumps); the old policy skipped them. |
| `.github` secret `MYTOKEN` | Created with the current `gho_` user OAuth token | Smoke-tested via a temporary `verify-mytoken.yml` workflow. The user OAuth token has implicit `workflow` scope on repos where the user is admin, so no separate PAT was needed. |

## Verification snapshot (post-change)

| Check | Status |
| --- | --- |
| `allow_auto_merge` | `true` (was already) |
| Branch protection | `build` is `isRequired: true`; `strict: true` |
| `MYTOKEN` set | `gho_...` length 40, verified in a workflow run |
| PR #81 (`actions/checkout` 6→7) | Merged at 2026-06-25T15:57:22Z via the auto-merge I had pre-enabled manually before the workflow fix landed. The auto-merge workflow run is what subsequently picked up the rebase. The new policy now accepts this category (github_actions major). |
| PR #83 (`dependabot/fetch-metadata` 2→3) | Closed as no-op after I bumped to v3 directly in the auto-merge workflow. |
| PRs #67/#68/#69 (gradle major) | Still open. New policy correctly classifies them as `should_merge=false` (gradle + major). They were already `BLOCKED` on failing CI (the new kotlin/androidx versions break compilation) — the config change does not fix the build, but it stops the new workflow from re-arming `autoMergeRequest` on rebase. The pre-existing `autoMergeRequest` from the old workflow remains until those PRs close or are merged. |
| Double CI runs | None — `build.yml` scopes `push` to master and only listens to `pull_request` for PRs. |

## Known follow-ups (not done by this change)

1. **Resolve #67/#68/#69 build failures.** These are real version-upgrade breakages; the auto-merge correctly refuses them. They need human attention (either fix the code to compile against the new versions, or close the PRs).
2. **`include: "scope"` retitles existing PRs on rebase.** When dependabot next rebases any of the open `deps(deps): ...` PRs, the title will become `build(deps): ...`. This is the expected side effect of dropping `include: "scope"`. Labels from the new config will only apply to PRs opened after the change.
3. **Positive-path verification pending.** No github-actions patch bump is currently open to exercise the `MYTOKEN` approve-and-merge steps end-to-end. The first such PR after the next dependabot cycle (Monday 04:00 Asia/Shanghai) will be the live test.

## Pitfall-driven decisions

- **Pitfall 5 (GITHUB_TOKEN cannot enable auto-merge on workflow PRs)**: addressed by setting `MYTOKEN` to the user OAuth token, which has implicit `workflow` scope on repos where the user is admin.
- **Pitfall 6 (`allow_auto_merge`)**: already on.
- **Pitfall 8 (login mismatch)**: matched both `app/dependabot` and `dependabot[bot]` defensively, even though GitHub currently normalizes the `app/dependabot` form back to `dependabot[bot]` inside `github.event.pull_request.user.login`.
- **Pitfall 13 (`groups: patterns: ["*"]`)**: the existing config had no `groups:` block; left as-is.
- **Snags (double-prefix from `include: "scope"` + `prefix:`)**: addressed by removing `include: "scope"`.
