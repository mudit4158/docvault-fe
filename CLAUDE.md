# DocVault Android — AI Context

Native Android client for DocVault, a secure personal document vault. Users upload, organise,
scan, and share documents with trusted people through groups. Talks to the FastAPI backend in
the sibling `docvault-be` repo.

**Stack:** Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Gradle (Kotlin DSL) ·
minSdk 26 / targetSdk 34 / compileSdk 34

## Current State — Read This First

**Auth, Groups, Vault, Sharing and Scan are built and talking to the real backend.**

| Area | State |
|---|---|
| Networking (Retrofit + OkHttp + kotlinx.serialization) | ✅ Built |
| DI graph (`AppContainer`, hand-wired) | ✅ Built |
| Token persistence (EncryptedSharedPreferences) | ✅ Built |
| Register / sign in / sign out | ✅ Built — password complexity enforced server-side (upper/lower/digit/special) |
| OTP sign-in (Firebase Phone Auth) | ✅ Built (`ui/screens/auth/OtpAuthScreen.kt`) — additional to password, not a replacement. Needs `google-services.json` to actually work; compiles without it |
| Me tab — profile, upload allowance | ✅ Built |
| Groups — list, create, detail, members | ✅ Built |
| Invitations — list, accept, decline | ✅ Built |
| Member management — remove, leave, transfer admin | ✅ Built |
| Vault tab — list, search, type filter, upload with progress/retry, quota banner | ✅ Built (`ui/screens/vault/`) |
| Document detail — download, rename, type, tags, share sheet, move to trash + undo | ✅ Built (`ui/screens/documents/`) |
| Trash + restore | ✅ Built |
| Group Documents tab | ✅ Built |
| Scan tab — capture (ML Kit Document Scanner: boundary detect + crop + gallery import), zoom-inspect, rotate/brightness/contrast/B&W, delete/retake/reorder pages, PDF (multi-page) or PDF/Image choice (single page), save via the existing `uploadDocument` path | ✅ Built (`ui/screens/scan/`) — no backend changes; uploads through the same `POST /documents` Vault already uses |
| Screenshots blocked (`FLAG_SECURE`) app-wide once signed in | ✅ Built — one `SecureScreen()` call in `DocVaultNavHost`, not per-screen (see Architecture Notes) |
| In-app preview with screenshots blocked | ✅ Built (`ui/screens/documents/PreviewScreen.kt`/`PreviewViewModel.kt`) — images via Coil, PDFs paged via the platform `PdfRenderer`; screenshots already blocked app-wide |
| Access-log viewer | ⬜ Pending — tracker #66 |
| Offline cache | ⬜ Not built |

**To run it against a local backend, see [`README.md`](README.md)** — including the
Troubleshooting table for device-specific install issues (e.g. MIUI/Xiaomi phones blocking
USB installs by default).

⚠️ **Never write a slash-star sequence inside a KDoc.** Kotlin block comments nest, so it opens a
comment that never closes and the file fails with "Unclosed comment". Writing `docs/*.md` in a
doc comment is enough to break the build.

## Package Map

```
com.docvault.app/
├── DocVaultApplication.kt      # owns the AppContainer
├── MainActivity.kt             # single Activity; hosts DocVaultTheme + DocVaultNavHost
├── di/
│   └── AppContainer.kt         # hand-wired DI graph (not Hilt — see below)
├── data/
│   ├── TokenStore.kt           # bearer token + base URL, EncryptedSharedPreferences
│   ├── ApiCall.kt              # ApiResult + error normalisation
│   ├── DocVaultRepository.kt   # the single entry point the UI talks to
│   └── net/
│       ├── Dtos.kt             # wire models mirroring the backend schemas
│       ├── DocVaultApi.kt      # Retrofit interface
│       └── ApiProvider.kt      # AuthInterceptor + Retrofit construction
├── navigation/
│   ├── DocVaultDestination.kt  # enum: route, label, icon for each of the 4 tabs
│   └── DocVaultNavHost.kt      # auth gate + tab graph + root-level Scan modal route
├── ui/theme/                   # Color.kt, Type.kt, Theme.kt
├── ui/components/              # shared composables (DocVaultBottomBar, SecureScreen)
└── ui/screens/<feature>/       # auth/, groups/, me/, vault/, scan/ built
    └── scan/
        ├── ScanScreen.kt, ScanNavHost.kt, ScanViewModel.kt, ScanPageOps.kt
        ├── PageEditScreen.kt, ReviewScreen.kt
        └── data/               # DocumentScannerLauncher, ScanCacheStore, PageTransforms, PdfAssembler, ScanSpec
```

