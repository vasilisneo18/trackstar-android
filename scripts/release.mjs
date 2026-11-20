#!/usr/bin/env node
// Release Trackstar Android to Google Play.
//
//   node scripts/release.mjs             build, upload and create a draft release on Production
//   node scripts/release.mjs --dry-run   everything up to the upload (tests, signed .aab); no keys needed
//   node scripts/release.mjs --check     only check the key and show what Play has (changes nothing)
//
//   --track <name>   production (default), beta, alpha or internal
//   --rollout        send the release for review / roll it out instead of leaving a draft
//   --skip-tests     don't run the unit tests
//
// versionName is bumped by hand. versionCode is bumped here when Play already has that code. The
// release notes come from the docs/CHANGELOG.md section for this versionName: the code blocks after
// "en-US" and "el-GR". With a draft, the release goes live from Play Console → Production →
// "Edit release" → "Save and publish".
//
// The key lives outside the repo, in ~/.trackstar-keys (or $TRACKSTAR_KEYS):
//   play-service-account.json   the Google Play service account key (release permission)

import { execFileSync, spawnSync } from 'node:child_process';
import { createPrivateKey, sign } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const GRADLE_FILE = join(ROOT, 'app', 'build.gradle.kts');
const CHANGELOG = join(ROOT, 'docs', 'CHANGELOG.md');
const BUNDLE = join(ROOT, 'app', 'build', 'outputs', 'bundle', 'release', 'app-release.aab');
const PACKAGE = 'com.vasilisneo.trackstar';
const KEYS = process.env.TRACKSTAR_KEYS || join(homedir(), '.trackstar-keys');
const LANGUAGES = ['en-US', 'el-GR'];
const API = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}`;
const UPLOAD_API = `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${PACKAGE}`;

const argv = process.argv.slice(2);
const dryRun = argv.includes('--dry-run');
const rollout = argv.includes('--rollout');
const skipTests = argv.includes('--skip-tests');
const checkOnly = argv.includes('--check');
const track = argv.includes('--track') ? argv[argv.indexOf('--track') + 1] : 'production';
if (!['production', 'beta', 'alpha', 'internal'].includes(track)) {
    console.error(`Unknown track "${track}"`);
    process.exit(1);
}

const step = (s) => console.log(`\n▸ ${s}`);
const fail = (s) => { console.error(`\n✗ ${s}`); process.exit(1); };
const run = (cmd, args) => {
    const r = spawnSync(cmd, args, { cwd: ROOT, stdio: 'inherit' });
    if (r.status !== 0) fail(`${cmd} ${args.join(' ')} failed`);
};
const git = (...a) => execFileSync('git', a, { cwd: ROOT, encoding: 'utf8' }).trim();

// ── Project ─────────────────────────────────────────────────────────────────

function readVersions() {
    const gradle = readFileSync(GRADLE_FILE, 'utf8');
    const code = gradle.match(/^\s*versionCode = (\d+)/m);
    const name = gradle.match(/^\s*versionName = "([^"]+)"/m);
    if (!code || !name) fail('Could not read versionCode / versionName from app/build.gradle.kts');
    return { code: Number(code[1]), name: name[1] };
}

// New versionCode in build.gradle.kts and on the changelog heading ("## [30] (1.2.0)" → "## [31] (1.2.0)").
function setCode(from, to, name) {
    const gradle = readFileSync(GRADLE_FILE, 'utf8');
    writeFileSync(GRADLE_FILE, gradle.replace(/^(\s*versionCode = )\d+/m, `$1${to}`));
    const log = readFileSync(CHANGELOG, 'utf8');
    writeFileSync(CHANGELOG, log.replace(`## [${from}] (${name})`, `## [${to}] (${name})`));
}

// The code blocks after "en-US" and "el-GR" in this versionName's CHANGELOG section.
export function releaseNotes(name) {
    const log = readFileSync(CHANGELOG, 'utf8');
    const start = log.search(new RegExp(`^## \\[\\d+\\] \\(${name.replace(/\./g, '\\.')}\\)`, 'm'));
    if (start < 0) fail(`docs/CHANGELOG.md has no "## [<versionCode>] (${name})" section`);
    const end = log.indexOf('\n## ', start + 1);
    const section = log.slice(start, end < 0 ? undefined : end);
    const notes = {};
    for (const lang of LANGUAGES) {
        const m = section.match(new RegExp(`${lang}[^\\n]*\\n\`\`\`\\n([\\s\\S]*?)\\n\`\`\``));
        if (!m) fail(`The ${name} changelog section has no ${lang} release notes block`);
        notes[lang] = m[1].trim();
        if (notes[lang].length > 500) fail(`${lang} release notes are ${notes[lang].length} characters (Play limit 500)`);
    }
    return notes;
}

function checkGit() {
    if (git('rev-parse', '--abbrev-ref', 'HEAD') !== 'main') fail('Not on main');
    if (git('status', '--porcelain')) fail('Uncommitted changes: commit or stash them first');
    git('fetch', '--quiet', 'origin', 'main');
    if (git('rev-list', '--count', 'HEAD..origin/main') !== '0') fail('main is behind origin/main: pull first');
}

// ── Google Play API ─────────────────────────────────────────────────────────

