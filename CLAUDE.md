# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

Build and install the debug APK (from project root):
```
.\gradlew installDebug
```

Build debug APK without installing:
```
.\gradlew assembleDebug
```

Run unit tests:
```
.\gradlew test
```

Run instrumented (on-device) tests:
```
.\gradlew connectedAndroidTest
```

Run a single unit test class:
```
.\gradlew testDebugUnitTest --tests "com.example.notimanager.ExampleUnitTest"
```

Clean build:
```
.\gradlew clean
```

## Architecture Overview

Android app (Kotlin, Jetpack Compose, minSdk 24, targetSdk 36) that intercepts system notifications and re-posts them reorganized into user-defined groups. Dependencies are managed via the version catalog at `gradle/libs.versions.toml` (AGP 9.2.1, Kotlin 2.2.10, Compose BOM 2026.02.01).

### Permission flow

`MainActivity` checks `onResume` whether `BIND_NOTIFICATION_LISTENER_SERVICE` has been granted (via `NotificationManagerCompat.getEnabledListenerPackages`). It also requests `POST_NOTIFICATIONS` at runtime on API 33+. When access is absent it shows `SettingsScreen` (a single button that opens the system notification-listener settings page); once granted it shows `AppBubbleScreen`.

### Data model & persistence (`GroupRepository.kt`)

- **`SavedGroupData`** — serializable snapshot of a group (id, name, description, icon key, dot color as `ULong`, dot scale, center `Offset`, list of package names, `groupingEnabled` flag).
- **`GroupState`** — live Compose state object used in the UI. Holds `mutableStateOf`/`mutableStateListOf` for all editable fields plus in-memory spring physics maps (`positions`, `velocities`, `slots`, `slotOf`, `ownerOf`).
- Persistence is `SharedPreferences` (`"noti_groups"`) serialized as a JSON array. `saveGroups` converts `GroupState → SavedGroupData → JSON`; `loadSavedGroups` reverses it. A separate boolean key (`management_enabled`) controls the global on/off toggle.
- Groups are saved on `Lifecycle.Event.ON_PAUSE` via a `DisposableEffect` in `AppBubbleScreen`.

### Main UI (`AppBubbleScreen.kt`)

A zoomable/pannable canvas (`transformable` + `graphicsLayer`) containing one `BubbleGroupCluster` per group.

**`GroupState` lifecycle in the UI:**
1. On first load, `getInstalledAppsWithUi` fetches all launchable apps (IO dispatcher). Saved groups are restored; any new apps since the last save fall into the `"default"` group.
2. A frame-loop `LaunchedEffect` runs group-separation physics each frame: three passes of push-apart, then center-dot clamping to screen bounds.
3. Each `BubbleGroupCluster` runs its own spring-physics loop (`SPRING = 40f`, `DAMPING = 0.80f`, `DT = 0.016f`) animating app bubbles toward their computed slots.
4. `packBubblesInCircle` places bubbles in concentric rings using a golden-angle-like offset. `CENTER_GAP` controls the gap between the center dot and the first ring.

**Interactions:**
- Drag center dot → moves the whole group (all bubble positions translated together).
- Long-press center dot → opens the edit dialog (name, description, icon, dot size slider, color picker, grouping toggle, delete).
- Drag app bubble → spring physics pauses for that bubble; dropping it inside another group's radius transfers ownership.
- Long-press app bubble → opens Android's app info settings for that package.
- FAB (`+`) → creates a new group via name dialog.
- Top-left `Switch` → toggles `management_enabled` in prefs and cancels all managed notifications if disabled.

**Icons:** Supported group icons: `"audio"`, `"mail"`, `"apartment"`, `"android"`, `"dnd"`, `"heart"`, `"music"`, `"news"`, `"settings"`, `"star"`, `"ic_launcher_foreground"` (see `DRAWABLE_ICONS` in `AppBubbleScreen.kt` and the three `when` blocks in `MyNotificationListener.kt` — `iconEmoji`, `iconLargeBitmap`, `iconCompatFor`). Adding a new icon requires updating all four locations.

### Notification listener (`MyNotificationListener.kt`)

Intercepts every non-self notification. For each incoming `StatusBarNotification`:
1. Looks up which saved group contains the app's package name.
2. If found and `groupingEnabled`, cancels the original notification and re-posts it as two notifications: a **child** (individual content) and a **summary** (group header using a custom `RemoteViews` layout `R.layout.custom_summary_header`).
3. Tracks state in four in-memory maps: `notifKeyToGroupId`, `groupActiveKeys`, `sbnKeyToChildId`, `childIdToSbnKey`. `selfCancelledKeys` prevents the service from reacting to its own cancellations.
4. On removal, if the dismissed notification is one of the app's re-posted children (matched by `sbn.id`), it cleans up tracking. If the original was dismissed externally, it removes the corresponding child and updates or removes the summary.

Avatar bitmaps for the summary are generated at runtime: a colored circle with the group icon drawn centered in white, color cycling through `AVATAR_COLORS` based on the first character of the group name.
