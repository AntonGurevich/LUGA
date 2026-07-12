# Google Fit → Health Connect Migration

## Why

Google Fit's Android API (`com.google.android.gms.fitness.*`, shipped via the
`play-services-fitness` artifact) is deprecated. New developer sign-ups have
been blocked since May 2024, and Google has stated the platform stops working
by the end of 2026. Google's designated replacement is **Health Connect**
(`androidx.health.connect:connect-client`), which is built into Android 14+
and available as a separate app on API 28+.

This document describes the migration that replaced every Fit-dependent code
path in the app with Health Connect, and the state the app is in afterward.

## Scope: what changed vs. what didn't

The app has two independent step/activity data sources. Only one was Fit-based:

1. **Raw Android `SensorManager` (`Sensor.TYPE_STEP_COUNTER` /
   `TYPE_STEP_DETECTOR`)** — real-time on-device step ticking used by
   `DashboardFragment` for the live step ring. This is a core Android hardware
   sensor API, not Google Fit. **Untouched by this migration.**
2. **Cross-device/cross-app aggregation** (cycling + swimming distance, and
   the 30-day background sync to Supabase) — this *was* Google Fit's History
   API (`Fitness.getHistoryClient()`), used in `StepCountWorker` and (for a
   local pre-server-sync UI baseline) in `DashboardFragment`. **This is what
   migrated to Health Connect.**

Also in scope: the Google Sign-In flow that existed *purely* to obtain
Fitness OAuth scopes. Health Connect uses on-device manifest permissions
instead of OAuth, so that entire sign-in flow was deleted rather than ported.

## Architecture after the migration

```
┌─────────────────────────┐     ┌──────────────────────────┐
│ SensorManager            │     │ Health Connect             │
│ (TYPE_STEP_COUNTER/      │     │ (StepsRecord,               │
│  TYPE_STEP_DETECTOR)     │     │  DistanceRecord,            │
│                           │     │  ExerciseSessionRecord)     │
└───────────┬───────────────┘     └──────────────┬───────────────┘
            │ live UI ticks                        │ 2-hourly background read
            ▼                                       ▼
   DashboardFragment                        StepCountWorker
   (local step ring only,                   (30-day sync → Supabase
    server data wins once loaded)            raw_steps / raw_bike / raw_swim)
                                                       │
                                                       ▼
                                              SupabaseUserManager
                                              (token_record2 etc.)
                                                       │
                                                       ▼
                                     DashboardFragment.loadTokenData()
                                     (source of truth for token totals —
                                      NOT the local Fit/HC calculation)
```

The key existing design that made this migration lower-risk: **token
totals shown in the UI come from the server** (`token_record2`, loaded via
`SupabaseUserManager.fetchTokenData` → `DashboardFragment.loadTokenData()`),
not from a local Fit/Health Connect calculation. The local step count is only
a "before the server responds" placeholder. This meant the migration could
focus entirely on getting `StepCountWorker`'s Supabase sync correct, without
having to replicate a parallel local cycling/swimming aggregation for the UI.

## Permission model

Health Connect permissions are **Android manifest permissions**, requested at
runtime — not OAuth scopes.

- Manifest (`AndroidManifest.xml`): `android.permission.health.READ_STEPS`,
  `READ_DISTANCE`, `READ_EXERCISE`. All read-only — the app never writes
  fitness data.
- A `<queries>` block declaring `com.google.android.apps.healthdata`, needed
  so `HealthConnectClient.getSdkStatus()` can detect the Health Connect app on
  Android 13 and below (on 14+, Health Connect is part of the OS and this path
  isn't used).
- Runtime request: `PermissionController.createRequestPermissionResultContract()`,
  an `ActivityResultContract` — same pattern as any other Android runtime
  permission, wired up in `DashboardFragment` as
  `healthConnectPermissionLauncher`.
- Health Connect itself requires a small "why does this app want this data"
  rationale screen, shown from *within Health Connect's own UI* (not the
  app). This is `PermissionsRationaleActivity`, registered in the manifest
  with the `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` intent filter,
  plus an `activity-alias` (`ViewPermissionUsageActivity`) required
  specifically on Android 14+. It reuses `activity_privacy_disclosure.xml`
  (same content as the in-app Privacy screen).

