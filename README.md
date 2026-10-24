# Solis Launcher

A distraction-free Android home screen. Pure AMOLED black, monospace only, no icons, one app at
a time, and friction between you and your impulses.

- Kotlin + Jetpack Compose (Material3), Coroutines/Flow, DataStore Preferences
- MVI: `UI -> LauncherViewModel -> DataStoreManager / AppRepository / system services`
- minSdk 29 (Android 10), targetSdk/compileSdk 35 (Android 15)
- Free: every feature is available to everyone, no purchases or accounts

## Build

Open the folder in Android Studio (Ladybug or newer) or run:

```
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug
./gradlew :app:testDebugUnitTest # pure-Kotlin rules: drawer filter, curfew, quote rotation
```

Install, press Home, and pick Solis (or use the in-app "set as default" prompt).

## Using it

| Where | Gesture | Result |
|---|---|---|
| Home | swipe up | focus drawer (search is focused, one app per page) |
| Home | swipe down | notification shade |
| Home | tap clock / date | alarms / calendar |
| Home | long-press clock | settings |
| Home | tap quote | next quote (otherwise rotates every 6 hours) |
| Drawer | long-press app | app info, uninstall, hide from carousel, launch delay |
| Anywhere | back | closes drawer/settings; never leaves the launcher |

## Project layout

```
app/src/main/java/com/solis/launcher/
  MainActivity.kt              Renders state, forwards actions, performs Activity effects
  SolisApp.kt                  Application + manual dependency container
  domain/                      Pure Kotlin: models, AppFilter, CurfewPolicy, QuoteRotation
  data/DataStoreManager.kt     All persistence: prefs, vault, quotes, metrics, focus session
  data/AppRepository.kt        Installed launchable apps (LauncherApps)
  data/QuoteBank.kt            Default harsh truths
  system/IntentResolver.kt     OEM-agnostic phone/messages/notes/calendar/camera/alarm
  system/FocusAccessibilityService.kt  Lock screen + expand shade, nothing else
  system/PackageChangeReceiver.kt      Dynamic receiver for add/remove/change
  system/LauncherRoleHelper.kt RoleManager.ROLE_HOME handshake
  system/LockscreenQuoteWriter.kt      Renders the quote onto the lock wallpaper
  ui/LauncherContract.kt       UiState / Action / Effect
  ui/LauncherViewModel.kt      The state machine
  ui/SolisRoot.kt              Screen switcher + dialog host
  ui/home, ui/drawer, ui/dialogs, ui/settings, ui/theme
```

## Features (all free)

1. **App vault**: hidden apps leave the carousel but appear when their exact name is typed.
2. **Curfew**: the drawer is closed between two configured times (wraps past midnight).
   Home and the five essentials keep working.
3. **Your own truths**: custom quote editor, plus optional lock-screen wallpaper sync.
4. **Launch delay**: flagged apps wait 5–30 s behind a countdown. Backing out counts as a dodge.
5. **Focus sessions**: close the drawer for 25, 50 or 90 minutes from settings. Home shows the
   time left; the essentials keep working. "end early" in settings stops it.

## Before publishing on Google Play

- **QUERY_ALL_PACKAGES**: fill the permissions declaration form, core use case "launcher".
- **Accessibility**: Solis shows a prominent disclosure before opening accessibility settings.
  You must also complete the Accessibility API declaration in the Play Console and include a
  short video of the lock/shade features. The service config sets `canRetrieveWindowContent="false"`.

## Notes on the spec

- Android has no `GLOBAL_ACTION_EXPAND_NOTIFICATIONS`; the real constant is
  `GLOBAL_ACTION_NOTIFICATIONS`, used here. Without the service, Solis tries the status bar
  service as a fallback, which works on most OEM builds.
- `ACTION_CREATE_NOTE` is API 34+, so it is referenced by string to keep minSdk 29.
- `Intent.ACTION_DELETE` needs `REQUEST_DELETE_PACKAGES`, which is declared.
- The activity also has a `LAUNCHER` filter so Solis can be opened from the old launcher before
  being made the default. Solis hides itself from its own drawer.
- Work profile apps are not listed in v1.

## CI

`.github/workflows/android.yml` runs the unit tests and builds a debug APK on every push to
`main`. The APK is attached to the workflow run as `solis-launcher-debug-apk`.
