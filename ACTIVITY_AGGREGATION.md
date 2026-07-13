# Activity Aggregation — How Android Counts Steps/Distance, and How iOS Should

Audience: the iOS developer. This documents the multi-source deduplication
algorithm the Android app ships (settled 2026-07-12 after several rounds of
on-device testing against a Garmin watch as ground truth), why each piece
exists, and a concrete mapping to HealthKit APIs so both platforms produce
**consistent numbers for the same user**.

Android implementation: `aggregateBestSource()` in
[StepCountWorker.kt](app/src/main/java/silverbackgarden/example/luga/StepCountWorker.kt).
Background on the platform migration: [HEALTH_CONNECT_MIGRATION.md](HEALTH_CONNECT_MIGRATION.md).

---

## 1. The problem, with measured numbers

Health platforms (Health Connect on Android, HealthKit on iOS) are shared
stores that **many apps write into**. A typical user's day of steps exists
several times over:

- the phone's own step tracking,
- the watch vendor's app (Garmin Connect, Samsung Health, …),
- sync/bridge apps (Health Sync et al.) re-writing the same watch data
  under their own identity,
- re-aggregator apps (the Google Fit app on Android) exporting their own
  *merged* multi-source total back into the store as if it were new data.

What we measured on the test device (Garmin watch worn 100% of the time,
watch total = ground truth 9,385 steps for the reference day):

