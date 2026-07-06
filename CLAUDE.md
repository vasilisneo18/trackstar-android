# Trackstar — Android App

Kotlin + Jetpack Compose Android client for the Trackstar fitness coaching platform. Sibling to `Apps/iOS/trackstar-ios` — see `~/Projects/CLAUDE.md` for how this fits into the wider workspace.

## Status

Freshly scaffolded, not yet built out. `MainActivity` currently just renders a placeholder "Hello, Trackstar!" screen. No networking, persistence, or auth wired up yet.

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
    MainActivity.kt
    ui/theme/          — Color.kt, Theme.kt, Type.kt (Compose theming)
  src/main/res/
    values/            — strings.xml, colors.xml, themes.xml
    mipmap-anydpi-v26/  — adaptive launcher icon (vector, no PNG)
    xml/               — backup_rules.xml, data_extraction_rules.xml
```

## Local dev

```
./gradlew assembleDebug   # build a debug APK
./gradlew tasks           # list available tasks
```

Needs `local.properties` with `sdk.dir=...` pointing at the local Android SDK — gitignored, not committed. Android Studio generates this automatically on first open; if building from the CLI first, create it manually.