## Architecture Notes

**DI is hand-wired, not Hilt.** The graph is four objects deep; annotation processing would add
build time and version-alignment risk for no benefit at this size. `AppContainer` is the single
file to replace with a Hilt module if it grows.

**ViewModels take constructor dependencies**, supplied by a small `factoryFor { }` helper in
`DocVaultNavHost`. There is no `ViewModel` with a no-arg constructor.

**Every tab screen under `MainTabs` (Vault, Groups, Me) uses `TAB_SCREEN_ZERO_INSETS`
(`ui/components/TabScreenInsets.kt`) in TWO places: its own `Scaffold`'s `contentWindowInsets`
AND its `TopAppBar`'s `windowInsets`.** `MainTabs` itself has an outer `Scaffold` (with the
bottom nav bar) that already reserves status-bar/nav-bar space for the whole tab area. Left at
their defaults, a tab screen's own `Scaffold` (`WindowInsets.systemBars`) *and*, independently,
its `TopAppBar` (`TopAppBarDefaults.windowInsets`, unaffected by the Scaffold's own setting)
would each reserve that same space a second time — two separate defaults, so zeroing only one
still leaves a gap. This was invisible on a phone with thin gesture-nav insets and glaringly
obvious on one with a tall 3-button nav bar — a real bug reported twice from exactly that
mismatch, once per default that still needed fixing. Any *new* tab screen added under
`MainTabs` needs both overrides; a root-level screen (document detail, group detail, scan,
auth, ...) does **not** — those aren't nested inside another `Scaffold`, so the real default is
correct there, and applying `TAB_SCREEN_ZERO_INSETS` to one would draw it under the status bar.

**The backend URL is editable at runtime** from the sign-in screen, and cached in `TokenStore`.
`ApiProvider` rebuilds Retrofit when it changes, so one APK works on an emulator (`10.0.2.2`) and
on a phone pointed at a laptop's LAN IP. Build-time default via `-PdocvaultApiUrl=...`.

**Errors come from the server.** `ApiCall.kt` surfaces the backend's `detail` message verbatim
rather than re-implementing rules client-side — so "Transfer admin rights before leaving this
group" is written once, on the server.

**Cleartext HTTP is debug-only.** `src/debug/` carries a network security config permitting it;
release builds block it. Do not move that into `src/main`.

**The client hides admin-only controls, but the server is the authority.** It returns 403
regardless of what the UI shows.

## Sprint Plan — Build Order (Person 1 track)

Per the 7-day sprint plan, this repo's owner builds full-stack vertical slices (Android UI +
the corresponding backend endpoints in `docvault-be`) in this order:

1. **Auth** — Firebase Google + phone-OTP sign-in, `POST /auth/login`, PIN/biometric lock
   screen backed by Android Keystore.
2. **Vault/Upload** — file picker, per-file upload progress, retry, list/grid toggle, search +
   filter, rate-limit (10/day) and size-cap (20 MB) blocked states.
3. **Scan** ✅ — camera capture with boundary detection + crop (ML Kit Document Scanner), gallery
   import, multi-page session, page edit (zoom-inspect, rotate, brightness/contrast, B&W filter,
   delete/retake/reorder), draft-until-save semantics, PDF (multi-page) or PDF/Image choice
   (single page). No backend changes — uploads through the existing `POST /documents`.
