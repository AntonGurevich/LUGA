# App Architecture

Overview of Acteamity's Android app structure: navigation, data flow, and the
main subsystems. Written after the Phase 0–9 redesign (iOS-parity UI port)
and the [Health Connect migration](HEALTH_CONNECT_MIGRATION.md), both of
which materially changed the app's shape from its original single-Activity
form. This intentionally doesn't cover backend/Supabase configuration — see
`SUPABASE_SETUP_GUIDE.md`, `TRIGGER_SETUP_GUIDE.md`, and the other
topic-specific docs in the repo root for that.

## Stack

- **UI**: Kotlin, XML layouts + `findViewById` (no Jetpack Compose,
  `viewBinding` is enabled in `build.gradle` but the code predominantly uses
  manual `findViewById`), Fragment-based navigation under a single host
  Activity.
- **Backend**: Supabase — Postgres (via `postgrest-kt`), Auth (`gotrue-kt`),
  Realtime, Storage, Edge Functions (`functions-kt`), all through the
  `io.github.jan-tennert.supabase` Kotlin BOM.
- **Background work**: `WorkManager` (`StepCountWorker`, periodic 2-hourly).
- **Local persistence**: Room (`offline/` package) — a single-purpose offline
  retry queue, not a general local cache. `SharedPreferences` is used
  extensively elsewhere for session state, appearance prefs, and sync
  bookkeeping flags.
- **Fitness data source**: Health Connect (see the migration doc above for
  the full history — Google Fit is fully removed as of this doc).
- **Charts**: MPAndroidChart (Stats tab).

## Navigation graph

```
MainActivity (splash, 2s delay, handles auth/password-reset deep links)
      │
      ▼
LoginActivity ──────────────► RegisterActivity (redirect shim)
      │  (already logged in?)         │
      │                                ▼
      │                        RegisterStep1Activity → RegisterStep2Activity
      │                                                        │
      ▼                                                        ▼
MainTabActivity  ◄───────────────────────────────────────────────┘
 (bottom nav host)
      │
      ├─ DashboardFragment   (nav_dashboard, default tab)
      ├─ StatsFragment        (nav_stats)
      ├─ TokenBreakdownFragment (nav_tokens)
      └─ ProfileFragment      (nav_profile)
```

`MainTabActivity` (`ui/MainTabActivity.kt`) is the single post-login host —
it owns a `BottomNavigationView` and swaps the four tab fragments into
`R.id.tabContainer` via `supportFragmentManager` transactions. Each fragment
also exposes `switchTo*Tab()` calls to `MainTabActivity` so, e.g., tapping the
token wallet card on Dashboard can jump straight to the Tokens tab.

