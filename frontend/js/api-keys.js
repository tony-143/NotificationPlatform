/* API Keys: create / rotate / revoke a single per-account credential.
   The secret lives in this module's memory only while the reveal dialog is open. */

let currentKey = null;
let revealedSecret = null;
let confirmHandler = null;
let keyBusy = false;
const overlayReturnFocus = new WeakMap();

document.addEventListener('DOMContentLoaded', initApiKeys);

async function initApiKeys() {
    bindKeyEvents();

    const authenticated = await requireAuth('index.html');
    if (!authenticated) {
        return;
    }

    setPageLoading(false);
    await loadKey();
}

/* ---------- data ---------- */

async function loadKey() {
    const card = document.getElementById('keyCard');
    card.setAttribute('aria-busy', 'true');

    try {
        currentKey = await api.get('/api/keys');
        renderKey();
    } catch (error) {
        if (error.isAuth) {
            window.location.replace('index.html?reason=auth');
            return;
        }
        card.innerHTML = `<div class="empty">
            <h3>Could not load your credential</h3>
            <p>${escapeHtml(error.message)}</p>
            <button class="btn btn-ghost btn-sm" type="button" id="keyRetry">Retry</button>
        </div>`;
        card.querySelector('#keyRetry').addEventListener('click', loadKey);
    } finally {
        card.setAttribute('aria-busy', 'false');
    }
}

/* ---------- rendering ---------- */

function renderKey() {
    const card = document.getElementById('keyCard');
    const headCreate = document.getElementById('headCreateBtn');

    if (!currentKey) {
        headCreate.hidden = false;
        card.innerHTML = `<div class="empty">
            <svg width="34" height="34" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="8" cy="12" r="4" stroke="currentColor" stroke-width="1.5"/>
                <path d="M12 12h9M17 12v3M20 12v2.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            <h3>No API key yet</h3>
            <p>Create a key to authenticate requests to the notification API. You will see the secret once.</p>
            <button class="btn btn-primary" type="button" id="createKeyBtn">Create API key</button>
        </div>`;
        card.querySelector('#createKeyBtn').addEventListener('click', openCreateModal);
        return;
    }

    const status = String(currentKey.status || '').toUpperCase();
    const active = status === 'ACTIVE';
    headCreate.hidden = active;

    const actions = active
        ? `<button class="btn btn-secondary btn-sm" type="button" id="rotateBtn">Rotate</button>
           <button class="btn btn-danger btn-sm" type="button" id="revokeBtn">Revoke</button>`
        : `<button class="btn btn-primary btn-sm" type="button" id="replaceBtn">Generate replacement key</button>`;

    card.innerHTML = `
        <div class="key-card">
            <div>
                <div class="key-identity">
                    <div class="key-icon" aria-hidden="true">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                            <circle cx="8" cy="12" r="4" stroke="currentColor" stroke-width="1.6"/>
                            <path d="M12 12h9M17 12v3M20 12v2.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
                        </svg>
                    </div>
                    <div style="min-width:0">
                        <div class="key-name">
                            ${escapeHtml(currentKey.name || 'Default key')}
                            ${statusBadge(status)}
                        </div>
                        <div class="key-prefix">${escapeHtml(currentKey.prefix || '')}••••••••••••••••</div>
                    </div>
                </div>
                <div class="key-meta">
                    <div><span>Key ID</span><strong>#${currentKey.id}</strong></div>
                    <div><span>Created</span><strong>${formatDateTime(currentKey.createdAt)}</strong></div>
                    <div><span>Last used</span><strong>${currentKey.lastUsedAt ? formatRelative(currentKey.lastUsedAt) : 'Never'}</strong></div>
                    <div><span>Expires</span><strong>${currentKey.expiresAt ? formatDate(currentKey.expiresAt) : 'No expiry'}</strong></div>
                </div>
            </div>
            <div class="key-actions">${actions}</div>
        </div>
        ${
            active
                ? ''
                : `<div class="panel-body" style="border-top:1px solid var(--line);padding-top:14px">
                    <div class="portal-alert error" style="margin:0">
                        This key is revoked and can no longer authenticate. Requests that still use it return
                        <code class="inline">401 Unauthorized</code>.
                    </div>
                  </div>`
        }`;

    if (active) {
        card.querySelector('#rotateBtn').addEventListener('click', () => openRotateConfirm());
        card.querySelector('#revokeBtn').addEventListener('click', () => openRevokeConfirm());
    } else {
        card.querySelector('#replaceBtn').addEventListener('click', openCreateModal);
    }
}