4. **Groups & Invites** — create group, invite/accept/decline, 20-member cap, admin transfer.
5. **Sharing** — per-group view/download permission grants, revoke, access-log viewer.

Each corresponds to a tab or a modal flow already stubbed under `ui/screens/`. Add a
`viewmodel/`, `data/`, or nested `navigation/` package under the relevant feature package as it
grows — don't let a single `Screen.kt` file absorb an entire flow's logic.

## Screen & Data-Model Reference

The engineering handoff doc (`../docs/product/DocVault_Engineering_Handoff_v2.pdf`) is the
primary spec:

- **§1** — tab → screen → core-entity map (21 screens across 4 flows).
- **§2** — data model implications per entity (`Document`, `ShareGrant`, `AccessLog`, `Group`,
  `Membership`, `Invitation`, `UploadQuota`) with UI-implied fields — cross-check against the
  backend's actual schema in `docvault-be/app/*/models/` before wiring a network call, since this
  doc predates the real implementation.
- **§3** — flow-by-flow screenshots and engineering notes (compression tiers computed
  server-side, rotation baked into the exported image not stored as metadata, draft scan pages
  are client-side only until "Save to vault", etc.).
- **§4** — required states to build against (empty vault, no search results, upload failure,
  quota exhausted, offline, biometrics unavailable).
- **§5** — open questions that block build decisions (Groups in v1 scope, real limit numbers,
  web scan capture, legal review for identity documents, whether removing a group member revokes
  already-downloaded local files) — check these are resolved before building the affected flow.

The PRD (`../docs/product/DocVault_PRD_v1.3.pdf`) is the feature-level source of truth. The
sprint plan (`DocVault_Sprint_Plan_v2.pdf`) defines the day-by-day build order and integration
checkpoints, but is **not currently on disk** — its 5-slice build order is summarised in
`../docs/TRACKER.md` §4 until the file is added to `../docs/product/`.

Cross-repo documentation — the PRD, HLDs, and the work tracker of done vs. pending items —
lives in `../docs/`. Start at `../docs/README.md`.

## Git Workflow

Two long-lived branches, mirrored in `docvault-be`: **`main`** (production/master) and **`uat`**
(staging). Day-to-day feature work happens on `uat`; merge to `main` once a slice is verified
against a real backend. Both repos' remotes are on `github.com/mudit4158/`.

This repo has two active committers (`Srishti Ganeriwal` on the scaffold/infra commits,
`mudit2812` on the auth/groups vertical slice) — check `git log --format='%an <%ae>'` before
assuming a single author when writing commit messages or attributing work.

## Conventions

- **Dark-first.** `DocVaultTheme` derives from `isSystemInDarkTheme()` with `dynamicColor`
  defaulted off — the brand accent (`VaultAccent`, `#E6472A`) stays fixed rather than following
  Material You. Don't reach for `dynamicColor = true` without a product decision.
- **One package per feature under `ui/screens/`.** Screens don't import from sibling feature
  packages — cross-feature navigation goes through `DocVaultNavHost`/`DocVaultDestination`.
- **`allowBackup="false"`** in the manifest — documents are sensitive (Aadhaar, PAN, passports
  per PRD §4.9); don't re-enable Android Auto Backup without an explicit decision on what's safe
  to include.
- **State survives tab switches.** The bottom-nav `popUpTo`/`saveState`/`restoreState` pattern in
  `DocVaultNavHost` is deliberate — keep it when adding new top-level destinations.
