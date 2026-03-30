/* ── GoogleAc Web App ─────────────────────────────────────────────────────── */
'use strict';

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------
const state = {
  accounts: [],         // [{ accountId, email, displayName, badge, enabledFeatures, … }]
  accountOrder: [],
  activeAccountId: null,
  allFiles: [],         // flat array of { ...driveFile, accountId, badge, badgeIndex }
  filteredFiles: [],
  selectedFile: null,
  selectedFileAccountId: null,
  mimeFilter: '',
  searchQuery: '',
  nextPageTokens: {},   // accountId → token
  isLoadingFiles: false,
  pendingAccountId: null,  // accountId being onboarded (feature-enablement step)
};

const BADGE_COLORS = ['badge-0', 'badge-1', 'badge-2', 'badge-3', 'badge-4', 'badge-5'];

// Mime type → emoji icon
const MIME_ICONS = {
  'application/vnd.google-apps.folder':       { icon: '📁', bg: '#FFF8E1' },
  'application/vnd.google-apps.document':     { icon: '📝', bg: '#E8F5E9' },
  'application/vnd.google-apps.spreadsheet':  { icon: '📊', bg: '#E3F2FD' },
  'application/vnd.google-apps.presentation': { icon: '📽️',  bg: '#F3E5F5' },
  'application/pdf':                          { icon: '📄', bg: '#FFEBEE' },
  'image/jpeg':                               { icon: '🖼️',  bg: '#E0F7FA' },
  'image/png':                                { icon: '🖼️',  bg: '#E0F7FA' },
  'video/mp4':                                { icon: '🎬', bg: '#FBE9E7' },
};
function mimeIcon(mimeType) {
  return MIME_ICONS[mimeType] || { icon: '📎', bg: '#F5F5F5' };
}

function formatBytes(bytes) {
  if (!bytes) return '—';
  const n = Number(bytes);
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString();
}

// ---------------------------------------------------------------------------
// CSRF token — fetched once and reused for all mutating requests
// ---------------------------------------------------------------------------
let _csrfToken = null;
async function getCsrfToken() {
  if (!_csrfToken) {
    const res = await fetch('/api/csrf-token');
    const data = await res.json();
    _csrfToken = data.csrfToken || '';
  }
  return _csrfToken;
}

// ---------------------------------------------------------------------------
// API helpers
// ---------------------------------------------------------------------------
async function api(method, path, body) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  // Attach CSRF token for state-mutating methods
  if (method !== 'GET') {
    opts.headers['X-CSRF-Token'] = await getCsrfToken();
  }
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch(path, opts);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
  return data;
}

// ---------------------------------------------------------------------------
// Toast
// ---------------------------------------------------------------------------
let toastTimer = null;
function showToast(msg, durationMs = 3000) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, durationMs);
}

// ---------------------------------------------------------------------------
// Screen navigation
// ---------------------------------------------------------------------------
function showScreen(id) {
  for (const s of document.querySelectorAll('.screen')) {
    s.hidden = (s.id !== id);
  }
}

// ---------------------------------------------------------------------------
// Auth screen
// ---------------------------------------------------------------------------
async function initAuthScreen() {
  showScreen('screen-auth');
  const errMsg = document.getElementById('auth-error-msg');
  const notConfigured = document.getElementById('auth-not-configured');

  // Check if server has credentials configured
  try {
    const cfg = await api('GET', '/api/config');
    if (!cfg.configured) {
      notConfigured.hidden = false;
      document.getElementById('btn-sign-in').disabled = true;
    }
  } catch (_) {/* ignore */}

  // Show any OAuth error from redirect
  const params = new URLSearchParams(location.search);
  if (params.has('auth_error')) {
    errMsg.textContent = 'Sign-in failed: ' + params.get('auth_error').replace(/_/g, ' ');
    errMsg.hidden = false;
    history.replaceState(null, '', '/');
  }

  document.getElementById('btn-sign-in').onclick = () => {
    location.href = '/auth/login';
  };
}

