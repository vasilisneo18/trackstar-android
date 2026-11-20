# Releasing to Google Play

One command builds, uploads and prepares the release: `scripts/release.mjs` (Node, no
dependencies).

## Each release

1. Set the new `versionName` in `app/build.gradle.kts`, if this is a new version.
2. Add a `## [<versionCode>] (<versionName>)` section to `docs/CHANGELOG.md` with the Play
   notes as code blocks after a line mentioning `en-US` and one mentioning `el-GR`
   (500 characters each; see the 1.2.0 entry).
3. Commit, then run from the repo root:

   ```
   node scripts/release.mjs
   ```

   It checks you're on an up-to-date, clean `main`, then:
   - bumps `versionCode` (and the changelog heading) if Play already has it, and commits
   - runs the unit tests
   - builds the signed release bundle (needs `keystore.properties`)
   - uploads it as a **draft** release on Production with the English and Greek notes
   - tags `android-<versionName>-<versionCode>` and pushes
4. In Play Console: Production → **Edit release** → **Save and publish**.

Options:
- `--dry-run`: tests and the signed `.aab`, with no upload, commit or key needed.
- `--track internal|alpha|beta`: another track (default `production`).
- `--rollout`: send it straight away instead of leaving a draft.
- `--skip-tests`.

## One-time setup: the service account

Play Console → Setup → API access → create (or link) a Google Cloud service account, create
a JSON key for it, and in Play Console → Users and permissions give it access to Trackstar
with **Release to production** (plus testing tracks). Save the key as
`~/.trackstar-keys/play-service-account.json`, never in the repo.