### Availability vs. permission are two different checks

`HealthConnectAvailability` (`app/src/main/java/.../health/HealthConnectAvailability.kt`)
wraps `HealthConnectClient.getSdkStatus()`:

| Status | Meaning | Handling |
|---|---|---|
| `AVAILABLE` | Health Connect app present (or built into OS) | Proceed to permission check |
| `UPDATE_REQUIRED` | Installed but needs updating | Treated as unavailable; falls back to device sensors only |
| `NOT_INSTALLED` | Not present (Android 13- only) | Falls back to device sensors only; `installOrUpdateIntent()` is available for a future "install Health Connect" prompt but isn't currently wired into a UI button |

Availability must be `AVAILABLE` *before* checking granted permissions —
`HealthConnectClient.getOrCreate(context)` throws if Health Connect isn't
present.

## Where each piece lives

| File | Role |
|---|---|
| `health/HealthConnectAvailability.kt` | SDK availability check + install/update Play Store deep link |
| `PermissionsRationaleActivity.kt` | Static rationale screen required by Health Connect's permission UI |
| `ui/dashboard/DashboardFragment.kt` | Permission gating (`setupHealthConnectAndSensors()`, `checkHealthConnectPermissionsAndProceed()`, `onHealthConnectPermissionsGranted()`), permission-status indicator row, schedules `StepCountWorker` |
| `ui/profile/ProfileFragment.kt` | Read-only "Cycling & Swimming (Health Connect)" status row on the Profile tab |
| `StepCountWorker.kt` | The actual data pipeline: reads Health Connect, writes to Supabase |

## `StepCountWorker`: the core data pipeline

This is the highest-risk part of the migration since it's what actually
populates `raw_steps` / `raw_bike` / `raw_swim` and therefore token
calculations. Runs as a `WorkManager` `Worker`, every 2 hours, syncing a
rolling 30-day window (`dayOffset` 1..30).

### Availability/permission gate

```kotlin
if (HealthConnectAvailability.check(applicationContext) != Status.AVAILABLE) {
    return Result.success() // don't retry — nothing will change until the user acts
}
val client = HealthConnectClient.getOrCreate(applicationContext)
if (!client.permissionController.getGrantedPermissions().containsAll(REQUIRED_PERMISSIONS)) {
    return Result.success()
}
```

`Result.success()` (not `retry()`) is used for both cases — matching the
pre-existing pattern in this worker, where `Result.retry()` is reserved for
transient failures (e.g. the Supabase auth session not yet loaded in a fresh
worker process) rather than states that require user action.

### Steps — multi-source dedup (`aggregateBestSource`)

The first shipped version summed all raw `StepsRecord`s for the day. On-device
testing showed this **massively overcounts** (3–4×) on real phones, because
multiple apps write the same physical steps into Health Connect: on the test
device, Samsung's system tracking (`android`), Garmin Connect, Health Sync
(re-syncing Garmin), and the Google Fit app were all writing step data.

The algorithm that replaced it (`aggregateBestSource()` in `StepCountWorker.kt`),
arrived at through several on-device iterations:

1. **One raw read** discovers which apps (data origins) wrote records that day
   and where any manually-typed entries lie in time.
2. **Composite re-aggregator origins are sidelined** (`COMPOSITE_ORIGINS`,
   currently the Google Fit app): Fit re-exports its own multi-source *merge*
   into Health Connect, so its totals exceed every real device (19.8k observed
   vs a 9.4k watch reading). Composites only compete when no primary source
   has data at all.
3. Per origin, `aggregateGroupByDuration()` slices that origin's records into
   **15-minute buckets** (platform handles record-to-bucket splitting).
   Buckets overlapping one of that origin's manual-entry windows are dropped —
   typed-in data never reaches a total and costs at most its own buckets.
