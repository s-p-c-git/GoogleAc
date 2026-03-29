# UI Screenshots — Visual Validation

These screenshots were generated using Chromium headless rendering of pixel-perfect
Material Design 3 mockups that faithfully mirror the Jetpack Compose source code in
each feature module.

---

## Auth Feature (`feature/auth`)

The `AuthScreen` composable has **4 distinct UI states** driven by `AuthUiState`:

| Screenshot | State | What it shows |
|---|---|---|
| ![Auth – Idle](auth_idle.png) | `AuthUiState.Idle` | Initial screen — "GoogleAc" title, subtitle, **Sign in with Google** button |
| ![Auth – Error](auth_error.png) | `AuthUiState.Error` | Red error card with message from failed token exchange + **Retry** button |
| ![Auth – Success](auth_success.png) | `AuthUiState.Success` | Avatar initial, signed-in email, green "Authentication successful" badge |

All three states are covered by `AuthViewModelTest` in
`feature/auth/src/test/…/ui/AuthViewModelTest.kt`.

---

## Drive Feature (`feature/drive`)

The `DriveScreen` composable uses a **ListDetailPaneScaffold** (adaptive layout):

| Screenshot | State | What it shows |
|---|---|---|
| ![Drive – List](drive_list.png) | File list | Unified view across 3 accounts (colour-coded chips): folders, PDFs, Docs, Sheets |
| ![Drive – Detail](drive_detail.png) | File detail pane | File metadata, **AI Summary** bullets, **Drive Capabilities** badges, role-based action buttons (Rename ✓ / Delete ✓ / Move ✗ grayed out per capabilities) |
| ![Drive – Search](drive_search.png) | Search active | Search bar with live query "budget", 3 cross-account results with matched term highlighted |

State logic is covered by `DriveViewModelTest` in
`feature/drive/src/test/…/ui/DriveViewModelTest.kt`.

---

## Test coverage cross-reference

| Unit test class | Source | Screens validated |
|---|---|---|
| `OAuthPkceHelperTest` (14 tests) | PKCE crypto layer | Auth flow security |
| `AuthViewModelTest` (8 tests) | `AuthViewModel` state machine | Auth – Idle / Error / Success |
| `AiSummarizerTest` (17 tests) | `AiSummarizer` | "AI Summary" bullets in Drive Detail |
| `DriveModelsTest` (13 tests) | `DriveFileResponse` / `DriveCapabilities` | Capability badges in Drive Detail |
| `DriveViewModelTest` (10 tests) | `DriveViewModel` | Drive List / Search routing |