// ---------------------------------------------------------------------------
// Feature enablement screen
// ---------------------------------------------------------------------------
async function initFeatureEnablementScreen(accountId) {
  showScreen('screen-feature-enablement');
  state.pendingAccountId = accountId;

  // Find account details
  const account = state.accounts.find((a) => a.accountId === accountId);
  const label = document.getElementById('fe-account-label');
  label.textContent = account ? `${account.email} (${account.badge})` : accountId;

  function getSelected() {
    return [...document.querySelectorAll('#fe-toggle-list input:checked')].map(
      (cb) => cb.name
    );
  }

  document.getElementById('btn-enable-all').onclick = () => {
    document.querySelectorAll('#fe-toggle-list input').forEach((cb) => (cb.checked = true));
  };

  async function saveAndContinue() {
    const enabledFeatures = getSelected();
    try {
      await api('POST', '/api/feature-enablement', { accountId, enabledFeatures });
      // Update local state
      const acc = state.accounts.find((a) => a.accountId === accountId);
      if (acc) acc.enabledFeatures = enabledFeatures;
    } catch (err) {
      showToast('Could not save features: ' + err.message);
    }
    await initDriveScreen();
  }

  document.getElementById('btn-fe-continue').onclick = saveAndContinue;
  document.getElementById('btn-fe-skip').onclick = async () => {
    await api('POST', '/api/feature-enablement', { accountId, enabledFeatures: [] }).catch(() => {});
    await initDriveScreen();
  };
}

// ---------------------------------------------------------------------------
// Drive screen
// ---------------------------------------------------------------------------
async function initDriveScreen() {
  showScreen('screen-drive');

  // Load accounts
  const { accounts, accountOrder } = await api('GET', '/api/accounts');
  state.accounts = accounts;
  state.accountOrder = accountOrder;

  if (!state.activeAccountId && accounts.length > 0) {
    state.activeAccountId = accounts[0].accountId;
  }

  renderAccountChips();

  // Set up controls
  document.getElementById('btn-add-account').onclick = () => {
    location.href = '/auth/login';
  };

  const searchInput = document.getElementById('search-input');
  let debounceTimer;
  searchInput.oninput = () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      state.searchQuery = searchInput.value.trim();
      applyFilters();
    }, 300);
  };

  document.getElementById('filter-chips').onclick = (e) => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    document.querySelectorAll('.chip').forEach((c) => c.classList.remove('chip-active'));
    chip.classList.add('chip-active');
    state.mimeFilter = chip.dataset.mime;
    applyFilters();
  };

  document.getElementById('btn-load-more').onclick = loadMoreFiles;

  // Load files for all accounts
  await loadAllFiles(true);
}

function renderAccountChips() {
  const container = document.getElementById('account-chips');
  container.innerHTML = '';
  state.accounts.forEach((acc, idx) => {
    const btn = document.createElement('button');
    btn.className = 'account-chip-btn' + (acc.accountId === state.activeAccountId ? ' active-account' : '');
    btn.title = acc.email;
    btn.innerHTML = `<span class="chip-dot ${BADGE_COLORS[idx % BADGE_COLORS.length]}"></span>${acc.badge}`;
    btn.onclick = () => {
      state.activeAccountId = acc.accountId;
      renderAccountChips();
    };
    container.appendChild(btn);
  });
}

async function loadAllFiles(reset = false) {
  if (reset) {
    state.allFiles = [];
    state.nextPageTokens = {};
  }
  state.isLoadingFiles = true;
  document.getElementById('file-list-loading').hidden = false;
  document.getElementById('file-list-empty').hidden = true;
  document.getElementById('btn-load-more').hidden = true;

  try {
    await Promise.all(state.accounts.map((acc) => loadFilesForAccount(acc)));
  } finally {
    state.isLoadingFiles = false;
    document.getElementById('file-list-loading').hidden = true;
  }
  applyFilters();
}

async function loadFilesForAccount(account) {
  const idx = state.accounts.findIndex((a) => a.accountId === account.accountId);
  const badgeIndex = idx >= 0 ? idx : 0;

  try {
    const params = new URLSearchParams({ accountId: account.accountId });
    if (state.searchQuery) params.set('q', state.searchQuery);
    if (state.mimeFilter) params.set('mimeType', state.mimeFilter);
    const pt = state.nextPageTokens[account.accountId];
    if (pt) params.set('pageToken', pt);

    const { files, nextPageToken } = await api('GET', `/api/drive/files?${params}`);

    state.nextPageTokens[account.accountId] = nextPageToken || null;

    const tagged = files.map((f) => ({
      ...f,
      accountId: account.accountId,
      badge: account.badge,
      badgeIndex,
    }));
    state.allFiles.push(...tagged);
  } catch (err) {
    console.error('Error loading files for', account.email, err);
    showToast(`Could not load files for ${account.badge}: ${err.message}`);
  }
}