| Approach | Result | Why it's wrong |
|---|---|---|
| Sum all raw records | 18,017 (other days up to 3–4× real) | Every writer's copy counted |
| Platform `aggregate()` (HC's built-in priority dedup) | still inflated | Sources report at different time granularity; overlap detection fails, and it can't exclude manual entries |
| Max per 15-min bucket across sources | 18,017 | Chunky sources get time-smeared, win quiet slots, and effectively get ADDED to real-time sources (see §3.4) |
| Max per 3h window across sources | 17,108 | Same smear, dampened but fatal |
| + composite-origin exclusion + fine/chunky split (shipped) | 11,146 | Residual +19% from two fine sources disagreeing about timing |

Two problems, distinct and both mandatory to solve:

1. **Duplication** — the same physical steps written by N apps.
2. **Gaming** — a user typing fake steps/workouts into a health app's
   "Add data" UI to earn tokens. This is money; it must be filtered.

## 2. Core principle

> **Sources are alternative measurements of the same person — never
> additive parts.** Two apps' step counts for the same window are two
> opinions about one truth; you may pick the better opinion, but you may
> never add them.

Every failure mode in the table above is some form of accidentally adding
opinions together.

## 3. The algorithm (per day, per activity type)

Runs in the background worker for each of the last 30 days, for steps,
cycling distance, and swimming distance independently.

### 3.1 One raw read — discover sources and manual entries

Read all raw records for the day. From this derive:
- the set of **origins** (which app wrote each record — package name on
  Android, `HKSource` bundle id on iOS),
- per origin, the **time windows of manually-entered records**.

### 3.2 Exclude composite (re-aggregator) origins

Some origins are not devices — they re-export an already-merged total of
other sources. On Android that's the Google Fit app
(`com.google.android.apps.fitness`): its day totals *exceeded every real
device* (19.8k vs the 9.4k watch) because it contains its own merge of
phone + watch. A hardcoded blocklist (`COMPOSITE_ORIGINS`) demotes these:
they only compete if **no primary source has any data** in the range
(some users may genuinely have nothing else).

### 3.3 Excise manual entries per-origin via fine buckets

Ask the platform for each origin's data sliced into aligned **15-minute
buckets** (`BUCKET_MINUTES = 15`). Drop any bucket that overlaps one of
that origin's manual-entry windows. Everything else in that origin
survives.

Why buckets instead of subtracting manual values: platform-side dedup may
have already partially dropped a manual record where it overlapped sensor
data — subtracting its full value double-punishes. Dropping *time* instead
of doing arithmetic is immune to that. A typed-in entry costs at most its
own 15-minute buckets; honest data in the rest of the day is untouched.

Fail-open rule: only records explicitly marked manual are excluded.
Unknown/missing recording metadata = treated as legitimate, so a
third-party integration that doesn't set the field doesn't get its real
data stripped.

### 3.4 Classify each origin: fine vs chunky

**An origin may only compete inside time windows if its records are finer
than the window.** Rule: if *any* of the origin's records in the range
spans longer than the stitch window (3h), that origin is **chunky** for
that day.

Why this exists — the failure it prevents: sync apps often write one big
delayed block (e.g. a whole day of Garmin steps as a single record).
When the platform slices that block into buckets it **spreads the value
uniformly** — the watch appears to produce ~100 steps at 3 AM. Those
phantom night steps win the quiet windows (real-time sources show ~0
there), while the real-time source wins the active windows, and the
window-max total converges on *watch + phone added together*. Measured:
17.1k vs 9.4k. A chunky record simply can't prove *when* its steps
happened, so it's not allowed to claim any particular window.

### 3.5 Stitch fine origins; chunky origins bid day totals

- **Fine origins**: roll their clean 15-min buckets up into
  **3-hour stitching windows** (`STITCH_WINDOW_MINUTES = 180`). In each
  window, the single best origin wins; windows are summed. This is what
  preserves the athlete who runs at 7 AM with only the watch and carries
  only the phone all afternoon — genuine device alternation stitches.
- **Chunky origins**: each offers only its whole-range total.

```
dayTotal = max( sum over 3h windows of max(fine origins per window),
                max(chunky origin totals) )
```

### 3.6 Known residual error and the tuning dial

When two *fine* sources disagree about the timing of the same steps
(sync delays, clock/attribution offsets), window-stitching collects the
best of each: measured **+19%** vs the watch at 3h windows. Widening
`STITCH_WINDOW_MINUTES` to a full day reduces this to ~+5% (result =
best single source) at the cost of losing alternation stitching. The
product decision was to keep 3h. **Both platforms must use the same
value** — see §6.

### 3.7 Cycling / swimming specifics

Distance gets **two layers**:
1. **Session level**: read exercise sessions, keep only cycling/swimming
   types (match ALL sub-types — pool + open-water swimming, road +
   stationary biking, etc.), drop manually-entered sessions, and merge
   overlapping session time-windows across sources into disjoint ranges
   (a watch's "road biking" and a phone's generic "biking" for the same
   ride must become one window).
2. **Distance level**: within each merged window, run the exact §3.1–3.6
   pipeline on distance records.

Zero sessions ⇒ report 0 m for the day (a real value, not a skip).

## 4. Server contract (identical for both platforms)

Per day (yyyy-MM-dd, device-local timezone), per user (Supabase Auth uid):
- `raw_steps.steps` (int), `raw_bike.m_per_day` (int, meters),
  `raw_swim.m_per_day` (int, meters).
- 30-day rolling window, recomputed every sync (2-hourly + on app open).
- **Upsert semantics: write when the computed value differs** from the
  stored row — including downward corrections. Never "only increase":
  that guard blocked algorithm corrections from ever reaching the server
  and is exactly how we got stuck with inflated historical data.

Everything downstream (token math in `token_record2`) is server-side and
platform-agnostic.

## 5. Suggested iOS implementation (HealthKit mapping)

The algorithm is deliberately platform-neutral. HealthKit has direct
equivalents for every primitive — in some places nicer than Health
Connect's:

| Concept | Health Connect (Android) | HealthKit (iOS) |
|---|---|---|
| Raw read | `readRecords(StepsRecord)` | `HKSampleQuery` on `HKQuantityType(.stepCount)` |
| Record's origin | `metadata.dataOrigin.packageName` | `sample.sourceRevision.source.bundleIdentifier` |
| Manual entry flag | `metadata.recordingMethod == RECORDING_METHOD_MANUAL_ENTRY` | `sample.metadata?[HKMetadataKeyWasUserEntered] == true` |
| Per-origin 15-min buckets | `aggregateGroupByDuration(dataOriginFilter:, timeRangeSlicer:)` | `HKStatisticsCollectionQuery` with `intervalComponents = 15 min` and options `[.cumulativeSum, .separateBySource]` — one query returns per-source buckets, then read `sumQuantity(for: source)` per bucket |
| Record time span (chunky test) | `record.startTime/endTime` | `sample.startDate/endDate` |
| Exercise sessions | `ExerciseSessionRecord.exerciseType` | `HKWorkout.workoutActivityType` (`.cycling`, `.swimming`) |
| Cycling / swimming distance | generic `DistanceRecord` inside session windows | dedicated `HKQuantityType(.distanceCycling)` / `.distanceSwimming` |

Implementation notes for iOS:

1. **`.separateBySource` is your friend.** One
   `HKStatisticsCollectionQuery` per day per type gives every source's
   15-min buckets simultaneously — you don't need N queries like Android
   does. Build `originTotal` / window maps from that.
2. **Manual-entry windows** still need the raw `HKSampleQuery` pass:
   collect `(source, startDate, endDate)` for samples with
   `HKMetadataKeyWasUserEntered == true`, then drop overlapping buckets
   for that source only. Same fail-open rule: absent metadata = legit.
3. **Do NOT trust a plain `HKStatisticsQuery` `.cumulativeSum`** as the
   final answer, even though Apple dedupes *Apple-device* overlap
   (iPhone+Watch) inside statistics queries. Third-party writers (Garmin
   Connect, bridge apps) are not part of that dedup, and manual entries
   are included. Verify on a device with a third-party watch app before
   assuming anything — that's exactly the trap the Android side fell
   into with the platform aggregate.
4. **Composite origins on iOS**: probably an empty list to start
   (there's no Google-Fit-style re-exporter preinstalled), but keep the
   mechanism — log per-origin day totals (see §7) and if some origin
   consistently exceeds every physical device, blocklist its bundle id.
5. **Distance simplification**: because HealthKit has *typed* distance
   quantities (`.distanceCycling`, `.distanceSwimming`), you can run the
   pipeline directly on those types over the whole day instead of
   session-window slicing — the type itself scopes the distance to the
   activity. Keep the manual-workout exclusion (`HKWorkout` with
   `WasUserEntered`) as a cross-check: drop distance buckets overlapping
   a manual workout of that type.
6. **Timezone**: day boundaries are device-local midnight-to-midnight,
   same as Android (`Calendar` local time). Dates serialize as
   `yyyy-MM-dd`.

### Pseudocode (both platforms)

```
function dayTotal(type, dayStart, dayEnd):
    raw = readAllSamples(type, dayStart, dayEnd)
    if raw.isEmpty: return 0

    manualWindows = raw.filter(isManual).groupBy(origin) -> [(start,end)]
    origins       = raw.origins() - (COMPOSITE_ORIGINS if any primary origin exists)
    chunky        = origins where any sample span > STITCH_WINDOW

    windowBest = {}          # 3h-window start -> best fine-origin value
    bestChunky = 0
    for origin in origins:
        buckets = platformBuckets(type, origin, dayStart, dayEnd, 15min)
        clean   = buckets.reject(b => overlaps(b, manualWindows[origin]))
        total   = sum(clean)
        if origin in chunky:
            bestChunky = max(bestChunky, total)
        else:
            for b in clean:
                w = floor((b.start - dayStart) / STITCH_WINDOW)
                windowBest[w][origin] += b.value
    stitched = sum over w of max(windowBest[w].values)
    return max(stitched, bestChunky)
```

Constants (keep in lockstep with Android):
`BUCKET_MINUTES = 15`, `STITCH_WINDOW_MINUTES = 180`,
`COMPOSITE_ORIGINS(android) = {com.google.android.apps.fitness}`,
`COMPOSITE_ORIGINS(ios) = {}` until evidence says otherwise.

## 6. ⚠️ Cross-platform consistency matters more than local perfection

If one user runs the app on both platforms, **both write the same
`raw_*` rows**, and the sync writes on any difference. Two platforms
computing different totals for the same day will overwrite each other on
every sync, flip-flopping the server value (and the recomputed tokens).
So:

- same constants, same classification rules, same fail-open behavior;
- any future tuning (e.g. changing the stitch window) must ship on both
  platforms in the same release window;
- if the numbers can't be made to agree for some edge case, prefer the
  **lower** one on both — undercounting is the safe failure mode for a
  rewards product.

## 7. How to validate (what we did on Android)

1. Log **per-origin day totals** during development
   (`Origin com.garmin...: 9904 StepsRecord (chunky)`) — one line per
   source per day. This is what exposed every bug above.
2. Wear one trusted device 100% of a day; compare its own display to the
   computed total. Expect: computed ≈ trusted device, up to ~+19% if a
   phone also counts in disjoint windows.
3. Type a manual entry into Apple Health / a fitness app; confirm the
   next sync's total drops by roughly that entry's amount, and that only
   its buckets were dropped (rest of the day intact).
4. Check the server rows correct **downward** after an algorithm change
   — if they don't, an "only increase" guard is hiding somewhere.
