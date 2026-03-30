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
| `feature_enablement_idle.png`, `feature_enablement_partial.png`, `feature_enablement_all_enabled.png` | `feature/auth/…/ui/FeatureEnablementScreen.kt` — 3 `FeatureEnablementViewModel` states |
| `drive_list.png`, `drive_detail.png`, `drive_search.png` | `feature/drive/…/ui/DriveScreen.kt` — `ListDetailPaneScaffold` |
| `multi_account_list.png` | `AccountRepository.addAccount` log flow + `AccountDao` |
| `drive_files_per_account.png` | `DriveRepository.observeRootFiles` per-account partitioning — search bar + per-row `[ac1]`/`[ac2]` chip tags |
| `move_file.png` | `DriveRepository.moveFile` insert-then-delete semantics — search-first flow showing `[ac1]` before-move and `[ac2]` after-move chip tags |

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

#### Option A — Download a pre-built APK from GitHub Actions (no toolchain needed)

Every push to `main` or `copilot/**` branches automatically triggers the
**Build Debug APK** workflow and uploads the resulting APK as a downloadable
artifact:

1. Go to the [Actions tab](https://github.com/s-p-c-git/GoogleAc/actions) of
   this repository.
2. Click the latest **Build Debug APK** run.
3. Scroll to **Artifacts** at the bottom of the run summary.
4. Download **`GoogleAc-debug-<run-number>`** (a zip containing the APK).
5. Unzip and side-load `app-debug.apk` onto your device:

```bash
adb install app-debug.apk
```

#### Option B — Build locally with Android Studio

Open the project in **Android Studio** (Hedgehog 2023.1.1 or newer).
Android Studio will automatically provision the Gradle wrapper (including the
required `gradle-wrapper.jar`) on first sync, then you can click ▶ Run or use
the terminal:

```bash
./gradlew installDebug
```

#### Option C — Build locally with a system-installed Gradle

If you have Gradle 8.13+ installed and want to build from the command line
without Android Studio:

```bash
# Generate the Gradle wrapper JAR first (only needed once)
gradle wrapper --gradle-version 8.13

# Then build
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

---

### Step 4 — Walk through the app

| Step | What to expect |
|---|---|
| Launch | `AuthScreen` appears — "Sign in with Google" button |
| Tap "Sign in" | A Chrome Custom Tab opens Google's consent screen |
| Grant permissions | Drive file access + profile scopes are requested |
| Redirect back | `MainActivity` receives the `com.googleac.app:/oauth2redirect` intent; auth code is exchanged for tokens |
| **Feature enablement** | `FeatureEnablementScreen` appears — toggle the Google services you want (Drive, Calendar, Tasks, AI Summarizer) per account, then tap **Continue** (or **Skip for now**) |
| Drive screen | `DriveScreen` loads — root files for the signed-in account appear |
| Add a second account | Repeat the sign-in flow; each account gets its own feature enablement step and its own `accountId` in Room |
| File list | Both accounts' files appear with **per-row `[ac1]`/`[ac2]` colour-coded chips** (purple = ac1, blue = ac2) — the same chip is visible on every file row, making account ownership immediately clear |
| Move a file | Tap the Search icon → type the filename → the result row shows the file tagged `[ac1]`; tap "Move to another account → ac2" → the file reappears tagged `[ac2]` with a green **✓ Moved** badge |

---

### Step 5 — Run the JVM unit tests (no device required)

The unit tests run entirely on the JVM — no emulator needed.  If you haven't
generated the wrapper JAR yet (see Option C above), use `gradle test` instead:

```bash
./gradlew test    # if wrapper JAR is present (generated by Android Studio)
# OR
gradle test       # if you have Gradle 8.13+ installed system-wide
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
| `AuthViewModelTest` | 13 | ViewModel state machine (Idle / AwaitingRedirect / Loading / Success / Error); accountId derivation |
| `FeatureEnablementViewModelTest` | 17 | Toggle, enableAll, saveAndContinue (new + existing account), skipAndContinue |
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
