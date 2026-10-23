---
name: trackstar-screen
description: >-
  Conventions for building a new screen, page, or sheet in the Trackstar apps
  (iOS SwiftUI and Android Jetpack Compose) so every screen looks and behaves
  consistently: the large-title-that-collapses-to-inline nav bar, the correct
  left/right/top/bottom paddings, the circular "glass" nav-bar buttons, and the
  shared app background. USE THIS whenever you add or restyle a screen, view,
  page, sheet, bottom-sheet, modal, or nav/top bar in trackstar-ios or
  trackstar-android — even if the user only mentions a header, a title, paddings,
  spacing, safe areas, or "make it match the other screens". Reach for it before
  writing a new SwiftUI `View`/`.sheet` or Compose screen/`ModalBottomSheet`, and
  whenever a header doesn't scroll/collapse like the rest of the app.
---

# Trackstar screen conventions

Trackstar screens share one visual system: a **large title that scrolls up and
collapses into an inline nav-bar title**, a dark **app background**, **16pt side
gutters**, circular **glass** icon buttons, and bottom padding that clears the
floating tab bar / action buttons. Follow this so a new screen feels native to
the app instead of a one-off. Match the nearest existing screen's spacing rather
than inventing new numbers.

Pick the platform section. When you build the same screen on both, keep the
titles, paddings, and behavior identical so the apps stay at parity.

---

## iOS (SwiftUI)

**There are ready-made components — use them, don't reinvent the header.**
`Trackstar/UI/UIComponents/CollapsingNavBar.swift` defines:

- `CollapsingNavBar(title:collapseProgress:leadingIcon:onBack:) { trailing }` —
  the sticky top bar. Put it in `.safeAreaInset(edge: .top)`. It shows a leading
  glass circle button (default `chevron.left`; pass `"xmark"` for a sheet), the
  inline title that **fades in** as you scroll, and your trailing buttons. There's
  a `where Trailing == Color` overload so you can omit the trailing closure.
- `CollapsingLargeTitle(title:collapseProgress:)` — the 30pt large title. Put it
  as the **first item** in the ScrollView content; it **fades out** as you scroll.

The two are driven by one `collapseProgress` (0 → 1) you compute from scroll
offset. This is the whole trick: large title and inline title cross-fade on the
same progress value.

### Screen recipe (copy this)

```swift
struct MyThingView: View {
    var onBack: (() -> Void)?

    @State private var scrollOffset: CGFloat = 0
    @State private var topInset: CGFloat = 0
    // 0 at rest, 1 once the large title (~60pt) has scrolled away.
    private var collapseProgress: CGFloat { min(1, max(0, (scrollOffset + topInset) / 60)) }
    private struct ScrollMetrics: Equatable { let offset: CGFloat; let insetTop: CGFloat }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                CollapsingLargeTitle(title: "My Thing", collapseProgress: collapseProgress)
                // optional subtitle
                Text("What this screen is for.")
                    .font(.system(size: 14)).foregroundStyle(.white.opacity(0.45))
                    .padding(.horizontal, 16).padding(.top, 4).padding(.bottom, 20)

                // content — every row/card gets .padding(.horizontal, 16)
                VStack(spacing: 10) { /* rows */ }.padding(.horizontal, 16)
            }
            .padding(.bottom, 40)                      // clears the floating tab bar / Today button
        }
        .onScrollGeometryChange(for: ScrollMetrics.self, of: {
            ScrollMetrics(offset: $0.contentOffset.y, insetTop: $0.contentInsets.top)
        }, action: { _, new in scrollOffset = new.offset; topInset = new.insetTop })
        .safeAreaInset(edge: .top, spacing: 0) {
            CollapsingNavBar(title: "My Thing", collapseProgress: collapseProgress, onBack: { onBack?() }) {
                Button { /* action */ } label: {
                    Image(systemName: "plus").font(.system(size: 16, weight: .semibold)).glassCircle()
                }
            }
        }
        .appBackground()
        .navigationBarHidden(true)
    }
}
```

Real references to mirror: `UI/View/MainApp/Attendance/GymsView.swift`,
`Profile/PersonalInfoView.swift`, `Coach/TemplatesView.swift`.

### Sheet / modal recipe

A `.sheet` uses the *same* collapsing pattern — the title still scrolls up. The
only differences: the nav bar's leading button is a **close X**, and you present
with a detent. Store the sheet's own `@State` scroll offset (don't reuse the
parent's).

```swift
.sheet(isPresented: $showThing) {
    mySheet
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)   // we provide the X ourselves
}
```