async function loadMoreFiles() {
  const hasMore = state.accounts.some((a) => state.nextPageTokens[a.accountId]);
  if (!hasMore) return;
  await Promise.all(
    state.accounts
      .filter((a) => state.nextPageTokens[a.accountId])
      .map((a) => loadFilesForAccount(a))
  );
  applyFilters();
}

function applyFilters() {
  let files = state.allFiles;

  if (state.mimeFilter) {
    files = files.filter((f) => f.mimeType === state.mimeFilter);
  }

  if (state.searchQuery) {
    const q = state.searchQuery.toLowerCase();
    files = files.filter((f) => (f.name || '').toLowerCase().includes(q));
  }

  state.filteredFiles = files;
  renderFileList();
}

function renderFileList() {
  const ul = document.getElementById('file-list');
  const empty = document.getElementById('file-list-empty');
  const loadMore = document.getElementById('btn-load-more');
  ul.innerHTML = '';

  if (state.filteredFiles.length === 0) {
    empty.hidden = false;
    return;
  }
  empty.hidden = true;

  for (const file of state.filteredFiles) {
    const li = document.createElement('li');
    li.className = 'file-item' + (file.id === state.selectedFile?.id && file.accountId === state.selectedFileAccountId ? ' selected' : '');
    li.dataset.fileId = file.id;
    li.dataset.accountId = file.accountId;

    const { icon, bg } = mimeIcon(file.mimeType);
    const badgeColorClass = BADGE_COLORS[file.badgeIndex % BADGE_COLORS.length];

    li.innerHTML = `
      <div class="file-mime-icon" style="background:${bg}" aria-hidden="true">${icon}</div>
      <div class="file-name" title="${escHtml(file.name)}">${escHtml(file.name)}</div>
      <div class="file-meta">${formatBytes(file.size)}</div>
      <span class="account-badge ${badgeColorClass}">${escHtml(file.badge)}</span>
    `;
    li.onclick = () => selectFile(file);
    ul.appendChild(li);
  }

  const hasMore = state.accounts.some((a) => state.nextPageTokens[a.accountId]);
  loadMore.hidden = !hasMore;
}

// ---------------------------------------------------------------------------
// File detail
// ---------------------------------------------------------------------------
async function selectFile(file) {
  state.selectedFile = file;
  state.selectedFileAccountId = file.accountId;

  // Highlight selected row
  document.querySelectorAll('.file-item').forEach((li) => {
    li.classList.toggle(
      'selected',
      li.dataset.fileId === file.id && li.dataset.accountId === file.accountId
    );
  });

  document.getElementById('detail-placeholder').hidden = true;
  const content = document.getElementById('detail-content');
  content.hidden = false;

  // Basic render immediately from list data
  renderDetailBasic(file);

  // Fetch full metadata in background
  try {
    const { file: fullFile, summary } = await api(
      'GET',
      `/api/drive/files/${encodeURIComponent(file.id)}?accountId=${encodeURIComponent(file.accountId)}`
    );
    renderDetailFull(fullFile, file, summary);
  } catch (err) {
    console.error('Detail fetch error:', err);
  }
}

function renderDetailBasic(file) {
  const badgeColorClass = BADGE_COLORS[file.badgeIndex % BADGE_COLORS.length];
  const { icon, bg } = mimeIcon(file.mimeType);

  const iconEl = document.getElementById('detail-icon');
  iconEl.textContent = icon;
  iconEl.style.background = bg;

  document.getElementById('detail-name').textContent = file.name;
  document.getElementById('detail-account-badge').textContent = file.badge;
  document.getElementById('detail-account-badge').className = `account-badge ${badgeColorClass}`;

  const meta = document.getElementById('detail-meta');
  meta.innerHTML = `
    <dt>MIME type</dt><dd>${escHtml(file.mimeType)}</dd>
    <dt>Size</dt><dd>${formatBytes(file.size)}</dd>
    <dt>Modified</dt><dd>${formatDate(file.modifiedTime)}</dd>
    <dt>Created</dt><dd>${formatDate(file.createdTime)}</dd>
  `;

  renderDetailActions(file, null);
}

