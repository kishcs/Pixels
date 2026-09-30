# Pixels

An offline-first "Year in Pixels" tracker for Android. Log one value per day on a 1–5 scale, and see the whole year as a coloured grid.

- Package `com.erohsik.pixels`, min SDK 26, target/compile SDK 36
- Kotlin 2.2.20, Jetpack Compose (BOM 2026.06.01), Material 3, AGP 8.13, Gradle 8.13
- Raw `SQLiteOpenHelper`, hand-rolled `ServiceLocator`, Glance widget. No Room, KSP, annotation processors, DI framework, network, analytics or ads.

## Build

```sh
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # minified release APK (unsigned; add a signingConfig to install)
./gradlew testDebugUnitTest    # Insights, DateUtils, BackupCodec, palette lightness
```

Open the project in Android Studio (Narwhal or newer) with JDK 17+.

## Layout

```
app/src/main/java/com/erohsik/pixels/
  PixelsApp.kt, MainActivity.kt
  di/ServiceLocator.kt
  data/        PixelsDbHelper, TrackerDao, EntryDao, SettingsStore, TrackerSelection
  data/model/  Tracker, Entry, Level
  data/export/ BackupCodec (org.json, pure), BackupRepository (SAF + transaction)
  domain/      DateUtils, Insights (pure, unit-tested)
  ui/          PixelsRoot (hand-rolled back stack), ViewModelFactory
  ui/year/     YearScreen, YearViewModel, PixelGrid (single Canvas)
  ui/month/    MonthScreen, MonthViewModel
  ui/log/      LogSheet, EntryWriter
  ui/trackers/ TrackerListScreen, TrackerEditScreen (+ ViewModels)
  ui/settings/ SettingsScreen, SettingsViewModel
  ui/palette/  Palettes
  ui/theme/    Color, Theme, Type
  widget/      PixelsWidget (Glance), PixelsWidgetReceiver
  notify/      ReminderScheduler (AlarmManager), ReminderReceiver
```

## Design notes

- **Day identity** is `LocalDate.toEpochDay()` everywhere. "Today" follows the device zone and refreshes on `DATE_CHANGED`, `TIME_SET` and `TIMEZONE_CHANGED`, and on every `onStart`.
- **Reactivity**: SQLite has no change notifications. Every write ticks one `MutableSharedFlow<Unit>` in `PixelsDbHelper`, and each `observe*` query re-runs on the tick.
- **Grid**: the ViewModel builds an `IntArray(372)` once per data change. The values are a level 1–5, `0` for an unlogged past day, or `-1` for a date that doesn't exist or is in the future. The canvas only indexes into this array. Drawing, hit testing and the month-initial header share one `GridMetrics`.
- **Palettes** are fixed in both themes, with no dynamic colour. Adjacent levels differ by ≥15 L\* (checked in `PalettesTest`). Calm is the default, and its low levels are blue, not red.
- **Import** merges rather than replaces. Trackers match by name, case-insensitive. Entries match by (tracker, day), and a conflict goes to the newer `updatedAt`. You see a summary before anything is written, and everything is applied in one transaction.
- **No streaks anywhere.** Insights are observational only.

## Assumptions (also marked `// ASSUMPTION:` in code)

- WAL is enabled with `enableWriteAheadLogging()` inside `onConfigure`. It issues `PRAGMA journal_mode=WAL` and also keeps the framework's connection pool consistent.
- `TrackerDao.observeAll` uses the shared change ticker rather than a separate tracker-only `MutableStateFlow`.
- Settings are read into memory once at startup, so theme and tracker don't flicker on cold start.
- "Unlogged past day" uses `onSurface` at 12% alpha. `surface` at 12% alpha would be invisible on a `surface` background.
- The widget, the reminder and the year screen all show the *home tracker*, meaning the one selected on the year screen. A single responsive widget covers both the 2×2 and 4×2 sizes. Glance rows are capped at 10 children, so the month strip is built from four 8-slot rows.
- Reminder suppression checks whether the home tracker has today logged.
- A note typed on a day with no level is dropped, because an entry needs a level.
- For an imported tracker that matches an existing tracker with zero entries, the palette and labels are adopted from the backup. That makes export → wipe → import reproduce the grid exactly.
- Insight thresholds not given in the spec: a weekday needs ≥4 entries to be compared, and each 30-day window needs ≥10 entries. The two insights shown rotate daily.
- Palette names are string resources (`nameRes`) rather than raw strings, so they live in `strings.xml`.
- The month calendar starts weeks on Monday.
- There's no icon library in the allowed dependency list, so the few glyphs (⋮ ‹ › ≡) are text, with accessibility labels.
- Test-only dependencies: `junit` and `org.json:json`. Android's `org.json` is a stub in local unit tests.
