# Changelog

All notable changes to the Trackstar Android app are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Entries are keyed by Play **versionCode** (with the user-facing versionName in
parentheses). Newest first.

## [Unreleased]

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