function renderDetailFull(fullFile, listFile, summary) {
  // Merge capabilities
  const file = { ...listFile, ...fullFile };
  const meta = document.getElementById('detail-meta');
  let webLink = '';
  if (file.webViewLink) {
    webLink = `<dt>Web link</dt><dd><a href="${escHtml(file.webViewLink)}" target="_blank" rel="noopener">Open in Drive ↗</a></dd>`;
  }
  meta.innerHTML = `
    <dt>MIME type</dt><dd>${escHtml(file.mimeType)}</dd>
    <dt>Size</dt><dd>${formatBytes(file.size)}</dd>
    <dt>Modified</dt><dd>${formatDate(file.modifiedTime)}</dd>
    <dt>Created</dt><dd>${formatDate(file.createdTime)}</dd>
    ${webLink}
  `;

  const summaryWrap = document.getElementById('detail-summary-wrap');
  if (summary) {
    document.getElementById('detail-summary-text').textContent = summary;
    summaryWrap.hidden = false;
  } else {
    summaryWrap.hidden = true;
  }

  renderDetailActions(file, fullFile.capabilities);
}

function renderDetailActions(file, capabilities) {
  const cap = capabilities || {};
  const actionsEl = document.getElementById('detail-actions');
  actionsEl.innerHTML = '';

  // Rename
  if (cap.canRename !== false) {
    const btn = document.createElement('button');
    btn.className = 'btn btn-outlined btn-sm';
    btn.innerHTML = '✏️ Rename';
    btn.onclick = () => openRenameDialog(file);
    actionsEl.appendChild(btn);
  }

  // Delete
  if (cap.canDelete !== false) {
    const btn = document.createElement('button');
    btn.className = 'btn btn-outlined btn-sm';
    btn.innerHTML = '🗑️ Delete';
    btn.onclick = () => openDeleteDialog(file);
    actionsEl.appendChild(btn);
  }

  // Move (only if ≥2 accounts)
  if (state.accounts.length >= 2 && cap.canMoveItemWithinDrive !== false) {
    const btn = document.createElement('button');
    btn.className = 'btn btn-outlined btn-sm';
    btn.innerHTML = '↗ Move';
    btn.onclick = () => openMoveDialog(file);
    actionsEl.appendChild(btn);
  }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

// — Rename —
function openRenameDialog(file) {
  const dlg = document.getElementById('dlg-rename');
  const input = document.getElementById('rename-input');
  input.value = file.name;
  dlg.showModal();
  input.select();

  const cancelBtn = document.getElementById('btn-rename-cancel');
  const form = dlg.querySelector('form');

  const cleanup = () => {
    cancelBtn.removeEventListener('click', onCancel);
    form.removeEventListener('submit', onSubmit);
  };
  const onCancel = () => { dlg.close(); cleanup(); };
  const onSubmit = async (e) => {
    e.preventDefault();
    const newName = input.value.trim();
    if (!newName) return;
    dlg.close();
    cleanup();
    try {
      await api('PATCH', `/api/drive/files/${encodeURIComponent(file.id)}`, {
        accountId: file.accountId,
        name: newName,
      });
      // Update local state
      file.name = newName;
      const listFile = state.allFiles.find((f) => f.id === file.id && f.accountId === file.accountId);
      if (listFile) listFile.name = newName;
      applyFilters();
      document.getElementById('detail-name').textContent = newName;
      showToast(`Renamed to "${newName}"`);
    } catch (err) {
      showToast('Rename failed: ' + err.message);
    }
  };
  cancelBtn.addEventListener('click', onCancel);
  form.addEventListener('submit', onSubmit);
}

// — Delete —
function openDeleteDialog(file) {
  const dlg = document.getElementById('dlg-delete');
  document.getElementById('dlg-delete-msg').textContent =
    `"${file.name}" will be permanently deleted from Google Drive. This cannot be undone.`;
  dlg.showModal();

  const cancelBtn = document.getElementById('btn-delete-cancel');
  const okBtn = document.getElementById('btn-delete-ok');

  const cleanup = () => {
    cancelBtn.removeEventListener('click', onCancel);
    okBtn.removeEventListener('click', onOk);
  };
  const onCancel = () => { dlg.close(); cleanup(); };
  const onOk = async () => {
    dlg.close();
    cleanup();
    try {
      await api('DELETE', `/api/drive/files/${encodeURIComponent(file.id)}?accountId=${encodeURIComponent(file.accountId)}`);
      // Remove from local state
      state.allFiles = state.allFiles.filter((f) => !(f.id === file.id && f.accountId === file.accountId));
      state.selectedFile = null;
      document.getElementById('detail-content').hidden = true;
      document.getElementById('detail-placeholder').hidden = false;
      applyFilters();
      showToast(`"${file.name}" deleted`);
    } catch (err) {
      showToast('Delete failed: ' + err.message);
    }
  };
  cancelBtn.addEventListener('click', onCancel);
  okBtn.addEventListener('click', onOk);
}

// — Move —
function openMoveDialog(file) {
  const dlg = document.getElementById('dlg-move');
  const list = document.getElementById('dlg-move-accounts');
  list.innerHTML = '';

  const otherAccounts = state.accounts.filter((a) => a.accountId !== file.accountId);
  for (const acc of otherAccounts) {
    const btn = document.createElement('button');
    btn.className = 'move-account-btn';
    const idx = state.accounts.findIndex((a) => a.accountId === acc.accountId);
    const colorClass = BADGE_COLORS[idx % BADGE_COLORS.length];
    btn.innerHTML = `<span class="account-badge ${colorClass}">${escHtml(acc.badge)}</span><span>${escHtml(acc.email)}</span>`;
    btn.onclick = async () => {
      dlg.close();
      try {
        const { name: newName } = await api('POST', `/api/drive/files/${encodeURIComponent(file.id)}/move`, {
          sourceAccountId: file.accountId,
          targetAccountId: acc.accountId,
        });
        // Update local state — reassign to target account
        const listFile = state.allFiles.find((f) => f.id === file.id && f.accountId === file.accountId);
        if (listFile) {
          const newIdx = state.accounts.findIndex((a) => a.accountId === acc.accountId);
          listFile.accountId = acc.accountId;
          listFile.badge = acc.badge;
          listFile.badgeIndex = newIdx >= 0 ? newIdx : 0;
        }
        state.selectedFile = null;
        document.getElementById('detail-content').hidden = true;
        document.getElementById('detail-placeholder').hidden = false;
        applyFilters();
        showToast(`"${newName}" moved to ${acc.badge} ✓`);
      } catch (err) {
        showToast('Move failed: ' + err.message);
      }
    };
    list.appendChild(btn);
  }

  dlg.showModal();
  document.getElementById('btn-move-cancel').onclick = () => dlg.close();
}

// ---------------------------------------------------------------------------
// HTML escaping
// ---------------------------------------------------------------------------
function escHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ---------------------------------------------------------------------------
// Router — decide which screen to show based on URL
// ---------------------------------------------------------------------------
async function route() {
  const path = location.pathname;
  const params = new URLSearchParams(location.search);

  // Load accounts (needed for most screens)
  try {
    const { accounts, accountOrder } = await api('GET', '/api/accounts');
    state.accounts = accounts;
    state.accountOrder = accountOrder;
  } catch (_) {
    state.accounts = [];
  }

  if (path === '/feature-enablement') {
    const accountId = params.get('accountId');
    if (!accountId || !state.accounts.find((a) => a.accountId === accountId)) {
      return initAuthScreen();
    }
    return initFeatureEnablementScreen(accountId);
  }

  if (path === '/drive') {
    if (state.accounts.length === 0) return initAuthScreen();
    return initDriveScreen();
  }

  // Default: if already signed in → drive, else → auth
  if (state.accounts.length > 0) {
    history.replaceState(null, '', '/drive');
    return initDriveScreen();
  }
  return initAuthScreen();
}

// ---------------------------------------------------------------------------
// Boot
// ---------------------------------------------------------------------------
route().catch((err) => {
  console.error('Router error:', err);
  document.getElementById('app').innerHTML =
    `<div style="padding:40px;color:#B3261E">Fatal error: ${escHtml(String(err))}</div>`;
});
