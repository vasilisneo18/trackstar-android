# Changelog

All notable changes to the Trackstar Android app are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Entries are keyed by Play **versionCode** (with the user-facing versionName in
parentheses). Newest first.

## [Unreleased]

## [30] (1.2.0) — 2026-11-20
_iOS 1.2.0 parity: activities, slopes and groups. Needs the Play declarations for
background location (recording), ACTIVITY_RECOGNITION (step sensor) and Health
Connect exercise / steps. Release notes for the store: below._

**Play release notes (en-US, 407/500):**
```
• Record runs, walks, hikes & rides with GPS: live map, pace, splits, elevation, steps
• Ski & snowboard: runs, lifts, vertical, top speed
• Groups: friends on a live map + leaderboard
• Share activities as images
• 870 exercises with photos
• Add exercises mid-workout, skip sets, set notes
• Recordings & workouts survive the app closing
• Customise your Home
• Now in Greek
• Free plan: 5 workouts a week
```
**(el-GR, 435/500):**
```
• Καταγραφή τρεξίματος, περπατήματος, πεζοπορίας & ποδηλάτου με GPS: χάρτης, ρυθμός, υψομετρικά, βήματα
• Σκι & snowboard: κατεβάσεις, αναβατήρες, μέγιστη ταχύτητα
• Ομάδες: φίλοι σε ζωντανό χάρτη + κατάταξη
• Κοινοποίηση δραστηριοτήτων ως εικόνα
• 870 ασκήσεις με φωτογραφίες
• Πρόσθεση ασκήσεων, παράλειψη σετ, σημειώσεις
• Οι καταγραφές σώζονται αν κλείσει η εφαρμογή
• Προσαρμογή Αρχικής
• Ελληνικά
• Δωρεάν: 5 προπονήσεις/εβδομάδα
```
### Added
- **GPS activities** — run / walk / hike / ride recorder on Google Maps with a
  foreground service (keeps recording with the screen off): distance, pace, 1 km
  splits, elevation (barometer when present). Steps counted by the phone's step
  sensor during the recording, saved with the activity; Health Connect as the
  fallback. Saved to Health Connect as an exercise session. Detail screen with route,
  splits, elevation profile, start / end time, steps and cadence.
- **Slopes** — ski / snowboard: runs vs lifts, vertical, top speed; piste maps and a
  bundled resort browser.
- **Hiking** — climb, descent, altitude, terrain map (trail browser behind the remote
  switch, off for now).
- **Groups** — QR / `trackstar://group/` links, live map while recording,
  leaderboard. Creating needs Bronze; joining is free.
- **Share an activity** as an image (same icon as iOS).
- **Exercise library** — about 870 exercises with photos and a muscle filter.
- **Live workout / Quick Log** — add an exercise mid-session (also added to the plan),
  skip sets / exercises, a note per set (Log Set + superset sheets), correct finished
  sets.
- **Recovery** — the recording (every ~10 s) and the live workout (on every change)
  are saved to the phone (`DraftStore`); after the app is closed the recording
  reopens paused and the workout returns to the mini-bar.
- **Home widgets** add / remove gallery; Diet as a Home card.
- **Greek** localization (`assets/i18n/el.json`); Accept-Language to the backend.
- **Log-in warning** before a fourth device logs out the oldest (`DeviceLimitPrompt`).
### Changed
- Free plan 5 sessions / week, 14-day history; Silver €4.99; no template cap on Gold;
  piste / trail maps are a paid feature.
- The minimized workout lives app-wide (`ActiveWorkout`), so it survives opening
  Profile / Diet / History; system Back minimizes it.
- Loading buttons: the shared spinner + "Saving…"-style text.
### Fixed
- A login rejected mid-recording no longer throws away the activity or the workout
  (`SignOutGuard`); a workout finished while signed out is queued and sent after the
  next log-in (the outbox kept it instead of dropping it on 401 / 403).
- Discard Session / minimize of the live workout when another screen opened on top.
- Stale unit tests brought up to date — all 161 pass.

## [27] (1.1.0) — 2026-09-14
_Ports the iOS 1.1.0 feature set. Requires the backend attendance endpoints in
production + Play declarations for Health Connect and location. Full detail in
`logs/v27.md`._
### Added
- **Apple Health steps (Health Connect)** — daily cumulative-curve card on the
  workout home (follows the swiped day) and a weekly bar chart on Stats with
  tap-to-inspect.
- **Workout home dashboard** — expandable session cards; empty-day "Plan your
  session" + one-tap copy-last-week; past-day steps / "No data" states; long-press
  pin; "Book a session" card with the coach's next open slots; "Today's booked
  session".
- **Gym attendance / check-ins (athlete)** — scan a gym QR (geofence) or coach
  session code to check in/out, month-grouped history, branded PDF export with
  in-app preview. Reached from Profile → Gym Check-In.
- **Coach attendance tools** — Check-Ins hub (Team tab): rotating session code,
  gym management (add + QR poster + delete), attendance roster grouped by date,
  per-athlete Team Attendance PDF.
### Changed
- **Week identifier pinned to ISO-8601** (Monday-first) so weeks match across
  regions/iOS — fixes Sunday sessions vanishing on US-style locales.
- Booking moved to the dashboard card (removed the nav-bar book button); attendance
  screens use a collapsing large-title; trial copy gated on real eligibility.
### Fixed
- Stale cross-week data (reports on future dates); "copy last week" duplicating on
  double-tap; check-in sending a stale location to the geofence.
### Dependencies
- Added Health Connect, fused location, and Guava (Health Connect ↔ CameraX
  `ListenableFuture` fix).

## [26] (1.0.0) — 2026-08-29
### Changed
- Rebuild to publish a Google Play Billing Library **8.0.0**–compliant bundle
  before the Aug 31 2026 deadline. No functional changes vs. 25 — legacy bundles
  (v24 and earlier) had shipped Billing 7.x via RevenueCat 8.10.1.

## [25] (1.0.0) — 2026-08-24 — first production release
### Added
- **Bronze credits for coaches.** Bronze Grants screen listing athletes the coach
  granted Bronze to ("granted by me"), with swipe-to-revoke (athlete → free, credit
  refunded, RevenueCat entitlement revoked).
- **Grant Bronze on connect.** A branded choice popup ("Grant Bronze / Skip this one")
  shown on all three connection paths — Email, QR scan, and QR "My QR" / Share Link.
  Invite-link joins stamp the invite so the credit is spent when the athlete accepts.
### Fixed
- Session-expiry **logout loop** that pushed the login screen repeatedly:
  `TokenAuthenticator` now signals expiry only once across concurrent 401s, and
  navigation skips re-entry when already at Landing.

---

_Earlier releases (versionCode ≤ 24) predate this changelog._
