// ---------- State ----------
let token = localStorage.getItem('token');
let me = JSON.parse(localStorage.getItem('me') || 'null');   // { email, role }
let mode = 'login';
let photoTargetId = null;

const $ = (id) => document.getElementById(id);

// Escape text before putting it into HTML (prevents XSS)
function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function toast(msg, isError = false) {
  const t = $('toast');
  t.textContent = msg;
  t.className = 'toast' + (isError ? ' err' : '');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.add('hidden'), 3500);
}

// ---------- API helper ----------
function errorMessage(data, status) {
  if (data && data.details) return Object.values(data.details).join(', ');
  if (data && data.error) return data.error;
  if (data && data.message) return data.message;
  if (status === 403) return 'You are not allowed to do this';
  return 'Something went wrong (' + status + ')';
}

async function api(path, options = {}) {
  const headers = options.headers || {};
  if (token) headers['Authorization'] = 'Bearer ' + token;
  if (options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(path, { ...options, headers });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }

  if (res.status === 401 && token) {
    logout();
    throw new Error('Session expired. Please login again.');
  }
  if (!res.ok) throw new Error(errorMessage(data, res.status));
  return data;
}

// ---------- Login / Register ----------
function setMode(m) {
  mode = m;
  $('tabLogin').classList.toggle('active', m === 'login');
  $('tabRegister').classList.toggle('active', m === 'register');
  $('name').classList.toggle('hidden', m === 'login');
  $('name').required = m === 'register';
  $('authSubmit').textContent = m === 'login' ? 'Login' : 'Create account';
  $('authError').textContent = '';
}

async function submitAuth(e) {
  e.preventDefault();
  $('authError').textContent = '';
  token = null;   // never send an old token while logging in

  const email = $('email').value.trim().toLowerCase();
  const password = $('password').value;

  try {
    if (mode === 'register') {
      await api('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({ name: $('name').value.trim(), email, password })
      });
    }
    const data = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
    token = data.token;
    me = { email, role: data.role };
    localStorage.setItem('token', token);
    localStorage.setItem('me', JSON.stringify(me));
    $('authForm').reset();
    showApp();
  } catch (err) {
    $('authError').textContent = err.message;
  }
}

function logout() {
  token = null;
  me = null;
  localStorage.removeItem('token');
  localStorage.removeItem('me');
  $('appView').classList.add('hidden');
  $('authView').classList.remove('hidden');
}

function showApp() {
  $('authView').classList.add('hidden');
  $('appView').classList.remove('hidden');
  $('userInfo').textContent = me.email + ' (' + me.role + ')';
  loadItems();
}

// ---------- Items ----------
async function loadItems() {
  const params = new URLSearchParams();
  const filters = {
    keyword: $('fKeyword').value.trim(),
    category: $('fCategory').value.trim(),
    location: $('fLocation').value.trim(),
    type: $('fType').value,
    status: $('fStatus').value
  };
  for (const [k, v] of Object.entries(filters)) {
    if (v) params.append(k, v);
  }

  try {
    const items = await api('/api/items/search?' + params.toString());
    renderItems(items);
  } catch (err) {
    toast(err.message, true);
  }
}

function renderItems(items) {
  items.sort((a, b) => b.id - a.id);   // newest first
  $('emptyMsg').classList.toggle('hidden', items.length > 0);
  $('itemsGrid').innerHTML = items.map(cardHtml).join('');
}

function cardHtml(it) {
  const isAdmin = me.role === 'ADMIN';
  const isReporter = it.reportedBy && it.reportedBy.email === me.email;
  const canClaim = !isReporter && it.status !== 'RESOLVED';
  const canManage = isReporter || isAdmin;

  const img = it.imageUrl
    ? `<img src="${esc(it.imageUrl)}" alt="${esc(it.title)}">`
    : `<div class="noimg">No photo</div>`;

  const buttons = [];
  if (canClaim) buttons.push(`<button class="btn primary small" data-action="claim" data-id="${it.id}">This is mine</button>`);
  if (canManage) buttons.push(`<button class="btn small" data-action="claims" data-id="${it.id}">View claims</button>`);
  if (canManage) buttons.push(`<button class="btn small" data-action="photo" data-id="${it.id}">${it.imageUrl ? 'Change photo' : 'Add photo'}</button>`);
  if (isAdmin) buttons.push(`<button class="btn danger small" data-action="delete" data-id="${it.id}">Delete</button>`);

  return `
    <div class="card">
      ${img}
      <div class="card-body">
        <span class="badge ${esc(it.type.toLowerCase())}">${esc(it.type)}</span>
        <span class="badge ${esc(it.status.toLowerCase())}">${esc(it.status)}</span>
        <h3>${esc(it.title)}</h3>
        <p>${esc(it.description || '')}</p>
        <p class="muted">📍 ${esc(it.location)} · 🏷️ ${esc(it.category)}</p>
        <p class="muted">Reported by ${esc(it.reportedBy ? it.reportedBy.name : 'unknown')}</p>
      </div>
      <div class="card-actions">${buttons.join('')}</div>
    </div>`;
}

