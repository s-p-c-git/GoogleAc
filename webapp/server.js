'use strict';

require('dotenv').config();

const express = require('express');
const session = require('express-session');
const rateLimit = require('express-rate-limit');
const { google } = require('googleapis');
const path = require('path');
const crypto = require('crypto');

const PORT = parseInt(process.env.PORT || '3000', 10);
const CLIENT_ID = process.env.OAUTH_CLIENT_ID || '';
const CLIENT_SECRET = process.env.OAUTH_CLIENT_SECRET || '';
const IS_PRODUCTION = process.env.NODE_ENV === 'production';

let SESSION_SECRET = process.env.SESSION_SECRET;
if (!SESSION_SECRET) {
  if (IS_PRODUCTION) {
    throw new Error('SESSION_SECRET environment variable must be set in production');
  }
  SESSION_SECRET = crypto.randomBytes(32).toString('hex');
  console.warn('⚠  SESSION_SECRET not set — using a random secret (sessions will be lost on restart). Set SESSION_SECRET in .env for persistent sessions.');
}
const REDIRECT_URI = `http://localhost:${PORT}/auth/callback`;

// OAuth scopes — mirrors the Android app
const SCOPES = [
  'https://www.googleapis.com/auth/drive',
  'https://www.googleapis.com/auth/userinfo.email',
  'https://www.googleapis.com/auth/userinfo.profile',
];

// ---------------------------------------------------------------------------
// Rate limiters
// ---------------------------------------------------------------------------
// Strict limiter for auth endpoints (10 requests / 15 minutes per IP)
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many authentication requests — please try again later.' },
  skip: () => process.env.NODE_ENV === 'test',
});

// API limiter (120 requests / minute per IP)
const apiLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 120,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests — please try again later.' },
  skip: () => process.env.NODE_ENV === 'test',
});

// ---------------------------------------------------------------------------
// App setup
// ---------------------------------------------------------------------------
const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: false }));
app.use(
  session({
    secret: SESSION_SECRET,
    resave: false,
    saveUninitialized: false,
    cookie: {
      httpOnly: true,
      sameSite: 'lax',
      // In production, require HTTPS so the session cookie is encrypted in transit.
      secure: IS_PRODUCTION,
    },
  })
);
app.use(express.static(path.join(__dirname, 'public')));

// ---------------------------------------------------------------------------
// CSRF protection
//
// A CSRF token is issued via GET /api/csrf-token and stored in the session.
// All state-mutating requests (POST / PATCH / DELETE) must include the token
// in the X-CSRF-Token header.  OAuth GET routes (/auth/login, /auth/callback)
// are exempt because they are protected by the OAuth state parameter.
// The test-only session-seeding route is also exempt.
// ---------------------------------------------------------------------------
app.get('/api/csrf-token', (req, res) => {
  if (!req.session.csrfToken) {
    req.session.csrfToken = crypto.randomBytes(32).toString('hex');
  }
  res.json({ csrfToken: req.session.csrfToken });
});

// Global CSRF guard — applied to all mutating methods except test helpers
const CSRF_EXEMPT = new Set(['/auth/callback', '/test/seed-session']);
app.use((req, res, next) => {
  const mutating = ['POST', 'PATCH', 'PUT', 'DELETE'].includes(req.method);
  if (!mutating) return next();
  if (process.env.NODE_ENV === 'test') return next();
  if (CSRF_EXEMPT.has(req.path)) return next();
  const token = req.headers['x-csrf-token'] || req.body?._csrf;
  if (!token || token !== req.session.csrfToken) {
    return res.status(403).json({ error: 'Invalid CSRF token' });
  }
  next();
});

function requireCsrf(_req, _res, next) {
  // CSRF is now enforced globally above; this stub is kept for clarity.
  next();
}

// ---------------------------------------------------------------------------
// Helper — build an OAuth2 client for a stored token set
// ---------------------------------------------------------------------------
function makeOAuth2Client(tokens) {
  const client = new google.auth.OAuth2(CLIENT_ID, CLIENT_SECRET, REDIRECT_URI);
  if (tokens) client.setCredentials(tokens);
  return client;
}

// ---------------------------------------------------------------------------
// Helper — derive a stable account-id from an email address
// (mirrors the Android app: first 8 bytes of SHA-256(email.lowercase()), hex)
// ---------------------------------------------------------------------------
function deriveAccountId(email) {
  return crypto.createHash('sha256').update(email.toLowerCase()).digest('hex').slice(0, 16);
}

// ---------------------------------------------------------------------------
// Auth — begin OAuth flow
// GET /auth/login[?state=<json-encoded-redirect-info>]
// ---------------------------------------------------------------------------
app.get('/auth/login', authLimiter, (req, res) => {
  const oauthClient = makeOAuth2Client(null);
  const state = crypto.randomBytes(16).toString('hex'); // CSRF token
  req.session.oauthState = state;

  const url = oauthClient.generateAuthUrl({
    access_type: 'offline',
    prompt: 'consent',
    scope: SCOPES,
    state,
  });
  res.redirect(url);
});