Other screens (not tabs, launched as standalone Activities):
`ProfileEditActivity`, `ForgotPasswordActivity`, `PasswordResetActivity`,
`PrivacyDisclosureActivity`, `StepDataViewActivity` (legacy chart view, still
reachable but superseded in spirit by `StatsFragment`), `PermissionsRationaleActivity`
(never launched by app code directly — only by Health Connect's own UI).

### Dead/legacy code note

`CentralActivity` and `ProfileActivity` were the pre-Phase-2 single-Activity
predecessors to `MainTabActivity`/`DashboardFragment`/`ProfileFragment`; they
were deleted during the Health Connect migration once confirmed unreachable.
`RegisterActivity` is kept only as a one-method redirect shim (some old
intents/deep links may still reference it by class name) — its real
registration logic now lives in `RegisterStep1Activity`/`RegisterStep2Activity`.

## Auth

`AuthManager` (`AuthManager.kt`) wraps Supabase Auth (`gotrue-kt`):
sign-in/sign-up/sign-out, session persistence via `SharedPreferences`
(`auth_prefs`), password reset, and email-verification resend. Two redirect
URLs are used and must stay distinct:
- `https://acteamity.com/authentication` — signup/email verification only.
- `https://acteamity.com/password_recovery` — forwards to the
  `acteamity://reset` deep link (handled by `MainActivity` →
  `PasswordResetActivity`), which carries the actual recovery tokens.

`MainActivity` is the app's single entry point and deep-link handler for both
of these — see its `handleIntent()`/`onNewIntent()`.

`SupabaseUserManager.checkUserExists()` gates whether a freshly-authenticated
user still needs to complete registration (Step 2) — used by both
`LoginActivity` and `RegisterStep2Activity` so that a user who verified their
email but never finished the profile form gets redirected back to Step 2
instead of landing on an incomplete Dashboard.

## Data model (Supabase tables)

| Table | Purpose |
|---|---|
| `users_registry` | Core user profile record, keyed by Supabase Auth UID |
| `raw_steps` | Daily step totals, written by `StepCountWorker` |
| `raw_bike` | Daily cycling distance (meters), written by `StepCountWorker` |
| `raw_swim` | Daily swimming distance (meters), written by `StepCountWorker` |
| `token_record2` | Server-computed monthly token totals — **the source of truth for token UI**, not a local calculation |
| `dmp_company_user_registry` | Employer/company registry |
| `company_rules` | Per-employer rules: token limits, allowed activity types, conversion rate/currency |
| `user_corp_link` | Links a user to their employer |

`SupabaseUserManager.kt` is the single data-access layer for all of the
above — every table name is a `private const val ..._TABLE` constant at the
top of the file, and all reads/writes go through it (no direct `supabase.from(...)`
calls elsewhere in the app).

### Token economy

Tokens are earned from steps, cycling distance, and swimming distance,
subject to per-employer rules (`CompanyRules.kt`, cached per-session by
`ui/CompanyRulesCache.kt` after `DashboardFragment.loadTokenData()` fetches
it). `CompanyRules.isActivityAllowed(activityName)` lets an employer disable
specific activity types; `earnings(tokens)` converts a token count to a
currency string using the employer's conversion rate. `TokenCalculation.kt`
holds the **local, pre-server** step→token math (10,000 steps = 1 token, 30
exchangeable / 30 non-exchangeable per month) used only as a placeholder
before `token_record2` loads — see the note in the Health Connect doc about
why the local calculation intentionally excludes cycling/swimming.

## Activity data pipeline

```
Health Connect (StepsRecord / DistanceRecord / ExerciseSessionRecord)
        │
        │  every 2 hours, 30-day rolling window
        ▼
StepCountWorker (WorkManager)
        │
        │  on failure per-day
        ▼                                  ┌─────────────────────────┐
OfflineSyncQueue (Room, offline/) ────────►│ retried on next          │
        │                                   │ worker run               │
        ▼                                   └─────────────────────────┘
SupabaseUserManager.sync*DataSuspendDetailed()
        │
        ▼
raw_steps / raw_bike / raw_swim  (Supabase)
        │
        │  server-side aggregation (not in this repo)
        ▼
token_record2
        │
        ▼
DashboardFragment.loadTokenData() → UI
```

`OfflineSyncQueue` (`offline/OfflineSyncQueue.kt`, backed by Room —
`OfflineQueueDatabase`/`OfflineQueueDao`/`OfflineQueueItem`) exists
specifically so a day that fails to sync (e.g. no network) isn't silently
dropped: failed `StepDataReport`/`BikeDataReport`/`SwimDataReport` entries are
queued and retried at the start of the *next* worker run, before that run's
normal 30-day sync. This mirrors the iOS app's Core Data-backed offline
queue.

See [HEALTH_CONNECT_MIGRATION.md](HEALTH_CONNECT_MIGRATION.md) for the full
detail on how `StepCountWorker` reads from Health Connect, including the
manual-entry exclusion and overlapping-session merge logic.

## Notification/refresh flag pattern

Rather than a shared in-memory event bus between the background worker and
the UI, the app uses a simple `SharedPreferences` flag:
`StepCountWorker` sets `token_data_needs_refresh = true` in `"MyPrefs"` after
a successful sync; `DashboardFragment.onResume()` checks and clears that flag
to trigger `loadTokenData()` (with a short delay to let the sync settle).
This is a deliberately simple pattern given the worker and UI don't share a
process lifecycle guarantee.

## Design system / UI components

`ui/components/` holds shared auth-screen building blocks introduced in the
Phase 1 redesign: `AuthTextInputView`, `AuthPasswordInputView`,
`AuthPrimaryButton`. Colors/themes live in `res/values/colors.xml` and
`themes.xml` (+ `values-night/themes.xml` for dark mode), with a
high-contrast theme variant (`Theme_LUGA_HighContrast`) toggled via
`Acteamity.KEY_PREFERS_HIGH_CONTRAST` in the `AppearancePrefs` preferences
file, applied in each Activity/`MainTabActivity`'s `onCreate()` before
`super.onCreate()`.

## What's intentionally out of scope for future contributors to assume

- There is no MFA support (explicitly deferred — see project history).
- There are no automated tests for the sync pipeline; verification is
  manual/on-device plus `Log.d`-based logcat inspection (see the worker's
  existing logging style if adding new data-fetch code).
- Token totals are never computed client-side for real — the local
  `TokenCalculation` path is a placeholder UI only; changing token math
  belongs server-side, not in `DashboardFragment.calculateTokens()`.
