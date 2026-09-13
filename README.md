# DocVault — Android

Native Android client for DocVault, a secure personal document vault. Talks to the FastAPI backend in the sibling `docvault-be` repo.

> **Working with Claude Code in this repo?** Start at [`CLAUDE.md`](CLAUDE.md).

**Stack:** Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Retrofit · Gradle (Kotlin DSL) · minSdk 26 / targetSdk 34

---

# Installation & Testing

Everything below assumes the backend is running on your laptop. The app never ships with a hardcoded server — you point it at one on the sign-in screen.

## Prerequisites

| Need | Notes |
|---|---|
| JDK 17 | Android Studio bundles one at `<studio>/jbr` |
| Android SDK, compileSdk 34 | Usually `%LOCALAPPDATA%\Android\Sdk` on Windows |
| `local.properties` | Not in git. Create it with `sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk` |

---

## Step 1 — Start the backend

In `docvault-be`:

```bash
python scripts/init_dev_db.py      # first run only — creates the SQLite tables
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

**`--host 0.0.0.0` matters.** The default binds to `127.0.0.1`, reachable only from the laptop itself — an emulator or phone gets "connection refused".

Confirm on the laptop: <http://localhost:8000/docs>

---

## Step 2a — Run on an emulator

The simplest path; no firewall or network setup.

```bash
# list your AVDs
%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -list-avds

# boot one
%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd <avd-name>

# build + install
./gradlew :app:installDebug
```

**Server address:** leave the default `http://10.0.2.2:8000`. `10.0.2.2` is the emulator's alias for the host machine's loopback — `localhost` inside the emulator means the emulator itself, which is why the default is not `localhost`.

---

## Step 2b — Run on your phone

### i. Open the firewall (once)

Windows blocks inbound connections by default, so the phone will time out without this. Run in an **Administrator** PowerShell:

```powershell
New-NetFirewallRule -DisplayName "DocVault dev API" -Direction Inbound -LocalPort 8000 -Protocol TCP -Action Allow -Profile Private
```

`-Profile Private` opens the port on your home Wi-Fi only, never on public networks.

### ii. Find your laptop's LAN IP

```powershell
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -like "*Wi-Fi*" }
```

This is a DHCP lease and **can change after a router reboot**. If the app suddenly cannot connect, re-check it. A DHCP reservation on your router avoids the problem.

### iii. Verify before installing anything

Open this in the **phone's browser**:

```
http://<laptop-ip>:8000/health
```

If it does not return `{"status":"ok"}`, stop — the problem is network, not the app. Usual causes: phone on mobile data instead of Wi-Fi, phone and laptop on different networks, guest-network client isolation, or the firewall rule missing.

### iv. Install the APK

```bash
./gradlew :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk` (~17 MB). Three ways to get it across:

**No cable** — send the APK to yourself (WhatsApp, Drive, email), open it on the phone, tap install. Android warns about an unknown developer; that is expected for an unsigned debug build.

**USB** — enable Developer Options (Settings → About phone → tap *Build number* seven times), turn on **USB debugging**, plug in, accept the pairing prompt, then:

```bash
./gradlew :app:installDebug
# or
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Wireless (Android 11+)** — Developer Options → *Wireless debugging* → *Pair device with pairing code*, then:

```bash
adb pair <ip>:<pair-port>
adb connect <ip>:<debug-port>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### v. Point the app at the laptop

On the sign-in screen tap **Server settings** and enter `http://<laptop-ip>:8000`.

One APK works on both targets — the address is stored on the device, not compiled in. To change the compiled-in *default*:

```bash
./gradlew :app:assembleDebug -PdocvaultApiUrl=http://192.168.1.42:8000/
```

---

## Step 3 — Walk through it

1. **Create an account** — *New here? Create an account*. Pick your country from the selector and enter the number without the dial code. Password 8–72 characters.
2. **Groups tab** — create a group. You become its admin.
3. **Invite** — open the group, tap the person-add icon. Choose from contacts or type a number. Invitations only reach people who **already have** a DocVault account, so register a second account first (sign out from the Me tab).
4. **Accept / decline** — sign in as the invitee; pending invitations sit at the top of the Groups tab.
5. **Manage** — make someone admin, remove them, leave, or delete the group.

Every action hits the real backend; watch the uvicorn console to see the requests.

---

## Tests

```bash
./gradlew :app:testDebugUnitTest      # JVM unit tests — no device needed
./gradlew :app:connectedAndroidTest   # instrumentation — needs a running emulator/device
```

Unit tests cover phone parsing and country handling. Instrumentation tests cover the auth gate and local validation; signed-in flows need a `DocVaultRepository` test double, which is not built yet.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| "Cannot reach the server" | Backend not running, bound to `127.0.0.1` instead of `0.0.0.0`, or wrong address in *Server settings* |
| Works on emulator, fails on phone | Firewall rule missing, or devices on different networks |
| Worked yesterday, fails today | Laptop's DHCP lease changed — re-check the IP |
| "Your session has expired" | Token older than 60 minutes. Sign in again; there is no refresh token yet |
| "…does not have an active DocVault account" | Correct behaviour — group invites only reach registered users |
| Cleartext HTTP blocked | Only the **debug** build permits plain HTTP (`src/debug/`). Release blocks it by design |
| Gradle cannot find the SDK | Missing `local.properties` — see Prerequisites |

---

# Development

## Layout

```
app/src/main/java/com/docvault/app/
├── DocVaultApplication.kt   # owns the AppContainer
├── MainActivity.kt          # single Activity, hosts the Compose nav graph
├── di/AppContainer.kt       # hand-wired dependency graph
├── data/                    # TokenStore, ApiCall, DocVaultRepository, net/
├── navigation/              # DocVaultDestination (tab enum) + DocVaultNavHost
├── ui/theme/                # dark-first Material 3 theme
├── ui/components/           # shared composables — bottom bar, PhoneNumberField, contacts picker
└── ui/screens/<feature>/    # auth, groups, me built; vault, scan placeholders
```

Each feature owns its package under `ui/screens/`. As one grows past a single composable, add `viewmodel/` or `data/` under that feature rather than growing the shell.

## Current State

| Area | State |
|---|---|
| Auth — register, sign in/out, profile, quota | ✅ |
| Groups — list, create, detail, members | ✅ |
| Invitations — invite (contacts or manual), accept, decline | ✅ |
| Members — remove, leave, transfer admin | ✅ |
| Vault tab | ⬜ Needs backend `document_management` |
| Scan tab | ⬜ Needs backend `document_management` |
| Offline cache | ⬜ |

## Notes

**Contacts are read without `READ_CONTACTS`.** The invite flow uses the system picker (`ACTION_PICK` on the phone data type), which returns only the single number the user chose. A document vault asking to read the whole address book is a poor trade, and the permission would sit in the Play listing forever.

**Phone numbers are captured as country + national number**, joined to E.164 only at the API boundary. See `ui/components/PhoneNumber.kt`. The country list is curated, not the full ISO set, and gives E.164 *shape* rather than real per-country validation — if that becomes necessary, move both client and server to libphonenumber rather than growing the rules by hand.

## Product Reference

Behaviour comes from the PRD (v1.3) and the engineering handoff doc in [`../docs/product/`](../docs/product/). Cross-repo docs — HLDs, the work tracker — are in [`../docs/`](../docs/README.md).
