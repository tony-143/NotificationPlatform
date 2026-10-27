/* Documentation: copy buttons, TOC highlighting, and the in-memory Try-it console. */

document.addEventListener('DOMContentLoaded', initDocs);

async function initDocs() {
    document.querySelectorAll('.js-api-base').forEach((node) => {
        node.textContent = API_BASE_URL;
    });

    bindCopyButtons();
    bindToc();
    bindTryout();

    const authenticated = await requireAuth('index.html');
    if (!authenticated) {
        return;
    }
    setPageLoading(false);

    // Never keep a pasted secret across navigations.
    window.addEventListener('pagehide', () => {
        const keyInput = document.getElementById('tryKey');
        if (keyInput) {
            keyInput.value = '';
        }
    });
}

/* ---------- copy buttons ---------- */

function bindCopyButtons() {
    document.querySelectorAll('.code-block .copy').forEach((button) => {
        button.addEventListener('click', async () => {
            const block = button.closest('.code-block');
            const pre = block ? block.querySelector('pre') : null;
            if (!pre) {
                return;
            }
            const text = pre.textContent;
            const original = button.textContent;
            const ok = await copyText(text);
            button.textContent = ok ? 'Copied' : 'Copy failed';
            window.setTimeout(() => {
                button.textContent = original;
            }, 1500);
        });
    });
}

/* ---------- table of contents ---------- */

function bindToc() {
    const links = [...document.querySelectorAll('.docs-toc a')];
    const entries = links
        .map((link) => ({ link, section: document.querySelector(link.getAttribute('href')) }))
        .filter((entry) => entry.section);

    if (entries.length === 0) {
        return;
    }

    let ticking = false;

    const update = () => {
        ticking = false;
        const offset = 110;
        let active = entries[0];
        for (const entry of entries) {
            if (entry.section.getBoundingClientRect().top <= offset) {
                active = entry;
            }
        }
        links.forEach((link) => link.classList.toggle('is-active', link === active.link));
    };

    window.addEventListener(
        'scroll',
        () => {
            if (!ticking) {
                ticking = true;
                window.requestAnimationFrame(update);
            }
        },
        { passive: true }
    );

    update();
}

/* ---------- try it ---------- */

function bindTryout() {
    const keyInput = document.getElementById('tryKey');
    const toggle = document.getElementById('toggleKey');
    const form = document.getElementById('tryForm');

    document.getElementById('tryIdem').value = newIdempotencyKey();

    toggle.addEventListener('click', () => {
        const showing = keyInput.type === 'text';
        keyInput.type = showing ? 'password' : 'text';
        toggle.textContent = showing ? 'Show' : 'Hide';
        keyInput.focus();
    });

    document.getElementById('regenIdem').addEventListener('click', () => {
        document.getElementById('tryIdem').value = newIdempotencyKey();
    });

    document.getElementById('tryReset').addEventListener('click', () => {
        form.reset();
        keyInput.value = '';
        keyInput.type = 'password';
        toggle.textContent = 'Show';
        document.getElementById('tryIdem').value = newIdempotencyKey();
        document.getElementById('tryResult').hidden = true;
    });

    form.addEventListener('submit', sendTestNotification);
}

function newIdempotencyKey() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        return window.crypto.randomUUID();
    }
    return `test-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
}

async function sendTestNotification(event) {
    event.preventDefault();

    const button = document.getElementById('trySubmit');
    const key = document.getElementById('tryKey').value.trim();
    const type = document.getElementById('tryType').value;
    const recipient = document.getElementById('tryRecipient').value.trim();
    const message = document.getElementById('tryMessage').value;
    const payload = document.getElementById('tryPayload').value.trim();
    const scheduled = document.getElementById('tryScheduled').value;
    const idempotencyKey = document.getElementById('tryIdem').value.trim();

    if (!key) {
        showTryError('Enter an API key before sending.');
        document.getElementById('tryKey').focus();
        return;
    }
    if (!recipient || !message.trim()) {
        showTryError('Recipient and message are required.');
        return;
    }
    if (!idempotencyKey) {
        showTryError('An Idempotency-Key is required.');
        return;
    }

    const body = { type, recipient, message };
    if (payload) {
        body.payload = payload;
    }
    if (scheduled) {
        const when = new Date(scheduled);
        if (Number.isNaN(when.getTime())) {
            showTryError('The scheduled time is invalid.');
            return;
        }
        body.scheduledAt = when.toISOString();
    }

    setButtonLoading(button, true, 'Sending…');
    const started = performance.now();

    try {
        const response = await fetch(`${API_BASE_URL}/api/notifications`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-API-Key': key,
                'Idempotency-Key': idempotencyKey
            },
            body: JSON.stringify(body)
        });

        const elapsed = Math.round(performance.now() - started);
        const text = await response.text();
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch {
                data = text;
            }
        }
        renderTryResult(response.status, data, elapsed);
    } catch (error) {
        showTryError(`Network error: ${error.message}. Is the backend running?`);
    } finally {
        setButtonLoading(button, false);
    }
}

function renderTryResult(status, data, elapsed) {
    const wrap = document.getElementById('tryResult');
    const badge = document.getElementById('tryStatus');
    const meta = document.getElementById('tryMeta');
    const body = document.getElementById('tryBody');

    const ok = status >= 200 && status < 300;
    badge.className = `badge ${ok ? 'badge-active' : 'badge-failed'}`;
    badge.textContent = `HTTP ${status}`;

    let details = `${elapsed} ms`;
    if (data && typeof data === 'object' && data.notification) {
        details += ` · notification #${data.notification.id} · status ${data.notification.status}`;
        if (data.duplicate) {
            details += ' · duplicate request (no new notification created)';
        }
    }
    if (data && typeof data === 'object' && data.message && !ok) {
        details += ` · ${data.message}`;
    }
    meta.textContent = details;

    body.textContent =
        data === null ? '(empty response body)' : typeof data === 'string' ? data : JSON.stringify(data, null, 2);

    wrap.hidden = false;
    wrap.classList.toggle('is-error', !ok);
    wrap.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
}

function showTryError(message) {
    const wrap = document.getElementById('tryResult');
    const badge = document.getElementById('tryStatus');
    const meta = document.getElementById('tryMeta');
    const body = document.getElementById('tryBody');

    badge.className = 'badge badge-failed';
    badge.textContent = 'Request not sent';
    meta.textContent = '';
    body.textContent = message;
    wrap.hidden = false;
    wrap.classList.add('is-error');
}