// ---------------------------------------------------------------------------
// Auth — OAuth callback
// GET /auth/callback?code=…&state=…
// ---------------------------------------------------------------------------
app.get('/auth/callback', authLimiter, async (req, res) => {
  const { code, state, error } = req.query;

  if (error) {
    return res.redirect('/?auth_error=' + encodeURIComponent(String(error)));
  }
  if (!code || state !== req.session.oauthState) {
    return res.redirect('/?auth_error=invalid_state');
  }
  delete req.session.oauthState;

  try {
    const oauthClient = makeOAuth2Client(null);
    const { tokens } = await oauthClient.getToken(String(code));
    oauthClient.setCredentials(tokens);

    // Fetch the user's profile so we have email + displayName
    const oauth2 = google.oauth2({ version: 'v2', auth: oauthClient });
    const { data: profile } = await oauth2.userinfo.get();

    const accountId = deriveAccountId(profile.email || '');

    // Persist in session (keyed by accountId so multiple accounts can coexist)
    if (!req.session.accounts) req.session.accounts = {};
    req.session.accounts[accountId] = {
      accountId,
      email: profile.email || '',
      displayName: profile.name || profile.email || '',
      photoUrl: profile.picture || null,
      tokens,
      enabledFeatures: [],   // set via feature-enablement step
    };

    // Track insertion order for badge numbering (ac1, ac2, …)
    if (!req.session.accountOrder) req.session.accountOrder = [];
    if (!req.session.accountOrder.includes(accountId)) {
      req.session.accountOrder.push(accountId);
    }

    res.redirect('/feature-enablement?accountId=' + encodeURIComponent(accountId));
  } catch (err) {
    console.error('OAuth callback error:', err);
    res.redirect('/?auth_error=token_exchange_failed');
  }
});

// ---------------------------------------------------------------------------
// Auth — logout (remove a specific account or all accounts)
// POST /auth/logout  { accountId?: string }
// ---------------------------------------------------------------------------
app.post('/auth/logout', requireCsrf, (req, res) => {
  const { accountId } = req.body;
  if (accountId && req.session.accounts) {
    delete req.session.accounts[accountId];
    if (req.session.accountOrder) {
      req.session.accountOrder = req.session.accountOrder.filter((id) => id !== accountId);
    }
  } else {
    req.session.accounts = {};
    req.session.accountOrder = [];
  }
  res.json({ ok: true });
});

// ---------------------------------------------------------------------------
// Feature enablement — save selected features for an account
// POST /api/feature-enablement  { accountId, enabledFeatures: string[] }
// ---------------------------------------------------------------------------
app.post('/api/feature-enablement', apiLimiter, requireCsrf, (req, res) => {
  const { accountId, enabledFeatures } = req.body;
  if (!req.session.accounts || !req.session.accounts[accountId]) {
    return res.status(401).json({ error: 'Account not found in session' });
  }
  const VALID_FEATURES = ['DRIVE', 'CALENDAR', 'TASKS', 'AI_SUMMARIZER'];
  const filtered = (Array.isArray(enabledFeatures) ? enabledFeatures : []).filter((f) =>
    VALID_FEATURES.includes(f)
  );
  req.session.accounts[accountId].enabledFeatures = filtered;
  res.json({ ok: true, enabledFeatures: filtered });
});

// ---------------------------------------------------------------------------
// Accounts — list all signed-in accounts
// GET /api/accounts
// ---------------------------------------------------------------------------
app.get('/api/accounts', (req, res) => {
  if (!req.session.accounts) return res.json({ accounts: [], accountOrder: [] });

  const order = req.session.accountOrder || Object.keys(req.session.accounts);
  const accounts = order
    .filter((id) => req.session.accounts[id])
    .map((id, idx) => {
      const { tokens, ...safeAccount } = req.session.accounts[id]; // strip tokens
      return { ...safeAccount, badge: `ac${idx + 1}` };
    });
  res.json({ accounts, accountOrder: order });
});

