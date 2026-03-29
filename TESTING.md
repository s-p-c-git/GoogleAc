# Testing Guide

## Q1 — Are the screenshots in `docs/screenshots/` real Android screenshots or mocks?

**They are programmatic mocks, not screenshots from a running Android device.**

The PNG files were generated with a Python + [Pillow](https://pillow.readthedocs.io/) script
that draws pixel-perfect Material Design 3 composables on a 390 × 844 canvas — the same logical
resolution as a Pixel 6 in portrait orientation.  No emulator, physical device, or Android
framework was involved in producing them.

### Why mocks instead of real screenshots?

| Reason | Detail |
|---|---|
| **CI environment** | The GitHub Actions runner that generates these artifacts has no GPU / display, so the Android emulator cannot produce screenshots during CI. |
| **No real OAuth credentials in the repo** | Running the app against live Google APIs requires a `client_id` that must not be committed (see §Q2 below). |
| **Deterministic pixel output** | Mocks guarantee that the images never change due to OS / font / locale differences, which would create noisy diffs in pull requests. |

### What the mocks accurately represent

Each mock faithfully mirrors the Kotlin source code in the corresponding feature module:

| Screenshot | Source it mirrors |
|---|---|
| `auth_idle.png`, `auth_error.png`, `auth_success.png` | `feature/auth/…/ui/AuthScreen.kt` — 4 `AuthUiState` cases |
| `drive_list.png`, `drive_detail.png`, `drive_search.png` | `feature/drive/…/ui/DriveScreen.kt` — `ListDetailPaneScaffold` |
| `multi_account_list.png` | `AccountRepository.addAccount` log flow + `AccountDao` |
| `drive_files_per_account.png` | `DriveRepository.observeRootFiles` per-account partitioning |
| `move_file.png` | `DriveRepository.moveFile` insert-then-delete semantics |

### What they do NOT prove

- That the Compose UI compiles and renders correctly on a real device.
- That live Google API calls succeed.
- That OAuth token storage / Keystore encryption works end-to-end.

Those guarantees come from the JVM unit tests (`./gradlew test`) and, ultimately, a manual
sideload on a real device (see Q2 below).

---

## Q2 — How do I install the app on my Android phone and test it against my real Google accounts?

### Prerequisites

| Tool | Minimum version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 |
| Android SDK | API 35 (compile), API 26 minimum |
| Physical device or emulator | Android 8.0 (API 26)+ |

---

### Step 1 — Create a Google Cloud OAuth client

The app uses **OAuth 2.0 with PKCE** to sign in to Google.  You must supply your own
`client_id`; it is intentionally not committed to this repository.

1. Go to [Google Cloud Console → APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials).
2. Create a project (or reuse an existing one).
3. Enable the **Google Drive API** for the project.
4. Click **"Create credentials" → OAuth client ID → Android**.
   - **Package name:** `com.googleac.app`
   - **SHA-1 fingerprint:** run the command below to obtain your debug keystore fingerprint:
     ```bash
     keytool -keystore ~/.android/debug.keystore \
             -list -v -alias androiddebugkey \
             -storepass android -keypass android \
       | grep "SHA1"
     ```
5. Note the **client ID** that Google generates (format: `<numbers>.apps.googleusercontent.com`).

---

### Step 2 — Wire the client ID into the app

Open `feature/auth/src/main/kotlin/com/googleac/feature/auth/ui/AuthScreen.kt` and look
for the `clientId` constant / parameter that is passed to `OAuthPkceHelper.buildAuthorizationUrl`.

> **Note:** The OAuth PKCE helper (`OAuthPkceHelper`) is already fully implemented — you only
> need to supply the real `client_id` value.  The recommended approach is a
> `local.properties` entry so it is never committed:

```properties
# local.properties  (already in .gitignore)
oauth.client_id=YOUR_CLIENT_ID_HERE
```

Then expose it via `build.gradle.kts` as a `BuildConfig` field:

```kotlin
// app/build.gradle.kts → android { defaultConfig { … } }
val localProps = java.util.Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
buildConfigField("String", "OAUTH_CLIENT_ID",
    "\"${localProps.getProperty("oauth.client_id", "")}\"")
```

And reference it in the UI:

```kotlin
OAuthPkceHelper.buildAuthorizationUrl(
    clientId = BuildConfig.OAUTH_CLIENT_ID,
    codeChallenge = …
)
```

---

### Step 3 — Build and install the debug APK

```bash
# Clone (if you haven't already)
git clone https://github.com/s-p-c-git/GoogleAc.git
cd GoogleAc

# Connect your phone via USB and enable USB Debugging
# (Settings → Developer options → USB debugging)

# Build & install in one step
./gradlew installDebug
```

Or open the project in **Android Studio**, select your device from the toolbar, and click ▶ Run.

---

### Step 4 — Walk through the app

| Step | What to expect |
|---|---|
| Launch | `AuthScreen` appears — "Sign in with Google" button |
| Tap "Sign in" | A Chrome Custom Tab opens Google's consent screen |
| Grant permissions | Drive file access + profile scopes are requested |
| Redirect back | `MainActivity` receives the `com.googleac.app:/oauth2redirect` intent; auth code is exchanged for tokens |
| Drive screen | `DriveScreen` loads — root files for the signed-in account appear |
| Add a second account | Repeat the sign-in flow; the app stores each account in Room with a unique `accountId` |
| File list | Both accounts' files appear with colour-coded chips (`ac1` purple, `ac2` blue) |
| Move a file | Long-press a file → "Move to account" → select the destination account |

---

### Step 5 — Run the JVM unit tests (no device required)

The unit tests run entirely on the JVM — no emulator needed:

```bash
./gradlew test
```

Expected output:

```
> Task :feature:auth:test
> Task :feature:drive:test
> Task :feature:ai:test
BUILD SUCCESSFUL
83 tests completed, 0 failed
```

Test classes and what they cover:

| Class | Tests | Validates |
|---|---|---|
| `OAuthPkceHelperTest` | 14 | PKCE code-verifier / challenge / URL generation |
| `AuthViewModelTest` | 8 | ViewModel state machine (Idle / Loading / Success / Error) |
| `AccountRepositoryTest` | 17 | Multi-account add + debug logging; `observeAllAccounts`; `setActiveAccount` |
| `AiSummarizerTest` | 17 | On-device extractive summarisation |
| `DriveModelsTest` | 13 | `DriveCapabilities` / `DriveFileResponse` data classes |
| `DriveViewModelTest` | 13 | File list / search routing / `moveFile` delegation |
| `DriveRepositoryTest` | 14 | Per-account isolation; cross-account `moveFile` (insert-before-delete) |

---

### Troubleshooting

| Symptom | Fix |
|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall any existing version of the app before reinstalling. |
| OAuth redirect not received | Verify the SHA-1 in Google Cloud Console matches your debug keystore. |
| Drive files not loading | Confirm the **Google Drive API** is enabled in the Cloud Console project. |
| `Keystore` crash on emulator | The Android Keystore is not available in the emulator by default; test token storage on a physical device. |
| Build fails with `Missing BuildConfig.OAUTH_CLIENT_ID` | Add the `oauth.client_id` property to your `local.properties` (Step 2). |