function setKeyAlert(kind, message) {
    const alert = document.getElementById('keyAlert');
    if (!kind) {
        alert.hidden = true;
        alert.replaceChildren();
        return;
    }
    alert.className = `portal-alert ${kind}`;
    alert.textContent = message;
    alert.hidden = false;
}

/* ---------- create ---------- */

function openCreateModal() {
    const modal = document.getElementById('createModal');
    document.getElementById('createForm').reset();
    openOverlay(modal, 'keyName');
}

async function submitCreate(event) {
    event.preventDefault();
    if (keyBusy) {
        return;
    }

    const button = document.getElementById('createSubmit');
    const name = document.getElementById('keyName').value.trim();
    keyBusy = true;
    setButtonLoading(button, true, 'Creating…');
    setKeyAlert(null);

    try {
        const issued = await api.post('/api/keys', { name: name || undefined });
        closeOverlay(document.getElementById('createModal'));
        showSecret(issued, 'created');
        await loadKey();
    } catch (error) {
        if (error.isAuth) {
            window.location.replace('index.html?reason=auth');
            return;
        }
        setKeyAlert('error', error.message);
    } finally {
        keyBusy = false;
        setButtonLoading(button, false);
    }
}

function showSecret(issued, action) {
    if (!issued || !issued.apiKey) {
        setKeyAlert('error', 'The server did not return a secret. Please try again.');
        return;
    }
    revealedSecret = issued.apiKey;
    document.getElementById('secretTitle').textContent =
        action === 'rotated' ? 'API key rotated' : 'API key created';
    document.getElementById('secretValue').textContent = issued.apiKey;
    document.getElementById('secretMeta').textContent = `Prefix ${issued.prefix} · created ${formatDateTime(
        issued.createdAt
    )}`;
    openOverlay(document.getElementById('secretModal'), 'copySecret');
}

/* ---------- rotate / revoke ---------- */

function openRotateConfirm() {
    if (!currentKey) {
        return;
    }
    openConfirm({
        title: 'Rotate API key',
        body: `<p>The current secret for <strong>${escapeHtml(
            currentKey.name || 'this key'
        )}</strong> stops working as soon as you confirm.</p>
        <p>Any service still using the old secret will receive
        <code class="inline">401 Unauthorized</code>. The new secret is shown once.</p>`,
        actionLabel: 'Rotate key',
        danger: false,
        onConfirm: async () => {
            const issued = await api.post(`/api/keys/${currentKey.id}/rotate`);
            closeOverlay(document.getElementById('confirmModal'));
            showSecret(issued, 'rotated');
            setKeyAlert('success', 'Key rotated. The previous secret is no longer valid.');
            await loadKey();
        }
    });
}

function openRevokeConfirm() {
    if (!currentKey) {
        return;
    }
    openConfirm({
        title: 'Revoke API key',
        body: `<p>Revoking <strong>${escapeHtml(
            currentKey.name || 'this key'
        )}</strong> immediately disables API access for this account.</p>
        <p>Existing notifications remain visible in the dashboard. The credential record is kept for audit
        and can be replaced later.</p>
        <p class="muted-note">This cannot be undone — you would need to generate a new key.</p>`,
        actionLabel: 'Revoke key',
        danger: true,
        onConfirm: async () => {
            await api.post(`/api/keys/${currentKey.id}/revoke`);
            closeOverlay(document.getElementById('confirmModal'));
            setKeyAlert('success', 'Key revoked. Requests using it now fail with 401.');
            await loadKey();
        }
    });
}