// ---------------------------------------------------------------------------
// Drive — list files
// GET /api/drive/files?accountId=…&q=…&mimeType=…&pageToken=…
// ---------------------------------------------------------------------------
app.get('/api/drive/files', apiLimiter, async (req, res) => {
  const { accountId, q = '', mimeType = '', pageToken = '' } = req.query;

  const account = req.session.accounts?.[String(accountId)];
  if (!account) return res.status(401).json({ error: 'Account not authenticated' });

  try {
    const oauthClient = makeOAuth2Client(account.tokens);
    const drive = google.drive({ version: 'v3', auth: oauthClient });

    // Build the Drive API query.
    // Sanitize inputs before embedding them in the query string:
    //   - Single quotes (') delimit string literals in Drive query syntax — embedding
    //     them unescaped would break the query structure or allow injection.
    //   - Backslashes (\) are the Drive query escape character.
    //   - Control characters (U+0000–U+001F) can confuse the API parser.
    const sanitizeQueryValue = (v) => v.replace(/['\\]/g, '').replace(/[\x00-\x1f]/g, '');
    const queryParts = ["trashed = false"];
    if (mimeType) {
      const safeMime = sanitizeQueryValue(String(mimeType));
      if (safeMime) queryParts.push(`mimeType = '${safeMime}'`);
    }
    if (q) {
      const safeQ = sanitizeQueryValue(String(q));
      if (safeQ) queryParts.push(`(name contains '${safeQ}' or fullText contains '${safeQ}' )`);
    }

    const fields =
      'nextPageToken, files(id, name, mimeType, size, createdTime, modifiedTime, webViewLink, thumbnailLink, parents, capabilities, driveId)';

    const listParams = {
      q: queryParts.join(' and '),
      fields,
      pageSize: 50,
      orderBy: 'folder,name',
    };
    if (pageToken) listParams.pageToken = String(pageToken);

    const response = await drive.files.list(listParams);

    // Refresh tokens if they were refreshed during the request
    const updatedCredentials = oauthClient.credentials;
    if (updatedCredentials.access_token !== account.tokens.access_token) {
      req.session.accounts[String(accountId)].tokens = updatedCredentials;
    }

    res.json({
      files: response.data.files || [],
      nextPageToken: response.data.nextPageToken || null,
    });
  } catch (err) {
    console.error('Drive list error:', err.message);
    const status = err.code === 401 ? 401 : 502;
    res.status(status).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// Drive — get a single file's metadata + AI summary
// GET /api/drive/files/:fileId?accountId=…
// ---------------------------------------------------------------------------
app.get('/api/drive/files/:fileId', apiLimiter, async (req, res) => {
  const { fileId } = req.params;
  const { accountId } = req.query;

  const account = req.session.accounts?.[String(accountId)];
  if (!account) return res.status(401).json({ error: 'Account not authenticated' });

  try {
    const oauthClient = makeOAuth2Client(account.tokens);
    const drive = google.drive({ version: 'v3', auth: oauthClient });

    const { data: file } = await drive.files.get({
      fileId: String(fileId),
      fields:
        'id, name, mimeType, size, createdTime, modifiedTime, webViewLink, thumbnailLink, parents, capabilities, description, contentHints',
    });

    // Extract text for AI summarisation (best effort)
    let summary = null;
    if (
      account.enabledFeatures.includes('AI_SUMMARIZER') &&
      file.contentHints?.indexableText
    ) {
      summary = extractiveSummarize(file.contentHints.indexableText);
    }

    res.json({ file, summary });
  } catch (err) {
    console.error('Drive get file error:', err.message);
    res.status(502).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// Drive — rename a file
// PATCH /api/drive/files/:fileId  { accountId, name }
// ---------------------------------------------------------------------------
app.patch('/api/drive/files/:fileId', apiLimiter, requireCsrf, async (req, res) => {
  const { fileId } = req.params;
  const { accountId, name } = req.body;

  const account = req.session.accounts?.[String(accountId)];
  if (!account) return res.status(401).json({ error: 'Account not authenticated' });
  if (!name || typeof name !== 'string' || !name.trim()) {
    return res.status(400).json({ error: 'name is required' });
  }

  try {
    const oauthClient = makeOAuth2Client(account.tokens);
    const drive = google.drive({ version: 'v3', auth: oauthClient });
    const { data: updated } = await drive.files.update({
      fileId: String(fileId),
      requestBody: { name: name.trim() },
      fields: 'id, name',
    });
    res.json({ ok: true, file: updated });
  } catch (err) {
    console.error('Drive rename error:', err.message);
    res.status(502).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// Drive — delete (trash) a file
// DELETE /api/drive/files/:fileId?accountId=…
// ---------------------------------------------------------------------------
app.delete('/api/drive/files/:fileId', apiLimiter, requireCsrf, async (req, res) => {
  const { fileId } = req.params;
  const { accountId } = req.query;

  const account = req.session.accounts?.[String(accountId)];
  if (!account) return res.status(401).json({ error: 'Account not authenticated' });

  try {
    const oauthClient = makeOAuth2Client(account.tokens);
    const drive = google.drive({ version: 'v3', auth: oauthClient });
    await drive.files.delete({ fileId: String(fileId) });
    res.json({ ok: true });
  } catch (err) {
    console.error('Drive delete error:', err.message);
    res.status(502).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// Drive — move a file to another account (copy + delete semantics, same as
// the Android app's insert-before-delete strategy)
// POST /api/drive/files/:fileId/move
// { sourceAccountId, targetAccountId }
// ---------------------------------------------------------------------------
app.post('/api/drive/files/:fileId/move', apiLimiter, requireCsrf, async (req, res) => {
  const { fileId } = req.params;
  const { sourceAccountId, targetAccountId } = req.body;

  const sourceAccount = req.session.accounts?.[String(sourceAccountId)];
  const targetAccount = req.session.accounts?.[String(targetAccountId)];
  if (!sourceAccount) return res.status(401).json({ error: 'Source account not authenticated' });
  if (!targetAccount) return res.status(401).json({ error: 'Target account not authenticated' });

  try {
    const srcClient = makeOAuth2Client(sourceAccount.tokens);
    const dstClient = makeOAuth2Client(targetAccount.tokens);
    const srcDrive = google.drive({ version: 'v3', auth: srcClient });
    const dstDrive = google.drive({ version: 'v3', auth: dstClient });

    // 1. Fetch file metadata from source
    const { data: srcFile } = await srcDrive.files.get({
      fileId: String(fileId),
      fields: 'id, name, mimeType, size',
    });

    // 2. Copy to target account's My Drive root
    const { data: copied } = await dstDrive.files.copy({
      fileId: String(fileId),
      requestBody: { name: srcFile.name },
      fields: 'id, name',
    });

    // 3. Delete original from source (mirrors the Android insert-then-delete)
    await srcDrive.files.delete({ fileId: String(fileId) });

    res.json({ ok: true, newFileId: copied.id, name: copied.name });
  } catch (err) {
    console.error('Drive move error:', err.message);
    res.status(502).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// Catch-all — serve the SPA for client-side routes
// ---------------------------------------------------------------------------
app.get(['/feature-enablement', '/drive'], apiLimiter, (_req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// ---------------------------------------------------------------------------
// Test-only session seeding — available only when NODE_ENV=test
// POST /test/seed-session  { account: { accountId, email, … } }
// ---------------------------------------------------------------------------
if (process.env.NODE_ENV === 'test') {
  app.post('/test/seed-session', (req, res) => {
    const { account } = req.body;
    if (!account || !account.accountId) return res.status(400).json({ error: 'account required' });
    if (!req.session.accounts) req.session.accounts = {};
    if (!req.session.accountOrder) req.session.accountOrder = [];
    req.session.accounts[account.accountId] = account;
    if (!req.session.accountOrder.includes(account.accountId)) {
      req.session.accountOrder.push(account.accountId);
    }
    res.json({ ok: true });
  });
}

// ---------------------------------------------------------------------------
// Extractive summarization (mirrors AiSummarizer in the Android app)
// Returns up to 5 bullet points from the most "significant" sentences.
// ---------------------------------------------------------------------------
function extractiveSummarize(text, maxSentences = 5) {
  if (!text || !text.trim()) return null;

  // Split into sentences
  const sentences = text
    .replace(/\s+/g, ' ')
    .trim()
    .split(/(?<=[.!?])\s+/)
    .filter((s) => s.split(/\s+/).length >= 5); // skip very short sentences

  if (sentences.length === 0) return null;

  // TF-IDF-inspired scoring: prefer sentences with rare+frequent terms
  const wordCounts = {};
  for (const s of sentences) {
    for (const w of s.toLowerCase().match(/\b\w+\b/g) || []) {
      wordCounts[w] = (wordCounts[w] || 0) + 1;
    }
  }
  const totalWords = Object.values(wordCounts).reduce((a, b) => a + b, 0) || 1;

  const scored = sentences.map((s) => {
    const words = s.toLowerCase().match(/\b\w+\b/g) || [];
    const score = words.reduce((acc, w) => acc + (wordCounts[w] || 0) / totalWords, 0);
    return { s, score };
  });

  scored.sort((a, b) => b.score - a.score);
  return scored
    .slice(0, maxSentences)
    .map(({ s }) => s)
    .join(' ');
}

// ---------------------------------------------------------------------------
// Config — expose safe config to the frontend (no secrets)
// GET /api/config
// ---------------------------------------------------------------------------
app.get('/api/config', (_req, res) => {
  res.json({
    configured: Boolean(CLIENT_ID && CLIENT_SECRET),
  });
});

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------
if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`GoogleAc webapp listening on http://localhost:${PORT}`);
    if (!CLIENT_ID || !CLIENT_SECRET) {
      console.warn(
        '⚠  OAUTH_CLIENT_ID / OAUTH_CLIENT_SECRET not set — copy .env.example to .env and fill in your credentials.'
      );
    }
  });
}

module.exports = { app, deriveAccountId, extractiveSummarize };
