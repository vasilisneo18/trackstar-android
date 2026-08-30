# Changelog

All notable changes to the Trackstar Android app are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Entries are keyed by Play **versionCode** (with the user-facing versionName in
parentheses). Newest first.

## [Unreleased]

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