- **`SecureScreen()` (blocks screenshots, `FLAG_SECURE`) is called exactly once, at the top of
  `DocVaultNavHost` itself — never inside an individual route's screen.** The root `NavHost` has
  several sibling top-level routes (main, group detail, document detail, trash, scan, otp login);
  a `SecureScreen()` call inside any one of them clears the flag the instant you navigate away from
  it (its `DisposableEffect` runs `onDispose`), which would leave you *less* protected while looking
  at, say, a document's details than while sitting on the tab list. Calling it once on
  `DocVaultNavHost` — a composable that only leaves composition when the Activity itself does —
  means it's set for the app's entire runtime, both before and after sign-in (the sign-in screen has
  nothing sensitive to protect either way, so this is simpler than trying to gate it on auth state).
  Scan used to call `SecureScreen()` itself before this existed; that call is gone now — leaving it
  in would double-clear the flag on the way out of Scan.
- **Scan is the deliberate exception to the bottom-tab shell.** Tapping the Scan tab does not
  navigate `MainTabs`' inner `NavHost` — `DocVaultBottomBar`'s `onDestinationSelected` special-cases
  it to call `onOpenScan()`, which pushes a root-level `"scan"` route outside the tab `Scaffold`
  entirely, matching the original placeholder's own KDoc ("a full-screen modal flow outside the
  tab bar"). A fresh `ScanViewModel` (and cache session) is created every time — never
  `saveState`/`restoreState`'d like Vault/Groups/Me are.
- **Scan is the deliberate exception to the bottom-tab shell.** Tapping the Scan tab does not
  navigate `MainTabs`' inner `NavHost` — `DocVaultBottomBar`'s `onDestinationSelected` special-cases
  it to call `onOpenScan()`, which pushes a root-level `"scan"` route outside the tab `Scaffold`
  entirely, matching the original placeholder's own KDoc ("a full-screen modal flow outside the
  tab bar"). A fresh `ScanViewModel` (and cache session) is created every time — never
  `saveState`/`restoreState`'d like Vault/Groups/Me are.
- **Scan's capture UI is Google's ML Kit Document Scanner** (`play-services-mlkit-document-scanner`),
  not hand-rolled CameraX + edge detection — see `ui/screens/scan/data/DocumentScannerLauncher.kt`.
  Boundary detection, crop, multi-page sessions and gallery import all come from that one
  Google-maintained flow; rotate/brightness/contrast/B&W are a custom screen layered after it
  (`PageEditScreen.kt`), since ML Kit's own UI doesn't support those.
- **OTP sign-in is entirely client-driven, like Scan's ML Kit choice.** This app talks to Firebase
  directly (`OtpAuthViewModel`, `PhoneAuthProvider.verifyPhoneNumber`) — Firebase sends the SMS and
  owns the resend cooldown; the backend only ever verifies the resulting ID token
  (`FirebaseOtpProvider` in docvault-be). Password sign-in is unaffected; OTP is additive.
  **The `google-services` Gradle plugin is applied conditionally** in `app/build.gradle.kts`
  (`if (file("google-services.json").exists())`) rather than unconditionally in the `plugins {}`
  block — that plugin hard-fails the build the moment it can't find the file, and this repo needs
  to keep building for anyone who hasn't dropped in a real Firebase project's config yet. The
  Kotlin code compiles either way; only actually signing in needs the file. Pin `firebase-bom` and
  `kotlinx-coroutines-play-services` to versions built against this project's Kotlin version
  (`2.0.20`) — newer releases of both are compiled with newer Kotlin metadata and fail with
  "Module was compiled with an incompatible version of Kotlin" if bumped carelessly; this was hit
  and fixed once already (see git history) by pinning to `firebase-bom 33.16.0` /
  `kotlinx-coroutines-play-services 1.9.0` rather than the latest of either.

## Backend Integration

`docvault-be` is a scaffold too (see its `CLAUDE.md`) — most endpoints this app will eventually
call are specified but not yet implemented. Check the backend module's `docs/*.md` for the
exact request/response shape before building a screen against it; where the backend isn't built
yet, coordinate with the backend track rather than guessing the contract.
