# DocVault Android — AI Context

Native Android client for DocVault, a secure personal document vault. Users upload, organise,
scan, and share documents with trusted people through groups. Talks to the FastAPI backend in
the sibling `docvault-be` repo.

**Stack:** Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Gradle (Kotlin DSL) ·
minSdk 26 / targetSdk 34 / compileSdk 34

## Current State — Read This First

**This is a scaffold. Nothing beyond the tab shell is built.** Four bottom-nav tabs (Vault,
Scan, Groups, Me) wired to a `NavHost` via `DocVaultDestination`, each showing a placeholder
screen. Dark-first Material 3 theme in place (`ui/theme/`). No auth, no networking, no
persistence, no permissions handling yet.

## Package Map

```
com.docvault.app/
├── DocVaultApplication.kt      # Application class — no DI graph wired yet
├── MainActivity.kt             # single Activity; hosts DocVaultTheme + DocVaultNavHost
├── navigation/
│   ├── DocVaultDestination.kt  # enum: route, label, icon for each of the 4 tabs
│   └── DocVaultNavHost.kt      # Scaffold + bottom bar + NavHost wiring
├── ui/theme/                   # Color.kt, Type.kt, Theme.kt
├── ui/components/              # shared composables (DocVaultBottomBar)
└── ui/screens/<feature>/       # vault/, scan/, groups/, me/ — one placeholder Screen.kt each
```

## Sprint Plan — Build Order (Person 1 track)

Per the 7-day sprint plan, this repo's owner builds full-stack vertical slices (Android UI +
the corresponding backend endpoints in `docvault-be`) in this order:

1. **Auth** — Firebase Google + phone-OTP sign-in, `POST /auth/login`, PIN/biometric lock
   screen backed by Android Keystore.
2. **Vault/Upload** — file picker, per-file upload progress, retry, list/grid toggle, search +
   filter, rate-limit (10/day) and size-cap (20 MB) blocked states.
3. **Scan** — camera capture with edge detection, multi-page session, page edit (reorder, crop,
   rotate, brightness/contrast, B&W filter), draft-until-save semantics.
4. **Groups & Invites** — create group, invite/accept/decline, 20-member cap, admin transfer.
5. **Sharing** — per-group view/download permission grants, revoke, access-log viewer.

Each corresponds to a tab or a modal flow already stubbed under `ui/screens/`. Add a
`viewmodel/`, `data/`, or nested `navigation/` package under the relevant feature package as it
grows — don't let a single `Screen.kt` file absorb an entire flow's logic.

## Screen & Data-Model Reference

The engineering handoff doc (`../Mobile app prototype scope.pdf`, sibling to this repo) is the
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

The PRD (`../DocVault_PRD_v1.3.pdf`) is the feature-level source of truth; the sprint plan
(`../DocVault_Sprint_Plan_v2.pdf`) is the day-by-day build order and integration checkpoints.

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

## Backend Integration

`docvault-be` is a scaffold too (see its `CLAUDE.md`) — most endpoints this app will eventually
call are specified but not yet implemented. Check the backend module's `docs/*.md` for the
exact request/response shape before building a screen against it; where the backend isn't built
yet, coordinate with the backend track rather than guessing the contract.
