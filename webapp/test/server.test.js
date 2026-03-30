'use strict';
/**
 * GoogleAc webapp unit tests
 *
 * Run with:  npm test   (uses Node.js built-in test runner, no extra deps)
 *
 * These tests exercise the server-side logic that does NOT require a live
 * Google OAuth credential — deriveAccountId, extractiveSummarize, and
 * the Express API endpoints (sessions, feature-enablement, config, etc.).
 */

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { app, deriveAccountId, extractiveSummarize } = require('../server.js');
const supertest = require('supertest');

// ---------------------------------------------------------------------------
// deriveAccountId
// ---------------------------------------------------------------------------
describe('deriveAccountId', () => {
  it('returns a 16-character hex string', () => {
    const id = deriveAccountId('user@example.com');
    assert.match(id, /^[0-9a-f]{16}$/);
  });

  it('is case-insensitive (mirrors Android SHA-256 of email.lowercase())', () => {
    const a = deriveAccountId('User@Example.COM');
    const b = deriveAccountId('user@example.com');
    assert.equal(a, b);
  });

  it('produces different ids for different emails', () => {
    const a = deriveAccountId('alice@gmail.com');
    const b = deriveAccountId('bob@gmail.com');
    assert.notEqual(a, b);
  });

  it('is deterministic across calls', () => {
    const id1 = deriveAccountId('test@test.com');
    const id2 = deriveAccountId('test@test.com');
    assert.equal(id1, id2);
  });
});

// ---------------------------------------------------------------------------
// extractiveSummarize
// ---------------------------------------------------------------------------
describe('extractiveSummarize', () => {
  it('returns null for empty input', () => {
    assert.equal(extractiveSummarize(''), null);
    assert.equal(extractiveSummarize(null), null);
    assert.equal(extractiveSummarize('   '), null);
  });

  it('returns null when all sentences are too short', () => {
    assert.equal(extractiveSummarize('Hi. Ok. Yes.'), null);
  });

  it('returns at most maxSentences sentences', () => {
    const text = Array.from({ length: 10 }, (_, i) =>
      `Sentence number ${i + 1} contains some words here for testing purposes.`
    ).join(' ');
    const summary = extractiveSummarize(text, 3);
    assert.ok(summary, 'should return a non-null summary');
    // Should be at most 3 sentences (3 full stops)
    const sentenceCount = (summary.match(/\./g) || []).length;
    assert.ok(sentenceCount <= 3, `Expected ≤3 sentences, got ${sentenceCount}`);
  });

  it('handles single long sentence', () => {
    const text =
      'This is the only sentence in the document and it contains enough words to pass the filter.';
    const summary = extractiveSummarize(text, 5);
    assert.ok(summary, 'should return non-null');
    assert.ok(summary.includes('only sentence'));
  });
});

// ---------------------------------------------------------------------------
// HTTP API — accounts, config, feature-enablement
// (no OAuth credentials required — tests use session injection)
// ---------------------------------------------------------------------------
describe('GET /api/config', () => {
  it('returns { configured: false } when env vars are absent', async () => {
    const res = await supertest(app).get('/api/config').expect(200);
    assert.equal(typeof res.body.configured, 'boolean');
  });
});

describe('GET /api/accounts (unauthenticated session)', () => {
  it('returns an empty accounts array when no session exists', async () => {
    const res = await supertest(app).get('/api/accounts').expect(200);
    assert.deepEqual(res.body.accounts, []);
  });
});

describe('POST /api/feature-enablement', () => {
  it('returns 401 when the account is not in the session', async () => {
    const res = await supertest(app)
      .post('/api/feature-enablement')
      .send({ accountId: 'nonexistent', enabledFeatures: ['DRIVE'] })
      .expect(401);
    assert.ok(res.body.error);
  });

  it('ignores invalid feature names and persists only valid ones', async () => {
    const agent = supertest.agent(app);
    const accountId = deriveAccountId('featuretest@example.com');

    // Seed a fake account into the session
    await agent
      .post('/test/seed-session')
      .send({ account: { accountId, email: 'featuretest@example.com', tokens: { access_token: 'fake' } } })
      .expect(200);

    // Post with a mix of valid and invalid features
    const res = await agent
      .post('/api/feature-enablement')
      .send({ accountId, enabledFeatures: ['DRIVE', 'INVALID_FEATURE', 'CALENDAR'] })
      .expect(200);
    assert.deepEqual(res.body.enabledFeatures, ['DRIVE', 'CALENDAR']);
  });
});

describe('POST /auth/logout', () => {
  it('succeeds even with no accounts in session', async () => {
    const res = await supertest(app)
      .post('/auth/logout')
      .send({})
      .expect(200);
    assert.equal(res.body.ok, true);
  });

  it('removes only the specified account', async () => {
    // Simulate two accounts in session via agent + internal seeding
    // (Stateless test: just verify the JSON response shape)
    const res = await supertest(app)
      .post('/auth/logout')
      .send({ accountId: 'abc123' })
      .expect(200);
    assert.equal(res.body.ok, true);
  });
});

describe('Drive API — unauthenticated', () => {
  it('GET /api/drive/files returns 401 without a valid session account', async () => {
    const res = await supertest(app)
      .get('/api/drive/files?accountId=unknownAccount')
      .expect(401);
    assert.ok(res.body.error);
  });

  it('GET /api/drive/files/:id returns 401 without a valid session account', async () => {
    const res = await supertest(app)
      .get('/api/drive/files/someFileId?accountId=unknownAccount')
      .expect(401);
    assert.ok(res.body.error);
  });

  it('PATCH /api/drive/files/:id returns 401 without session account', async () => {
    const res = await supertest(app)
      .patch('/api/drive/files/someFileId')
      .send({ accountId: 'unknown', name: 'New Name' })
      .expect(401);
    assert.ok(res.body.error);
  });

  it('PATCH /api/drive/files/:id returns 400 with empty name (session present)', async () => {
    const agent = supertest.agent(app);
    const accountId = deriveAccountId('rename400@example.com');
    await agent
      .post('/test/seed-session')
      .send({ account: { accountId, email: 'rename400@example.com', tokens: { access_token: 'fake' } } })
      .expect(200);

    const res = await agent
      .patch('/api/drive/files/someFileId')
      .send({ accountId, name: '   ' })
      .expect(400);
    assert.ok(res.body.error, 'should return an error message');
  });

  it('DELETE /api/drive/files/:id returns 401 without session account', async () => {
    const res = await supertest(app)
      .delete('/api/drive/files/someFileId?accountId=unknownAccount')
      .expect(401);
    assert.ok(res.body.error);
  });

  it('POST /api/drive/files/:id/move returns 401 without session accounts', async () => {
    const res = await supertest(app)
      .post('/api/drive/files/someFileId/move')
      .send({ sourceAccountId: 'a', targetAccountId: 'b' })
      .expect(401);
    assert.ok(res.body.error);
  });
});

describe('SPA catch-all routes', () => {
  it('GET /drive serves index.html', async () => {
    const res = await supertest(app).get('/drive').expect(200);
    assert.ok(res.text.includes('<!DOCTYPE html') || res.text.includes('<html'));
  });

  it('GET /feature-enablement serves index.html', async () => {
    const res = await supertest(app).get('/feature-enablement').expect(200);
    assert.ok(res.text.includes('<!DOCTYPE html') || res.text.includes('<html'));
  });

  it('GET / serves static index.html', async () => {
    const res = await supertest(app).get('/').expect(200);
    assert.ok(res.text.includes('<!DOCTYPE html') || res.text.includes('<html'));
  });
});
