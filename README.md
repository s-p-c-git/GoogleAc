# gShare
Multi-account Google Drive manager — available as an **Android app** and a **web app**.

[![Build Debug APK](https://github.com/s-p-c-git/GoogleAc/actions/workflows/build-apk.yml/badge.svg)](https://github.com/s-p-c-git/GoogleAc/actions/workflows/build-apk.yml)

---

## 📱 Android App

A pre-built debug APK is available as a downloadable artifact on every CI run —
go to the [Actions tab](https://github.com/s-p-c-git/GoogleAc/actions), open
the latest **Build Debug APK** run, and download the artifact at the bottom of
the page.

See [TESTING.md](TESTING.md) for:
- Whether the UI screenshots are real or mocks (short answer: **they are programmatic mocks**)
- How to build, sideload, and test the app against your own Google accounts

---

## 🌐 Web App

A browser-based companion that lets you sign in with multiple Google accounts,
browse Drive files, search, filter, rename, delete, and move files between accounts.

**Quick start (requires Node.js ≥ 18):**

```bash
cd webapp

# 1. Copy the environment template and fill in your Google OAuth credentials
cp .env.example .env
#    Edit .env → set OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET, SESSION_SECRET
#    (See webapp/README.md Step 1 for how to create a Google OAuth Web client)

# 2. Install dependencies
npm install

# 3. Start the server
npm start
```

Then open **http://localhost:3000** in your browser.

For full setup instructions — including how to create a Google Cloud OAuth credential,
a walkthrough of every screen, and how to run the test suite — see
👉 **[webapp/README.md](webapp/README.md)**
