# gShare — Web App

A browser-based companion to the **gShare** Android app.  
It lets you sign in with one or more Google accounts, browse Drive files, search, filter, and manage files (rename / delete / move between accounts) — all from a standard web browser on Ubuntu (or any machine with Node.js ≥ 18).

---

## Prerequisites

| Tool | Minimum version |
|------|----------------|
| Node.js | 18 |
| npm | 9 |
| A modern browser | Chrome / Firefox / Edge |

---

## Step 1 — Create a Google Cloud OAuth 2.0 *Web* client

The webapp uses a **Web Application** OAuth 2.0 client (not the Android one).

1. Go to [Google Cloud Console → APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials).
2. Create a project (or reuse the one you created for the Android app).
3. Enable the **Google Drive API** for the project.
4. Click **"Create credentials" → OAuth client ID → Web application**.
   - **Name:** `gShare Web`
   - **Authorized redirect URIs:** add `http://localhost:3000/auth/callback`
5. Note the **Client ID** and **Client Secret** that Google generates.

---

## Step 2 — Configure the webapp

```bash
cd webapp
cp .env.example .env
# Edit .env and fill in OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET, SESSION_SECRET
```

`.env` fields:

| Variable | Description |
|----------|-------------|
| `OAUTH_CLIENT_ID` | The *Web* client ID from Step 1 (e.g. `12345.apps.googleusercontent.com`) |
| `OAUTH_CLIENT_SECRET` | The client secret from Step 1 |
| `SESSION_SECRET` | Any long random string used to sign the session cookie |
| `PORT` | (Optional) Port to listen on, default `3000` |

---

## Step 3 — Install and start

```bash
cd webapp
npm install
npm start
```

Then open **http://localhost:3000** in your browser.

---

## Step 4 — Walk through the webapp

| Step | What to expect |
|------|---------------|
| Open the app | **Auth screen** — "Sign in with Google" button |
| Tap "Sign in" | Browser redirects to Google's consent screen |
| Grant permissions | Drive (read + write) and profile scopes are requested |
| Redirect back | Server exchanges the auth code for tokens, stores them in session |
| **Feature enablement** | Toggle the features you want (Drive, Calendar, Tasks, AI Summarizer) then click **Continue** |
| Drive screen | Your Drive root files appear, tagged with an account badge |
| Add a second account | Click "Add account" in the top-right → repeat sign-in |
| Search | Type in the search bar — results update in real time |
| MIME filter | Click a chip (Folders / Docs / Sheets / PDFs) to filter |
| File detail | Click any file row — detail pane opens on the right |
| Rename | Click ✏️ in the detail pane (if permitted) |
| Delete | Click 🗑️ in the detail pane (if permitted) |
| Move | Click ↗ in the detail pane (if permitted, only when ≥2 accounts) |

---

## Running tests (no Google credentials needed)

```bash
cd webapp
npm test
```

The test suite exercises the account-management logic and the Drive file filtering without making live API calls.

---

## Development

```bash
npm run dev   # uses node --watch for auto-restart on file changes
```