4. Origins are classified per-day as **fine** (all records shorter than the 3h
   stitch window) or **chunky** (any record longer): a source that writes big
   delayed blocks can't prove *when* its steps happened, and letting Health
   Connect smear it across windows stacked phantom night steps on top of
   real-time sources (measured: 17.1k vs a 9.4k watch day).
5. **Fine origins stitch**: best single origin per 3-hour window, windows
   summed — so genuine device alternation (watch-only workout in the morning,
   phone-only afternoon) is captured. **Chunky origins** bid only their
   whole-day total. Result = `max(stitched fine total, best chunky total)`.

Sources are treated as *alternative measurements of the same person*, never
additive parts. Known residual: when two fine sources disagree about the
timing of the same steps, window-stitching collects the best of each (~+19%
observed vs the watch at 3h windows; full-day windows would reduce this to
~+5% at the cost of losing alternation stitching — `STITCH_WINDOW_MINUTES`
is the dial).

### Sync guard: server rows mirror the latest computed value

`SupabaseUserManager`'s sync methods originally only updated an existing row
when the new value was **higher** — which silently blocked downward
corrections (the deduped totals couldn't overwrite the inflated rows already
in `raw_steps`). All six sync paths now update whenever the computed value
**differs**. Safe because the worker always recomputes complete days
(yesterday and older, never partial today) from Health Connect as source of
truth.

### Cycling / swimming distance

Google Fit split cycling and swimming into multiple sub-types
(road biking, stationary biking, pool swimming, open-water swimming, etc.)
rather than one generic type each — a real bug was found and fixed *before*
this migration where only one Fit sub-type ID was matched, meaning most real
swim/bike activity was silently never captured. Health Connect has the same
shape (multiple `exerciseType` values per category), so the fix carried
forward as type **sets**:

```kotlin
private val CYCLING_EXERCISE_TYPES = setOf(
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
)
private val SWIMMING_EXERCISE_TYPES = setOf(
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER
)
```

For each day:
1. Read `ExerciseSessionRecord`s in the day's window, filter to the matching
   type set, exclude manual entries.
2. **Merge overlapping session time-windows** (`mergeOverlappingTimeRanges`)
   before querying distance. If a watch logs "road biking" and a phone app
   logs generic "biking" for the same ride, querying distance once per
   session would double-count the overlapping time; querying merged,
   disjoint windows guarantees each moment is counted once.
3. For each merged window, read `DistanceRecord`, exclude manual entries, sum
   `distance.inMeters`.

### Manual-entry detection

Replaces the old Fit-era heuristic (matching
`DataPoint.getOriginalDataSource().getAppPackageName() == "com.google.android.apps.fitness"`,
i.e. "was this typed into Google Fit's own Add Activity screen") with Health
Connect's first-class metadata field:

```kotlin
private fun Metadata.isManualEntry(): Boolean =
    recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY
```

`recordingMethod` is one of `RECORDING_METHOD_UNKNOWN` (0),
`RECORDING_METHOD_ACTIVELY_RECORDED` (1), `RECORDING_METHOD_AUTOMATICALLY_RECORDED`
(2), `RECORDING_METHOD_MANUAL_ENTRY` (3). Only `MANUAL_ENTRY` is excluded —
**`UNKNOWN` is treated as legitimate (fails open)**. This matters because
`recordingMethod` is a relatively new Health Connect field (added ~April
2025); a third-party app that hasn't adopted it yet would report `UNKNOWN`,
and failing closed on that would wrongly strip real activity from apps like
Strava, Garmin Connect, or Samsung Health that haven't caught up yet.

### Why per-day queries instead of one batched call

