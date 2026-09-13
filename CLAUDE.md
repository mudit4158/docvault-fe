# DocVault Android â€” AI Context

Native Android client for DocVault, a secure personal document vault. Users upload, organise,
scan, and share documents with trusted people through groups. Talks to the FastAPI backend in
the sibling `docvault-be` repo.

**Stack:** Kotlin Â· Jetpack Compose Â· Material 3 Â· Navigation Compose Â· Gradle (Kotlin DSL) Â·
minSdk 26 / targetSdk 34 / compileSdk 34

## Current State â€” Read This First

**Auth, Groups, Vault and Sharing are built and talking to the real backend. Scan is still a placeholder.**

| Area | State |
|---|---|
| Networking (Retrofit + OkHttp + kotlinx.serialization) | âœ… Built |
| DI graph (`AppContainer`, hand-wired) | âœ… Built |
| Token persistence (EncryptedSharedPreferences) | âœ… Built |
| Register / sign in / sign out | âœ… Built |
| Me tab â€” profile, upload allowance | âœ… Built |
| Groups â€” list, create, detail, members | âœ… Built |
| Invitations â€” list, accept, decline | âœ… Built |
| Member management â€” remove, leave, transfer admin | âœ… Built |
| Vault tab — list, search, type filter, upload with progress/retry, quota banner | ✅ Built (`ui/screens/vault/`) |
| Document detail — download, rename, type, tags, share sheet, move to trash + undo | ✅ Built (`ui/screens/documents/`) |
| Trash + restore | ✅ Built |
| Group Documents tab | ✅ Built |
| In-app preview with screenshots blocked (`FLAG_SECURE`) | ⬜ Pending — tracker #63 |
| Access-log viewer | ⬜ Pending — tracker #66 |
| Scan tab | ⬜ Placeholder — deliberately not built yet (Q10) |
| Offline cache | â¬œ Not built |

**To run it against a local backend, see [`README.md`](README.md).**

âš ï¸ **Never write a slash-star sequence inside a KDoc.** Kotlin block comments nest, so it opens a
comment that never closes and the file fails with "Unclosed comment". Writing `docs/*.md` in a
doc comment is enough to break the build.

## Package Map

```
com.docvault.app/
â”œâ”€â”€ DocVaultApplication.kt      # owns the AppContainer
â”œâ”€â”€ MainActivity.kt             # single Activity; hosts DocVaultTheme + DocVaultNavHost
â”œâ”€â”€ di/
â”‚   â””â”€â”€ AppContainer.kt         # hand-wired DI graph (not Hilt â€” see below)
â”œâ”€â”€ data/
â”‚   â”œâ”€â”€ TokenStore.kt           # bearer token + base URL, EncryptedSharedPreferences
â”‚   â”œâ”€â”€ ApiCall.kt              # ApiResult + error normalisation
â”‚   â”œâ”€â”€ DocVaultRepository.kt   # the single entry point the UI talks to
â”‚   â””â”€â”€ net/
â”‚       â”œâ”€â”€ Dtos.kt             # wire models mirroring the backend schemas
â”‚       â”œâ”€â”€ DocVaultApi.kt      # Retrofit interface
â”‚       â””â”€â”€ ApiProvider.kt      # AuthInterceptor + Retrofit construction
â”œâ”€â”€ navigation/
â”‚   â”œâ”€â”€ DocVaultDestination.kt  # enum: route, label, icon for each of the 4 tabs
â”‚   â””â”€â”€ DocVaultNavHost.kt      # auth gate + tab graph
â”œâ”€â”€ ui/theme/                   # Color.kt, Type.kt, Theme.kt
â”œâ”€â”€ ui/components/              # shared composables (DocVaultBottomBar)
â””â”€â”€ ui/screens/<feature>/       # auth/, groups/, me/ built; vault/, scan/ placeholders
```

## Architecture Notes

**DI is hand-wired, not Hilt.** The graph is four objects deep; annotation processing would add
build time and version-alignment risk for no benefit at this size. `AppContainer` is the single
file to replace with a Hilt module if it grows.

**ViewModels take constructor dependencies**, supplied by a small `factoryFor { }` helper in
`DocVaultNavHost`. There is no `ViewModel` with a no-arg constructor.

**The backend URL is editable at runtime** from the sign-in screen, and cached in `TokenStore`.
`ApiProvider` rebuilds Retrofit when it changes, so one APK works on an emulator (`10.0.2.2`) and
on a phone pointed at a laptop's LAN IP. Build-time default via `-PdocvaultApiUrl=...`.