Inside `mySheet`, build it exactly like the screen recipe but pass
`leadingIcon: "xmark", onBack: { showThing = false }` to `CollapsingNavBar`.
`Add widget` in `MyWorkoutView.swift` (`widgetGallerySheet`) is a worked example.

### iOS values & building blocks

- **Side gutter:** `.padding(.horizontal, 16)` on every row/card and the large
  title (the component already does 16).
- **Bottom clearance:** `.padding(.bottom, 40)` on scroll content (more — ~80 —
  when a floating "Today"/action button sits above the tab bar).
- **Background:** `.appBackground()` on the outermost view (variant:
  `.appBackground(slot: N)`). Never hand-roll a gradient + `.ignoresSafeArea()`.
- **Nav-bar icon buttons:** `Image(systemName: …).glassCircle()` — never a manual
  `Circle()`. Back = `chevron.left`, close = `xmark`.
- **Collapse threshold:** divide by `60` (list screens) or `80` (screens with a
  taller header); keep it consistent with the sibling screen you're matching.
- **Large title:** 30pt bold; inline title: 17pt semibold (both inside the
  components — don't restyle).
- **Accent:** `(AppTheme(rawValue: themeRaw) ?? .midnight).accentColor` via
  `@AppStorage(AppTheme.storageKey)` — use for CTAs only, not every label.
- **Card fill:** `.white.opacity(0.06)` with `RoundedRectangle(cornerRadius: 20,
  style: .continuous)`.
- `.navigationBarHidden(true)` — the app draws its own bars, never UIKit's.

---

## Android (Jetpack Compose)

There is **no shared collapsing-header composable yet** — each screen inlines the
pattern (`DietScreen.kt`, `stats/StatsScreen.kt`, `stats/HistoryScreen.kt`,
`workout/WorkoutScreen.kt`). Copy the closest one rather than writing a header
from scratch; if you build several new screens, factor a shared
`CollapsingHeader` composable and migrate them.

### The pattern

A `LazyColumn` (or `verticalScroll` Column) whose **first item is the large
title**. Track how far it has scrolled off via `listState.layoutInfo` to get a
`collapse` fraction (0 → 1), then fade the large title out and an inline header
in. The header is an **overlay** (so content scrolls under it) that gains an
opaque "frost" fill once docked.

```kotlin
val listState = rememberLazyListState()
val collapse by remember {
    derivedStateOf {
        val info = listState.layoutInfo.visibleItemsInfo
        val title = info.firstOrNull { it.index == 0 }
            ?: return@derivedStateOf if (info.isEmpty()) 0f else 1f
        val h = title.size.toFloat()
        if (h <= 0f) 0f else ((-title.offset).toFloat() / h).coerceIn(0f, 1f)
    }
}
```

### Android values & conventions

- **Root insets:** `Modifier.statusBarsPadding()` on the screen's root Column;
  `Modifier.navigationBarsPadding()` on anything pinned to the bottom.
- **Side gutter:** `Modifier.padding(horizontal = 16.dp)` (floating bottom bars
  use `15.dp`).
- **Bottom clearance:** leave room for the floating tab bar via
  `tabBarContentBottomPadding()` (see `WorkoutScreen.kt`), or a `bottom = 92.dp`
  (150.dp when a mini-bar is present) pad for bottom-aligned pills.
- **Nav/header icon buttons:** `GlassCircleIconButton(...)` and
  `ProfileAvatarButton(...)` — the Compose equivalents of iOS's glass circle.
- **Card fill:** `CardFill = Color.White.copy(alpha = 0.06f)` with
  `RoundedCornerShape(20.dp)`.
- **Accent / background:** `TrackstarAccent`, `TrackstarBackground` from
  `ui/theme`. Header "frost" = `HeaderTint` composited over the theme gradient.
- A floating bottom pill (Today / Add widget) is an `AnimatedVisibility` aligned
  `BottomCenter` with `navigationBarsPadding()`; stack multiple by increasing the
  lower one's `bottom` padding.

---

## Before you finish — checklist

- [ ] Large title scrolls up and the inline nav-bar title fades in (not a static header).
- [ ] 16pt side gutters on every row/card and the title.
- [ ] Bottom padding clears the floating tab bar / action buttons (nothing hidden behind them).
- [ ] Back button is a glass circle `chevron.left`; a sheet's close is `xmark`.
- [ ] App background applied once on the outermost view; the app's own bars, not the system's.
- [ ] Same titles, paddings, and behavior on iOS and Android when the screen exists on both.
