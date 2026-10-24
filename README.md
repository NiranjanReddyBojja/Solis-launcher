# Solis Launcher

A distraction-free Android home screen. Black, white and grey only, one monospace font
(JetBrains Mono), no icons, a plain A–Z app list, and friction between you and your impulses.

- Kotlin + Jetpack Compose (Material3), Coroutines/Flow, DataStore Preferences
- MVI: `UI -> LauncherViewModel -> DataStoreManager / AppRepository / system services`
- minSdk 29 (Android 10), targetSdk/compileSdk 35 (Android 15)
- Free: every feature is available to everyone, no purchases or accounts

## Build

Open the folder in Android Studio (Ladybug or newer) or run:

```
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug
./gradlew :app:testDebugUnitTest # pure-Kotlin rules: app list, curfew, quotes, time away, reflections
```

Install, press Home, and pick Solis (or use the in-app "set as default" prompt).

## Using it

| Where | Gesture | Result |
|---|---|---|
| Home | swipe up | app list: every app A to Z under letter headings; tap a letter on the right to jump |
| Home | swipe down | notification shade |
| Home | tap clock / date | alarms / calendar |
| Home | long-press clock | settings |
| Home | tap quote | next quote (otherwise rotates every 6 hours) |
| Home | tap "~ a moment" | box breathing and the daily reflection prompt |
| Home | tap "time away" or the away line | how long you've stayed out of flagged apps |
| App list | long-press app | app info, uninstall, move to the vault, launch delay |
| Anywhere | back | closes any screen; never leaves the launcher |

## Project layout

```
app/src/main/java/com/solis/launcher/
  MainActivity.kt              Renders state, forwards actions, performs Activity effects
  SolisApp.kt                  Application + manual dependency container
  domain/                      Pure Kotlin: models, AppFilter, CurfewPolicy, QuoteRotation,
                               AwayTime (streaks), SelfCare (breathing, prompts, reflections)
  data/DataStoreManager.kt     All persistence: prefs, vault, quotes, metrics, focus session
  data/AppRepository.kt        Installed launchable apps (LauncherApps)
  data/QuoteBank.kt            Default harsh truths
  system/IntentResolver.kt     OEM-agnostic phone/messages/notes/calendar/camera/alarm
  system/FocusAccessibilityService.kt  Lock screen + expand shade, nothing else
  system/PackageChangeReceiver.kt      Dynamic receiver for add/remove/change
  system/LauncherRoleHelper.kt RoleManager.ROLE_HOME handshake
  system/LockscreenQuoteWriter.kt      Renders the quote onto the lock wallpaper
  system/UsageAccess.kt        On-device "last on screen" times for flagged apps
  ui/LauncherContract.kt       UiState / Action / Effect
  ui/LauncherViewModel.kt      The state machine
  ui/SolisRoot.kt              Screen switcher + dialog host
  ui/home, ui/drawer, ui/moment, ui/away, ui/dialogs, ui/settings, ui/theme
```

## Features (all free)

1. **App vault**: hidden apps leave the A–Z list and sit behind a collapsed "vault" row at its end.
2. **Curfew**: the app list is closed between two configured times (wraps past midnight).
   Home and the five essentials keep working.
3. **Your own truths**: custom quote editor, plus optional lock-screen wallpaper sync.
4. **Launch delay**: flagged apps wait 5–30 s behind a countdown. Backing out counts as a dodge.
5. **Focus sessions**: close the app list for 25, 50 or 90 minutes from settings. Home shows the
   time left; the essentials keep working. "end early" in settings stops it.
6. **A moment**: one minute of box breathing (a square traced 4 s per side, four rounds) and one
   reflection prompt a day. Answers are kept on the phone as private notes to self.
7. **Time away**: with usage access granted, Solis shows how long you've stayed out of each
   flagged app, an encouraging line that grows with the streak, the next milestone (1 h, 6 h,
   1 d, 3 d, 7 d…) and your personal best. Home shows the shortest current streak, and the
   launch countdown reminds you what opening the app costs.

## Before publishing on Google Play

- **PACKAGE_USAGE_STATS**: only used on-device for the time-away feature. Mention it in the
  privacy policy; usage access is granted by the user in system settings.
- **QUERY_ALL_PACKAGES**: fill the permissions declaration form, core use case "launcher".
- **Accessibility**: Solis shows a prominent disclosure before opening accessibility settings.
  You must also complete the Accessibility API declaration in the Play Console and include a
  short video of the lock/shade features. The service config sets `canRetrieveWindowContent="false"`.

## Font

JetBrains Mono (no-ligature build, Light/Regular/Medium/Bold) is bundled under the SIL Open
Font License; the license text is in `app/src/main/assets/licenses/`. To swap it, replace the
four files in `app/src/main/res/font/` and keep the names.

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
