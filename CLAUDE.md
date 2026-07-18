# Trackstar — Android App

Kotlin + Jetpack Compose Android client for the Trackstar fitness coaching platform. Sibling to `Apps/iOS/trackstar-ios` — see `~/Projects/CLAUDE.md` for how this fits into the wider workspace.

## Status

Substantially built out — the app is at versionCode 8 (versionName 1.0.0) and has been on real devices. Networking, auth/token refresh, and the bulk of the iOS feature set are wired up:

- **Auth & onboarding** — landing, login, forgot-password, and the full multi-step registration flow (email → password → personal details → body metrics → fitness profile → goals).
- **Athlete side** — weekly plan, session editing (incl. supersets via `CompoundExercisePairSheet`), active workout logging (single + compound sets, quick log, mini-bar that outlives the screen like iOS), stats/history/session reports/exercise progress, diet tracking + AI Diet Planner, AI Workout Planner, and the My Coach flow.
- **Coach side** — athlete roster, athlete detail (plan/diet/progress/profile tabs), templates + template editor, apply-to-athlete, and the invite flow (QR connect/scan, accept-invite sheet, deep links).
- **Billing** — `BillingManager` is a **real** RevenueCat integration (live offerings/prices, purchase, restore, entitlement→plan mapping), not a stub. It no-ops gracefully until an Android RevenueCat key is provided (see below), so the app runs fully without it.

### Remaining vs iOS

- **Missed-session sheet** — iOS has `MissedSessionSheet`; Android's `WorkoutViewModel.SessionDisplay` deliberately omits the `.missed` case. Not modeled yet.
- **Interstitial ads** — iOS has `InterstitialAdManager` (AdMob); Android has no ads integration.
- **RevenueCat go-live** — the code is done; what's missing is *configuration*: set `revenuecat.apiKey=goog_...` in `local.properties` (or the `REVENUECAT_API_KEY` env var for CI) and configure the offering/entitlements in the RevenueCat dashboard + Play Console. Until the key is set, `BillingManager.isConfigured` stays false and billing is a no-op (plan = FREE).

## Stack

- Kotlin, Jetpack Compose (Material 3), no XML views
- Gradle Kotlin DSL (`build.gradle.kts`), version numbers declared directly in each module's build file — no version catalog (`libs.versions.toml`) yet, add one if/when dependency count grows enough to justify it
- `compileSdk`/`targetSdk` = 36, `minSdk` = 26 (matches installed local SDK platform; adaptive icons require 26+ so no legacy PNG mipmap fallback is needed)
- `applicationId` = `com.vasilisneo.trackstar` — clean name, no legacy constraint like iOS has with `com.vasilisneo.metalion`

## Backend

Talks to the same Spring Boot API as the iOS app (`Backend/fitness-book-api`, production at `https://api.trackstar.fitness`). No separate/duplicate backend — see the iOS app's `CLAUDE.md` for the API's shape (auth flow, endpoints, DTOs) since it documents the same API this app will call. Prefer reading real request/response DTOs from the backend repo directly over guessing at payload shapes.

## Design parity with iOS

The iOS app (`Apps/iOS/trackstar-ios`) is the reference for product behavior — coach/athlete roles, weekly workout plans (including supersets — two exercises paired back-to-back, see `CompoundExercisePairSheet`/`CompoundExerciseView` naming in iOS even though the user-facing label is "Superset"), diet tracking, AI-generated plans, RevenueCat subscriptions, coach invites via QR/deep link. When building a feature here, check how iOS does it first rather than redesigning from scratch — the two apps should feel like the same product.

Trackstar's brand color (`#2E80FF` accent on `#0D0D17` background) is set up in `ui/theme/Color.kt` to match iOS's default "Midnight" theme.

## Project layout

```
app/
  src/main/java/com/vasilisneo/trackstar/
    MainActivity.kt        — single-activity host, Compose nav graph
    TrackstarApplication.kt — app init (configures BillingManager/RevenueCat)
    data/
      api/                 — Retrofit APIs (Auth, Plan, Session, Diet, Ai, Template,
                             Athlete, Comment, Profile), Dtos, NetworkClient, TokenAuthenticator
      auth/                — AuthRepository, ProfileRepository, TokenStore, ApiResult
      billing/             — BillingManager (RevenueCat), AppPlan, FeatureGate
      workout/             — repositories (Plan, Session, Diet, Ai, Template, Athlete, Comment)
                             + ExerciseGrouping
    ui/
      components/          — shared Compose (auth, main, settings, QR scanner, pickers, drag-reorder)
      screens/             — landing, login, register, subscription, and main/ (plan, workout,
                             diet, stats, coach, settings, profile)
      theme/               — Color.kt, Theme.kt, Type.kt, AppTheme.kt
      util/                — Prefs
  src/main/res/
    values/                — strings.xml, colors.xml, themes.xml
    mipmap-anydpi-v26/     — adaptive launcher icon (vector, no PNG)
    xml/                   — backup_rules.xml, data_extraction_rules.xml
```

No DI framework (no Hilt/Koin) and no version catalog yet — repositories/managers are wired manually and dependency versions live in `app/build.gradle.kts`.

## Local dev

```
./gradlew assembleDebug   # build a debug APK
./gradlew tasks           # list available tasks
```

Needs `local.properties` with `sdk.dir=...` pointing at the local Android SDK — gitignored, not committed. Android Studio generates this automatically on first open; if building from the CLI first, create it manually.