async function onGridClick(e) {
  const btn = e.target.closest('button[data-action]');
  if (!btn) return;
  const id = btn.dataset.id;

  try {
    if (btn.dataset.action === 'claim') {
      const message = prompt('Why is this yours? Describe something only the owner would know:');
      if (message === null) return;   // user pressed Cancel
      await api(`/api/items/${id}/claim`, { method: 'POST', body: JSON.stringify({ message }) });
      toast('Claim sent! The reporter will review it.');
      loadItems();
    } else if (btn.dataset.action === 'claims') {
      openClaims(id);
    } else if (btn.dataset.action === 'photo') {
      photoTargetId = id;
      $('photoInput').click();
    } else if (btn.dataset.action === 'delete') {
      if (!confirm('Delete this item permanently?')) return;
      await api(`/api/items/${id}`, { method: 'DELETE' });
      toast('Item deleted');
      loadItems();
    }
  } catch (err) {
    toast(err.message, true);
  }
}

// ---------- Photo upload for an existing item ----------
async function onPhotoChosen() {
  const file = $('photoInput').files[0];
  if (!file || !photoTargetId) return;
  const fd = new FormData();
  fd.append('file', file);
  try {
    await api(`/api/items/${photoTargetId}/image`, { method: 'POST', body: fd });
    toast('Photo uploaded');
    loadItems();
  } catch (err) {
    toast(err.message, true);
  }
  $('photoInput').value = '';
}

// ---------- Report item ----------
function openReport() {
  $('reportForm').reset();
  $('reportError').textContent = '';
  $('reportModal').classList.remove('hidden');
}

async function submitReport(e) {
  e.preventDefault();
  $('reportError').textContent = '';

  const item = {
    title: $('rTitle').value.trim(),
    description: $('rDescription').value.trim(),
    type: $('rType').value,
    category: $('rCategory').value.trim(),
    location: $('rLocation').value.trim()
  };

  try {
    const saved = await api('/api/items', { method: 'POST', body: JSON.stringify(item) });

    const file = $('rPhoto').files[0];
    if (file) {
      try {
        const fd = new FormData();
        fd.append('file', file);
        await api(`/api/items/${saved.id}/image`, { method: 'POST', body: fd });
      } catch (imgErr) {
        toast('Item saved, but photo failed: ' + imgErr.message, true);
        $('reportModal').classList.add('hidden');
        loadItems();
        return;
      }
    }
    $('reportModal').classList.add('hidden');
    toast('Item reported!');
    loadItems();
  } catch (err) {
    $('reportError').textContent = err.message;
  }
}

// ---------- Claims ----------
async function openClaims(itemId) {
  const list = $('claimsList');
  list.innerHTML = '<p class="muted">Loading...</p>';
  $('claimsModal').classList.remove('hidden');

  try {
    const claims = await api(`/api/items/${itemId}/claims`);
    if (!claims.length) {
      list.innerHTML = '<p class="muted">No claims yet.</p>';
      return;
    }
    list.innerHTML = claims.map((c) => `
      <div class="claim">
        <div>
          <strong>${esc(c.claimedBy.name)}</strong>
          <span class="muted">(${esc(c.claimedBy.email)})</span>
          <span class="badge ${esc(c.status.toLowerCase())}">${esc(c.status)}</span>
        </div>
        <p>${esc(c.message || 'No message')}</p>
        ${c.status === 'PENDING' ? `
          <div class="row">
            <button class="btn small" data-claim="reject" data-id="${c.id}" data-item="${itemId}">Reject</button>
            <button class="btn primary small" data-claim="approve" data-id="${c.id}" data-item="${itemId}">Approve</button>
          </div>` : ''}
      </div>`).join('');
  } catch (err) {
    list.innerHTML = `<p class="error">${esc(err.message)}</p>`;
  }
}

async function onClaimsClick(e) {
  const btn = e.target.closest('button[data-claim]');
  if (!btn) return;
  try {
    await api(`/api/claims/${btn.dataset.id}/${btn.dataset.claim}`, { method: 'PUT' });
    toast('Claim ' + (btn.dataset.claim === 'approve' ? 'approved' : 'rejected'));
    openClaims(btn.dataset.item);
    loadItems();
  } catch (err) {
    toast(err.message, true);
  }
}

// ---------- Wire everything up ----------
$('tabLogin').addEventListener('click', () => setMode('login'));
$('tabRegister').addEventListener('click', () => setMode('register'));
$('authForm').addEventListener('submit', submitAuth);
$('btnLogout').addEventListener('click', logout);
$('btnReport').addEventListener('click', openReport);
$('btnCancelReport').addEventListener('click', () => $('reportModal').classList.add('hidden'));
$('reportForm').addEventListener('submit', submitReport);
$('btnCloseClaims').addEventListener('click', () => $('claimsModal').classList.add('hidden'));
$('claimsList').addEventListener('click', onClaimsClick);
$('itemsGrid').addEventListener('click', onGridClick);
$('photoInput').addEventListener('change', onPhotoChosen);
$('btnSearch').addEventListener('click', loadItems);
$('btnClear').addEventListener('click', () => {
  ['fKeyword', 'fCategory', 'fLocation', 'fType', 'fStatus'].forEach((id) => ($(id).value = ''));
  loadItems();
});
['fKeyword', 'fCategory', 'fLocation'].forEach((id) =>
  $(id).addEventListener('keydown', (e) => { if (e.key === 'Enter') loadItems(); }));

// On page load: go straight to the app if already logged in
if (token && me) showApp();