Health Connect offers `aggregateGroupByPeriod()`, which can return 30 days of
step buckets in a single call — more efficient than 30 separate `readRecords`
calls. That optimization was **deliberately not used** here: `aggregate()`
has no way to filter out manually-entered records (no `recordingMethod`
filter on `AggregateRequest`, only a `dataOriginFilter`), so using it would
mean giving up manual-entry exclusion. The per-day `readRecords()` loop keeps
the code close to a line-for-line port of the previously-working Fit version,
which was judged to be the safer tradeoff for the piece of code that directly
feeds token calculations, at the cost of 90 Health Connect calls per worker
run instead of ~3.

## Permission UI

`DashboardFragment` shows a 4-item permission-status row (Activity
Recognition, Body Sensors, Health Connect Available, Health Connect
Permission). The Health Connect pair replaced what used to be "Google Fit" /
"Google Account" indicators — both the XML ids in `fragment_dashboard.xml`
(`googleFitIcon` → `healthConnectAvailableIcon`, etc.) and the Kotlin fields
were renamed to match.

`ProfileFragment`'s permissions section shows a simpler read-only status row
("Cycling & Swimming (Health Connect)"), checked asynchronously via
`viewLifecycleOwner.lifecycleScope.launch` since Health Connect's permission
check is a suspend call.

## What was deleted

- `com.google.android.gms:play-services-fitness` and
  `com.google.android.gms:play-services-auth` Gradle dependencies (nothing in
  the codebase references `com.google.android.gms.*` anymore).
- All Google Sign-In-for-fitness code: `setupGoogleSignIn()`,
  `FitnessOptions`/`GoogleSignInOptions` building, `RC_SIGN_IN`/
  `REQUEST_GOOGLE_SIGN_IN` activity-result handling, `manualGoogleSignIn()`,
  `initiateExplicitGoogleSignIn()`, `logAppSignature()` (a SHA-1 debug helper
  for diagnosing Google Sign-In `DEVELOPER_ERROR`s — no longer relevant).
- **`CentralActivity.kt` and `ProfileActivity.kt` — deleted entirely.** These
  were pre-fragment-redesign activities, orphaned once `MainTabActivity` +
  fragments (`DashboardFragment`, `ProfileFragment`) became the app's
  post-login entry point. Nothing in the live navigation graph reached them
  anymore, and they were the last things still compiling against Fit APIs.
  Confirmed dead by tracing every code path that could construct an
  `Intent(..., CentralActivity::class.java)` or
  `Intent(..., ProfileActivity::class.java)` back to its caller.
- `RegisterActivity.kt` trimmed from a full (dead) registration form down to
  a one-method redirect shim to `RegisterStep1Activity` — kept only because
  some old intents/deep links may still target it by class name.

## Known gaps / what still needs on-device verification

This migration was implemented and verified via `./gradlew assembleDebug` /
`bundleRelease` only — **no physical device or emulator testing has been done
yet.** Before relying on this in production:

1. **Android 14+ device** (Health Connect built into OS) — confirm the
   permission flow end-to-end and that a real day's totals look sane.
2. **Android 13- device with Health Connect pre-installed** — same check.
3. **Android 13- device *without* Health Connect installed** — confirm the
   app falls back to device-sensors-only gracefully rather than crashing (the
   `installOrUpdateIntent()` prompt path exists in `HealthConnectAvailability`
   but isn't wired into any UI button yet — currently the fallback is silent).
4. **Manual-entry filter validation** — manually add a workout/steps entry on
   a test device via a fitness app's own "Add activity" UI, then confirm the
   next `StepCountWorker` run excludes it from the synced totals. This is the
   one behavior that couldn't be validated for the old Fit-based heuristic
   either, so it's a new opportunity to actually confirm the exclusion works.
5. **Third-party app coverage** — Strava, Samsung Health, Garmin Connect, and
   Fitbit all have Health Connect integrations already (largely because of
   the Fit shutdown), but this hasn't been spot-checked against this app's
   specific read permissions.

## Version

Shipped as versionCode 23 / versionName 2.0.14, as a signed
`app-release.aab` for internal testing.