**Errors come from the server.** `ApiCall.kt` surfaces the backend's `detail` message verbatim
rather than re-implementing rules client-side â€” so "Transfer admin rights before leaving this
group" is written once, on the server.

**Cleartext HTTP is debug-only.** `src/debug/` carries a network security config permitting it;
release builds block it. Do not move that into `src/main`.

**The client hides admin-only controls, but the server is the authority.** It returns 403
regardless of what the UI shows.

## Sprint Plan â€” Build Order (Person 1 track)

Per the 7-day sprint plan, this repo's owner builds full-stack vertical slices (Android UI +
the corresponding backend endpoints in `docvault-be`) in this order:

1. **Auth** â€” Firebase Google + phone-OTP sign-in, `POST /auth/login`, PIN/biometric lock
   screen backed by Android Keystore.
2. **Vault/Upload** â€” file picker, per-file upload progress, retry, list/grid toggle, search +
   filter, rate-limit (10/day) and size-cap (20 MB) blocked states.
3. **Scan** â€” camera capture with edge detection, multi-page session, page edit (reorder, crop,
   rotate, brightness/contrast, B&W filter), draft-until-save semantics.
4. **Groups & Invites** â€” create group, invite/accept/decline, 20-member cap, admin transfer.
5. **Sharing** â€” per-group view/download permission grants, revoke, access-log viewer.

Each corresponds to a tab or a modal flow already stubbed under `ui/screens/`. Add a
`viewmodel/`, `data/`, or nested `navigation/` package under the relevant feature package as it
grows â€” don't let a single `Screen.kt` file absorb an entire flow's logic.

## Screen & Data-Model Reference

The engineering handoff doc (`../docs/product/DocVault_Engineering_Handoff_v2.pdf`) is the
primary spec:

- **Â§1** â€” tab â†’ screen â†’ core-entity map (21 screens across 4 flows).
- **Â§2** â€” data model implications per entity (`Document`, `ShareGrant`, `AccessLog`, `Group`,
  `Membership`, `Invitation`, `UploadQuota`) with UI-implied fields â€” cross-check against the
  backend's actual schema in `docvault-be/app/*/models/` before wiring a network call, since this
  doc predates the real implementation.
- **Â§3** â€” flow-by-flow screenshots and engineering notes (compression tiers computed
  server-side, rotation baked into the exported image not stored as metadata, draft scan pages
  are client-side only until "Save to vault", etc.).
- **Â§4** â€” required states to build against (empty vault, no search results, upload failure,
  quota exhausted, offline, biometrics unavailable).
- **Â§5** â€” open questions that block build decisions (Groups in v1 scope, real limit numbers,
  web scan capture, legal review for identity documents, whether removing a group member revokes
  already-downloaded local files) â€” check these are resolved before building the affected flow.

The PRD (`../docs/product/DocVault_PRD_v1.3.pdf`) is the feature-level source of truth. The
sprint plan (`DocVault_Sprint_Plan_v2.pdf`) defines the day-by-day build order and integration
checkpoints, but is **not currently on disk** â€” its 5-slice build order is summarised in
`../docs/TRACKER.md` Â§4 until the file is added to `../docs/product/`.

Cross-repo documentation â€” the PRD, HLDs, and the work tracker of done vs. pending items â€”
lives in `../docs/`. Start at `../docs/README.md`.

## Conventions

- **Dark-first.** `DocVaultTheme` derives from `isSystemInDarkTheme()` with `dynamicColor`
  defaulted off â€” the brand accent (`VaultAccent`, `#E6472A`) stays fixed rather than following
  Material You. Don't reach for `dynamicColor = true` without a product decision.
- **One package per feature under `ui/screens/`.** Screens don't import from sibling feature
  packages â€” cross-feature navigation goes through `DocVaultNavHost`/`DocVaultDestination`.
- **`allowBackup="false"`** in the manifest â€” documents are sensitive (Aadhaar, PAN, passports
  per PRD Â§4.9); don't re-enable Android Auto Backup without an explicit decision on what's safe
  to include.
- **State survives tab switches.** The bottom-nav `popUpTo`/`saveState`/`restoreState` pattern in
  `DocVaultNavHost` is deliberate â€” keep it when adding new top-level destinations.

## Backend Integration

`docvault-be` is a scaffold too (see its `CLAUDE.md`) â€” most endpoints this app will eventually
call are specified but not yet implemented. Check the backend module's `docs/*.md` for the
exact request/response shape before building a screen against it; where the backend isn't built
yet, coordinate with the backend track rather than guessing the contract.