function openConfirm({ title, body, actionLabel, danger, onConfirm }) {
    document.getElementById('confirmTitle').textContent = title;
    document.getElementById('confirmBody').innerHTML = body;

    const action = document.getElementById('confirmAction');
    action.textContent = actionLabel;
    action.className = `btn ${danger ? 'btn-danger' : 'btn-primary'}`;

    confirmHandler = onConfirm;
    openOverlay(document.getElementById('confirmModal'), 'confirmAction');
}

async function runConfirm() {
    if (!confirmHandler || keyBusy) {
        return;
    }
    const action = document.getElementById('confirmAction');
    const handler = confirmHandler;
    keyBusy = true;
    setButtonLoading(action, true, 'Working…');

    try {
        await handler();
        confirmHandler = null;
    } catch (error) {
        if (error.isAuth) {
            window.location.replace('index.html?reason=auth');
            return;
        }
        const body = document.getElementById('confirmBody');
        const existing = body.querySelector('.portal-alert');
        if (existing) {
            existing.remove();
        }
        const alert = document.createElement('div');
        alert.className = 'portal-alert error';
        alert.setAttribute('role', 'alert');
        alert.textContent = error.message;
        body.append(alert);
    } finally {
        keyBusy = false;
        setButtonLoading(action, false);
    }
}

/* ---------- overlays ---------- */

function openOverlay(overlay, focusId) {
    overlayReturnFocus.set(overlay, document.activeElement);
    overlay.hidden = false;
    document.body.classList.add('no-scroll');

    const target = focusId ? document.getElementById(focusId) : null;
    if (target) {
        target.focus();
    } else {
        const first = overlay.querySelector('button, input, a[href]');
        if (first) {
            first.focus();
        }
    }
}

function closeOverlay(overlay) {
    if (!overlay || overlay.hidden) {
        return;
    }
    overlay.hidden = true;

    if (overlay.id === 'secretModal') {
        revealedSecret = null;
        document.getElementById('secretValue').textContent = '';
        document.getElementById('secretMeta').textContent = '';
    }

    if (!document.querySelector('.overlay:not([hidden])')) {
        document.body.classList.remove('no-scroll');
    }

    const previous = overlayReturnFocus.get(overlay);
    if (previous && typeof previous.focus === 'function') {
        previous.focus();
    }
    overlayReturnFocus.delete(overlay);
}

function closeTopOverlay() {
    const open = [...document.querySelectorAll('.overlay:not([hidden])')].pop();
    if (open) {
        closeOverlay(open);
    }
}

function trapOverlayFocus(event) {
    if (event.key !== 'Tab') {
        return;
    }
    const open = [...document.querySelectorAll('.overlay:not([hidden])')].pop();
    if (!open) {
        return;
    }
    const focusable = open.querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])'
    );
    if (!focusable.length) {
        return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
    }
}

/* ---------- events ---------- */

function bindKeyEvents() {
    document.getElementById('headCreateBtn').addEventListener('click', openCreateModal);
    document.getElementById('createForm').addEventListener('submit', submitCreate);
    document.getElementById('confirmAction').addEventListener('click', runConfirm);

    document.getElementById('copySecret').addEventListener('click', async (event) => {
        if (!revealedSecret) {
            return;
        }
        await copyWithFeedback(event.currentTarget, revealedSecret, 'Copied');
    });

    document.querySelectorAll('.overlay').forEach((overlay) => {
        overlay.addEventListener('click', (event) => {
            if (event.target === overlay && !keyBusy) {
                closeOverlay(overlay);
            }
        });
        overlay.querySelectorAll('[data-close]').forEach((button) => {
            button.addEventListener('click', () => {
                if (!keyBusy) {
                    closeOverlay(overlay);
                }
            });
        });
    });

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && !keyBusy) {
            closeTopOverlay();
        }
        trapOverlayFocus(event);
    });
}