async function makeClient() {
    const file = join(KEYS, 'play-service-account.json');
    if (!existsSync(file)) fail(`Google Play key missing: put the service account JSON at ${file}`);
    const account = JSON.parse(readFileSync(file, 'utf8'));
    const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
    const now = Math.floor(Date.now() / 1000);
    const body = `${b64({ alg: 'RS256', typ: 'JWT' })}.${b64({
        iss: account.client_email, scope: 'https://www.googleapis.com/auth/androidpublisher',
        aud: account.token_uri, iat: now, exp: now + 3600,
    })}`;
    const jwt = `${body}.${sign('sha256', Buffer.from(body), createPrivateKey(account.private_key)).toString('base64url')}`;
    const res = await fetch(account.token_uri, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: jwt }),
    });
    if (!res.ok) fail(`Google sign-in with the service account failed: ${res.status} ${await res.text()}`);
    const { access_token } = await res.json();

    return async (method, url, body, contentType = 'application/json') => {
        const r = await fetch(url, {
            method,
            headers: { Authorization: `Bearer ${access_token}`, 'Content-Type': contentType },
            body: body === undefined ? undefined : contentType === 'application/json' ? JSON.stringify(body) : body,
        });
        const text = await r.text();
        if (!r.ok) {
            let detail = text;
            try { detail = JSON.parse(text).error.message; } catch {}
            fail(`Google Play ${method} ${url.replace(/^.*applications\/[^/]+/, '')}: ${r.status} ${detail}`);
        }
        return text ? JSON.parse(text) : null;
    };
}

async function highestUploadedCode(api) {
    const edit = await api('POST', `${API}/edits`, {});
    const { bundles = [] } = await api('GET', `${API}/edits/${edit.id}/bundles`);
    const { tracks = [] } = await api('GET', `${API}/edits/${edit.id}/tracks`);
    await api('DELETE', `${API}/edits/${edit.id}`);
    const codes = [
        ...bundles.map((b) => Number(b.versionCode)),
        ...tracks.flatMap((t) => (t.releases || []).flatMap((r) => (r.versionCodes || []).map(Number))),
    ];
    return Math.max(0, ...codes);
}

// ── Main ────────────────────────────────────────────────────────────────────

async function main() {
    let { code, name } = readVersions();
    const notes = releaseNotes(name);
    console.log(`Trackstar Android ${name} (versionCode ${code}) → ${track}${dryRun ? ' — dry run' : ''}`);
    console.log(`  release notes: ${Object.entries(notes).map(([l, t]) => `${l} ${t.length}/500`).join(', ')}`);

    if (checkOnly) {
        const api = await makeClient();
        const edit = await api('POST', `${API}/edits`, {});
        const { tracks = [] } = await api('GET', `${API}/edits/${edit.id}/tracks`);
        const { listings = [] } = await api('GET', `${API}/edits/${edit.id}/listings`);
        await api('DELETE', `${API}/edits/${edit.id}`);
        console.log('  key works');
        for (const t of tracks) {
            for (const r of t.releases || []) console.log(`  ${t.track}: ${r.name || ''} [${(r.versionCodes || []).join(', ')}] ${r.status}`);
        }
        console.log(`  store listing languages: ${listings.map((l) => l.language).join(', ')}`);
        return;
    }

    if (!existsSync(join(ROOT, 'keystore.properties'))) fail('keystore.properties missing: the release bundle would be unsigned');

    let api;
    if (!dryRun) {
        checkGit();
        api = await makeClient();
        step('Checking Google Play');
        const highest = await highestUploadedCode(api);
        if (code <= highest) {
            const next = highest + 1;
            setCode(code, next, name);
            git('commit', '-q', '-am', `versionCode ${next} for ${name}`);
            console.log(`  versionCode ${highest} is already on Play: bumped to ${next}`);
            code = next;
        } else {
            console.log(`  versionCode ${code} is new`);
        }
    }

    if (!skipTests) {
        step('Running unit tests');
        run('./gradlew', ['--quiet', 'testDebugUnitTest']);
    }

    step('Building the release bundle');
    run('./gradlew', ['--quiet', 'clean', 'bundleRelease']);
    const verify = spawnSync('jarsigner', ['-verify', BUNDLE], { encoding: 'utf8' });
    if (!/jar verified/.test(verify.stdout)) fail(`The bundle isn't signed:\n${verify.stdout}${verify.stderr}`);
    console.log(`  signed: ${BUNDLE}`);

    if (dryRun) {
        console.log('\n✓ Dry run done. Nothing was uploaded or committed.');
        return;
    }

    step(`Uploading to Google Play (${track})`);
    const edit = await api('POST', `${API}/edits`, {});
    const uploaded = await api('POST', `${UPLOAD_API}/edits/${edit.id}/bundles?uploadType=media`,
        readFileSync(BUNDLE), 'application/octet-stream');
    console.log(`  uploaded versionCode ${uploaded.versionCode}`);
    // Notes only for the languages the store listing has (Play shows the rest nowhere).
    const { listings = [] } = await api('GET', `${API}/edits/${edit.id}/listings`);
    const languages = LANGUAGES.filter((l) => listings.some((x) => x.language === l));
    await api('PUT', `${API}/edits/${edit.id}/tracks/${track}`, {
        track,
        releases: [{
            name: `${name} (${code})`,
            versionCodes: [String(code)],
            status: rollout ? 'completed' : 'draft',
            releaseNotes: languages.map((language) => ({ language, text: notes[language] })),
        }],
    });
    await api('POST', `${API}/edits/${edit.id}:commit`);
    console.log(`  ${rollout ? 'release sent' : 'draft release created'} with notes in ${languages.join(', ')}`);

    step('Tagging and pushing');
    const tag = `android-${name}-${code}`;
    git('tag', tag);
    git('push', '-q', 'origin', 'main', tag);
    console.log(`  ${tag}`);

    console.log(rollout
        ? `\n✓ ${name} (${code}) sent to ${track}.`
        : `\n✓ ${name} (${code}) is a draft on ${track}. Last step: Play Console → ${track} → Edit release → Save and publish.`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main();
