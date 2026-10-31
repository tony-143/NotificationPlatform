/* API Testing playground.
   Sends real requests to the public notification API with an X-API-Key header.
   The key lives in this module's memory only: never localStorage, sessionStorage,
   cookies, logs, or the request history. */

const PG_HISTORY_LIMIT = 25;
const PG_DEFAULT_PATH = '/api/v1/notifications';

const pgState = {
    channel: 'EMAIL',
    sending: false,
    savedKey: null,
    history: [],
    selectedHistoryId: null,
    sequence: 0,
    lastCopyable: ''
};

document.addEventListener('DOMContentLoaded', pgInit);

async function pgInit() {
    pgSetDefaults();
    pgBindEvents();
    pgUpdateChannelUI();
    pgUpdateMethodUI();
    pgUpdateUrlPreview();
    pgRenderHistory();

    const authenticated = await requireAuth('index.html');
    if (!authenticated) {
        return;
    }

    setPageLoading(false);
    await pgLoadSavedKey();

    // Drop the secret as soon as this document goes away.
    window.addEventListener('pagehide', () => {
        pgState.savedKey = null;
        const input = document.getElementById('apiKeyInput');
        if (input) {
            input.value = '';
        }
    });
}

/* ---------- defaults ---------- */

function pgSetDefaults() {
    document.getElementById('baseUrlInput').value = API_BASE_URL;
    document.getElementById('pathInput').value = PG_DEFAULT_PATH;
    document.getElementById('idempotencyInput').value = pgNewIdempotencyKey();

    const schedule = document.getElementById('scheduleAt');
    const now = new Date(Date.now() + 5 * 60000);
    now.setSeconds(0, 0);
    schedule.min = pgToLocalInputValue(now);
    schedule.value = pgToLocalInputValue(now);

    pgUpdateMessageCount();
}

function pgNewIdempotencyKey() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        return window.crypto.randomUUID();
    }
    const random = Math.random().toString(16).slice(2, 10);
    return `pg-${Date.now()}-${random}`;
}

function pgToLocalInputValue(date) {
    const pad = (value) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
        date.getHours()
    )}:${pad(date.getMinutes())}`;
}

/* ---------- saved credential metadata ---------- */

async function pgLoadSavedKey() {
    const select = document.getElementById('savedKeySelect');
    const hint = document.getElementById('savedKeyHint');

    try {
        const key = await api.get('/api/keys');
        if (!key) {
            hint.textContent = 'No credential exists yet. Create one on the API Keys page.';
            return;
        }

        pgState.savedKey = key;
        const status = String(key.status || '').toUpperCase();
        const option = document.createElement('option');
        option.value = String(key.id);
        option.textContent = `${key.name || 'Default key'} · ${key.prefix}…${
            status === 'ACTIVE' ? '' : ' (revoked)'
        }`;
        select.append(option);

        hint.textContent =
            status === 'ACTIVE'
                ? `“${key.name || 'Default key'}” is active. Its secret is only shown once — paste it above.`
                : `“${key.name || 'Default key'}” is revoked. Paste a replacement secret above.`;
    } catch (error) {
        if (error.isAuth) {
            window.location.replace('index.html?reason=auth');
            return;
        }
        hint.textContent = 'Could not load key metadata. Paste a key manually.';
    }
}

/* ---------- url / method ---------- */

function pgJoinUrl(base, path) {
    const cleanBase = (base || '').trim().replace(/\/+$/, '');
    let cleanPath = (path || '').trim();
    if (!cleanPath) {
        return cleanBase;
    }
    if (!cleanPath.startsWith('/')) {
        cleanPath = `/${cleanPath}`;
    }
    return `${cleanBase}${cleanPath}`;
}

function pgCurrentUrl() {
    return pgJoinUrl(
        document.getElementById('baseUrlInput').value,
        document.getElementById('pathInput').value
    );
}

function pgUpdateUrlPreview() {
    const method = document.getElementById('methodSelect').value;
    const url = pgCurrentUrl();
    document.getElementById('urlPreview').value = url;

    const note = document.getElementById('sendNote');
    if (method === 'GET') {
        note.textContent = url ? `GET ${url} · history is returned newest first` : '';
    } else {
        note.textContent = url
            ? `POST ${url} · JSON body · Idempotency-Key header`
            : '';
    }
}

function pgUpdateMethodUI() {
    const select = document.getElementById('methodSelect');
    const method = select.value;
    const isPost = method === 'POST';
    const panel = document.getElementById('notificationPanel');

    select.dataset.method = method;
    panel.classList.toggle('is-inactive', !isPost);
    panel.querySelectorAll('input, textarea, select').forEach((element) => {
        element.disabled = !isPost;
    });

    document.getElementById('methodNotice').hidden = isPost;
    document.getElementById('advancedSection').hidden = !isPost;
    document.getElementById('clearFormBtn').disabled = !isPost;
}

function pgUpdateChannelUI() {
    const checked = document.querySelector('input[name="channel"]:checked');
    const channel = checked ? checked.value : 'EMAIL';
    pgState.channel = channel;

    document.getElementById('emailFields').hidden = channel !== 'EMAIL';
    document.getElementById('smsFields').hidden = channel !== 'SMS';
    document.getElementById('recipientEmail').required = channel === 'EMAIL';
    document.getElementById('recipientPhone').required = channel === 'SMS';
}

/* ---------- field helpers ---------- */

function pgUpdateMessageCount() {
    const value = document.getElementById('messageInput').value;
    document.getElementById('messageCount').textContent = `${value.length} / 10000`;
}

function pgUpdatePayloadStatus() {
    const status = document.getElementById('payloadStatus');
    const raw = document.getElementById('payloadInput').value.trim();

    if (!raw) {
        status.textContent = '';
        status.className = 'json-status';
        return;
    }

    try {
        const parsed = JSON.parse(raw);
        status.textContent = Array.isArray(parsed) ? 'Valid JSON array' : 'Valid JSON';
        status.className = 'json-status ok';
    } catch {
        status.textContent = 'Invalid JSON';
        status.className = 'json-status bad';
    }
}

function pgUpdateAdvancedSummary() {
    const summary = document.getElementById('advancedSummary');
    const parts = [];

    if (document.getElementById('scheduleToggle').checked) {
        parts.push('Scheduled');
    }
    if (document.getElementById('payloadInput').value.trim()) {
        parts.push('Payload');
    }
    summary.textContent = parts.length ? parts.join(' · ') : '';
}

function pgSetAdvanced(open) {
    const toggle = document.getElementById('advancedToggle');
    const body = document.getElementById('advancedBody');
    toggle.setAttribute('aria-expanded', String(open));
    body.hidden = !open;
}

function pgFormAlert(kind, message) {
    const alert = document.getElementById('formAlert');
    if (!kind) {
        alert.hidden = true;
        alert.replaceChildren();
        return;
    }
    alert.className = `portal-alert ${kind}`;
    alert.textContent = message;
    alert.hidden = false;
}

/* ---------- draft ---------- */

function pgReadForm() {
    const method = document.getElementById('methodSelect').value;
    const base = document.getElementById('baseUrlInput').value.trim();
    const path = document.getElementById('pathInput').value.trim();
    const channel = pgState.channel;
    const scheduleEnabled = document.getElementById('scheduleToggle').checked;

    return {
        method,
        base,
        path,
        url: pgJoinUrl(base, path),
        key: document.getElementById('apiKeyInput').value.trim(),
        channel,
        recipient:
            channel === 'EMAIL'
                ? document.getElementById('recipientEmail').value.trim()
                : document.getElementById('recipientPhone').value.trim(),
        subject: document.getElementById('subjectInput').value.trim(),
        message: document.getElementById('messageInput').value,
        payloadRaw: document.getElementById('payloadInput').value.trim(),
        scheduleEnabled,
        scheduleAt: scheduleEnabled ? document.getElementById('scheduleAt').value : '',
        idempotencyKey: document.getElementById('idempotencyInput').value.trim()
    };
}

function pgComposePayload(draft) {
    let parsed = draft.payloadRaw ? JSON.parse(draft.payloadRaw) : null;

    if (draft.channel === 'EMAIL' && draft.subject) {
        if (parsed === null) {
            parsed = { subject: draft.subject };
        } else if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
            parsed = { ...parsed, subject: draft.subject };
        } else {
            throw new Error(
                'A subject can only be merged into a JSON object payload. Remove the array or primitive payload.'
            );
        }
    }

    return parsed === null ? '' : JSON.stringify(parsed);
}

function pgValidate(draft) {
    if (!draft.key) {
        return { message: 'Enter an API key before sending a request.', focus: 'apiKeyInput' };
    }

    try {
        const parsed = new URL(draft.url);
        if (!/^https?:$/.test(parsed.protocol)) {
            return { message: 'The base URL must use http:// or https://.', focus: 'baseUrlInput' };
        }
    } catch {
        return {
            message: 'The request URL is not valid. Check the base URL and endpoint.',
            focus: 'baseUrlInput'
        };
    }

    if (!draft.path) {
        return { message: 'Enter an endpoint path such as /api/v1/notifications.', focus: 'pathInput' };
    }

    if (draft.method === 'GET') {
        return null;
    }

    if (!draft.recipient) {
        return {
            message:
                draft.channel === 'EMAIL'
                    ? 'Enter a recipient email address.'
                    : 'Enter a recipient phone number.',
            focus: draft.channel === 'EMAIL' ? 'recipientEmail' : 'recipientPhone'
        };
    }

    if (!draft.message || !draft.message.trim()) {
        return { message: 'Enter a message body.', focus: 'messageInput' };
    }

    if (draft.message.length > 10000) {
        return { message: 'The message must be 10,000 characters or fewer.', focus: 'messageInput' };
    }

    if (!draft.idempotencyKey) {
        return {
            message: 'An Idempotency-Key is required. Generate one in Advanced options.',
            focus: 'idempotencyInput'
        };
    }

    if (draft.payloadRaw) {
        try {
            JSON.parse(draft.payloadRaw);
        } catch {
            return {
                message: 'The JSON payload is not valid JSON. Fix it or clear the field.',
                focus: 'payloadInput'
            };
        }
    }

    try {
        const payload = pgComposePayload(draft);
        if (payload.length > 100000) {
            return { message: 'The payload must be 100,000 characters or fewer.', focus: 'payloadInput' };
        }
    } catch (error) {
        return { message: error.message, focus: 'payloadInput' };
    }

    if (draft.scheduleEnabled) {
        if (!draft.scheduleAt) {
            return { message: 'Choose a scheduled date and time or turn the toggle off.', focus: 'scheduleAt' };
        }
        const when = new Date(draft.scheduleAt);
        if (Number.isNaN(when.getTime())) {
            return { message: 'The scheduled time is invalid.', focus: 'scheduleAt' };
        }
        if (when.getTime() < Date.now() - 60000) {
            return { message: 'The scheduled time must be in the future.', focus: 'scheduleAt' };
        }
    }

    return null;
}

function pgBuildRequest(draft) {
    const headers = { 'X-API-Key': draft.key };
    let body;

    if (draft.method === 'POST') {
        headers['Content-Type'] = 'application/json';
        headers['Idempotency-Key'] = draft.idempotencyKey;

        const payload = {
            type: draft.channel,
            recipient: draft.recipient,
            message: draft.message
        };

        const composed = pgComposePayload(draft);
        if (composed) {
            payload.payload = composed;
        }
        if (draft.scheduleEnabled && draft.scheduleAt) {
            payload.scheduledAt = new Date(draft.scheduleAt).toISOString();
        }

        body = JSON.stringify(payload);
    }

    return { headers, body };
}

/* ---------- send ---------- */

async function pgSend(event) {
    if (event) {
        event.preventDefault();
    }
    if (pgState.sending) {
        return;
    }

    const draft = pgReadForm();
    const problem = pgValidate(draft);
    if (problem) {
        pgFormAlert('error', problem.message);
        const field = document.getElementById(problem.focus);
        if (field) {
            field.focus();
            field.scrollIntoView({ block: 'center' });
        }
        return;
    }

    pgFormAlert(null);

    let request;
    try {
        request = pgBuildRequest(draft);
    } catch (error) {
        pgFormAlert('error', error.message);
        return;
    }

    pgState.sending = true;
    pgSetSending(true);
    pgRenderResponseLoading(draft);

    const startedAt = new Date();
    const t0 = performance.now();
    let entry;

    try {
        const response = await fetch(draft.url, {
            method: draft.method,
            headers: request.headers,
            body: request.body,
            cache: 'no-store',
            credentials: 'omit'
        });

        const text = await response.text();
        const duration = Math.round(performance.now() - t0);

        entry = {
            id: ++pgState.sequence,
            method: draft.method,
            url: draft.url,
            path: draft.path,
            channel: draft.method === 'POST' ? draft.channel : null,
            status: response.status,
            statusText: response.statusText,
            duration,
            at: startedAt,
            body: text,
            error: null,
            snapshot: pgSnapshot(draft)
        };
    } catch {
        entry = {
            id: ++pgState.sequence,
            method: draft.method,
            url: draft.url,
            path: draft.path,
            channel: draft.method === 'POST' ? draft.channel : null,
            status: null,
            statusText: '',
            duration: Math.round(performance.now() - t0),
            at: startedAt,
            body: '',
            error:
                'The request never reached the API. Check the base URL, confirm the backend is running, and check the browser console for a CORS error.',
            snapshot: pgSnapshot(draft)
        };
    } finally {
        pgState.sending = false;
        pgSetSending(false);
    }

    pgRenderResponse(entry);
    pgAddHistory(entry);
}

function pgSnapshot(draft) {
    // Never includes the API key.
    return {
        method: draft.method,
        base: draft.base,
        path: draft.path,
        channel: draft.channel,
        recipient: draft.recipient,
        subject: draft.subject,
        message: draft.message,
        payloadRaw: draft.payloadRaw,
        scheduleEnabled: draft.scheduleEnabled,
        scheduleAt: draft.scheduleAt,
        idempotencyKey: draft.idempotencyKey
    };
}

function pgSetSending(sending) {
    const button = document.getElementById('sendBtn');
    setButtonLoading(button, sending, 'Sending Request...');
    button.classList.toggle('is-sending', sending);
}

/* ---------- response ---------- */

function pgStatusKind(entry) {
    if (entry.error) {
        return { className: 'bad', label: 'Network error' };
    }
    const status = entry.status;
    if (status >= 200 && status < 300) {
        return { className: 'ok', label: `${status} ${entry.statusText || 'OK'}`.trim() };
    }
    if (status === 401 || status === 403) {
        return { className: 'bad', label: `${status} ${entry.statusText || 'Unauthorized'}`.trim() };
    }
    if (status >= 400 && status < 500) {
        return { className: 'warn', label: `${status} ${entry.statusText || 'Request error'}`.trim() };
    }
    if (status >= 500) {
        return { className: 'bad', label: `${status} ${entry.statusText || 'Server error'}`.trim() };
    }
    return { className: 'muted', label: String(status) };
}

function pgExplain(entry) {
    if (entry.error) {
        return entry.error;
    }
    if (!entry.body) {
        return '';
    }
    const data = pgParseJson(entry.body);
    const message = data && typeof data === 'object' && typeof data.message === 'string' ? data.message : '';

    if (entry.status === 202) {
        return 'Accepted for asynchronous processing. This confirms the notification was stored, not delivered.';
    }
    if (entry.status === 200 && data && data.duplicate === true) {
        return 'Idempotent replay: the original notification was returned and no new one was created.';
    }
    if (entry.status === 200) {
        return 'The API returned the requested data.';
    }
    if (entry.status === 401) {
        return message || 'Authentication failed: the key is missing, malformed, or revoked.';
    }
    if (entry.status === 409) {
        return message || 'This Idempotency-Key was already used with a different body in the last 5 minutes.';
    }
    if (entry.status === 400) {
        return message || 'The request was rejected by validation. See the response body.';
    }
    if (entry.status === 404) {
        return message || 'No notification with that id exists for this account.';
    }
    if (entry.status >= 500) {
        return message || 'The server failed to process the request. Retry with the same Idempotency-Key.';
    }
    return message;
}

function pgRenderResponseLoading(draft) {
    const body = document.getElementById('responseBody');
    document.getElementById('respMeta').hidden = true;
    document.getElementById('copyResponseBtn').disabled = true;
    pgState.lastCopyable = '';
    body.setAttribute('aria-busy', 'true');
    body.innerHTML = `
        <div class="resp-loading">
            <span class="spinner" aria-hidden="true"></span>
            <span>Waiting for <code class="inline">${escapeHtml(draft.method)}</code>
                <code class="inline">${escapeHtml(draft.path)}</code>…</span>
        </div>
        <div class="skeleton" style="height:132px;margin-top:14px"></div>`;
    document.getElementById('responseLive').textContent = `Sending ${draft.method} request.`;
}

function pgRenderResponse(entry) {
    const body = document.getElementById('responseBody');
    const meta = document.getElementById('respMeta');
    const statusPill = document.getElementById('respStatus');
    const kind = pgStatusKind(entry);

    body.setAttribute('aria-busy', 'false');
    meta.hidden = false;
    statusPill.className = `status-pill ${kind.className}`;
    statusPill.textContent = kind.label;
    document.getElementById('respDuration').textContent = entry.error ? '—' : `${entry.duration} ms`;
    document.getElementById('respTime').textContent = formatDateTime(entry.at);

    const explanation = pgExplain(entry);
    const explanationHtml = explanation
        ? `<p class="resp-explain ${kind.className}">${escapeHtml(explanation)}</p>`
        : '';

    let bodyHtml;
    let copyable = entry.body || '';

    if (entry.error) {
        bodyHtml = `<div class="resp-error">
            <strong>Network error</strong>
            <p>${escapeHtml(entry.error)}</p>
            <button class="btn btn-secondary btn-sm" type="button" id="respRetryBtn">Try again</button>
        </div>`;
    } else if (!entry.body) {
        bodyHtml = '<div class="resp-empty-body">The response body was empty.</div>';
    } else {
        const parsed = pgParseJson(entry.body);
        if (parsed !== null) {
            copyable = JSON.stringify(parsed, null, 2);
            bodyHtml = `<pre class="json-view">${pgHighlightJson(copyable)}</pre>`;
        } else {
            bodyHtml = `<pre class="plain-view">${escapeHtml(entry.body)}</pre>`;
        }
    }

    body.innerHTML = `${explanationHtml}${bodyHtml}`;

    const copyButton = document.getElementById('copyResponseBtn');
    copyButton.disabled = !copyable;
    pgState.lastCopyable = copyable;

    const retry = document.getElementById('respRetryBtn');
    if (retry) {
        retry.addEventListener('click', () => document.getElementById('sendBtn').click());
    }

    document.getElementById('responseLive').textContent = entry.error
        ? 'Network error. The request did not reach the API.'
        : `Response ${entry.status} ${entry.statusText || ''} in ${entry.duration} milliseconds.`.trim();
}

/* ---------- json helpers ---------- */

function pgParseJson(text) {
    if (!text) {
        return null;
    }
    try {
        return JSON.parse(text);
    } catch {
        return null;
    }
}

function pgHighlightJson(json) {
    const pattern =
        /("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g;
    let result = '';
    let lastIndex = 0;
    let match;

    while ((match = pattern.exec(json)) !== null) {
        result += escapeHtml(json.slice(lastIndex, match.index));

        if (match[1]) {
            const className = match[2] ? 'json-key' : 'json-string';
            result += `<span class="${className}">${escapeHtml(match[1])}</span>`;
            if (match[2]) {
                result += escapeHtml(match[2]);
            }
        } else if (match[3]) {
            const className = match[3] === 'null' ? 'json-null' : 'json-bool';
            result += `<span class="${className}">${match[3]}</span>`;
        } else {
            result += `<span class="json-num">${match[4]}</span>`;
        }

        lastIndex = pattern.lastIndex;
    }

    result += escapeHtml(json.slice(lastIndex));
    return result;
}

/* ---------- history ---------- */

function pgAddHistory(entry) {
    pgState.history.unshift(entry);
    if (pgState.history.length > PG_HISTORY_LIMIT) {
        pgState.history.length = PG_HISTORY_LIMIT;
    }
    pgState.selectedHistoryId = entry.id;
    pgRenderHistory();
}

function pgRenderHistory() {
    const tbody = document.getElementById('historyBody');
    const summary = document.getElementById('historySummary');
    const clearButton = document.getElementById('clearHistoryBtn');

    clearButton.disabled = pgState.history.length === 0;
    summary.textContent = pgState.history.length
        ? `${pgState.history.length} request${pgState.history.length === 1 ? '' : 's'}`
        : 'No requests yet';

    if (pgState.history.length === 0) {
        tbody.innerHTML = `<tr class="empty-row"><td colspan="5">
            <div class="empty">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <circle cx="12" cy="12" r="8.5" stroke="currentColor" stroke-width="1.4"/>
                    <path d="M12 7.5V12l3 2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
                </svg>
                <h3>No requests in this session</h3>
                <p>Every request you send appears here. Selecting a row restores its configuration and response —
                    API keys are never stored, so you will re-enter the secret.</p>
            </div>
        </td></tr>`;
        return;
    }

    tbody.innerHTML = pgState.history
        .map((entry) => {
            const kind = pgStatusKind(entry);
            const selected = entry.id === pgState.selectedHistoryId;
            const channel = entry.channel ? pgChannelLabel(entry.channel) : '—';
            const methodClass = entry.method === 'GET' ? 'get' : 'post';
            return `<tr data-history-id="${entry.id}" tabindex="0"${
                selected ? ' class="is-selected"' : ''
            } aria-label="Load ${escapeHtml(entry.method)} ${escapeHtml(entry.path)}">
                <td><span class="method ${methodClass}">${escapeHtml(entry.method)}</span></td>
                <td class="mono">${escapeHtml(entry.path || entry.url)}</td>
                <td>${escapeHtml(channel)}</td>
                <td><span class="status-pill ${kind.className}">${escapeHtml(kind.label)}</span></td>
                <td>${formatDateTime(entry.at)}
                    <span class="cell-sub">${formatRelative(entry.at)}</span>
                </td>
            </tr>`;
        })
        .join('');
}

function pgChannelLabel(channel) {
    if (!channel) {
        return '—';
    }
    return channel === 'EMAIL' ? 'Email' : channel === 'SMS' ? 'SMS' : channel;
}

function pgRestoreHistory(id) {
    const entry = pgState.history.find((item) => item.id === id);
    if (!entry) {
        return;
    }

    const snapshot = entry.snapshot;
    document.getElementById('methodSelect').value = snapshot.method;
    document.getElementById('baseUrlInput').value = snapshot.base;
    document.getElementById('pathInput').value = snapshot.path;

    const radio = document.querySelector(
        `input[name="channel"][value="${snapshot.channel === 'SMS' ? 'SMS' : 'EMAIL'}"]`
    );
    if (radio) {
        radio.checked = true;
    }

    document.getElementById('recipientEmail').value =
        snapshot.channel === 'EMAIL' ? snapshot.recipient : '';
    document.getElementById('recipientPhone').value =
        snapshot.channel === 'SMS' ? snapshot.recipient : '';
    document.getElementById('subjectInput').value = snapshot.subject || '';
    document.getElementById('messageInput').value = snapshot.message || '';
    document.getElementById('payloadInput').value = snapshot.payloadRaw || '';
    document.getElementById('idempotencyInput').value = snapshot.idempotencyKey || '';

    document.getElementById('scheduleToggle').checked = Boolean(snapshot.scheduleEnabled);
    document.getElementById('scheduleField').hidden = !snapshot.scheduleEnabled;
    if (snapshot.scheduleAt) {
        document.getElementById('scheduleAt').value = snapshot.scheduleAt;
    }

    pgUpdateChannelUI();
    pgUpdateMethodUI();
    pgUpdateUrlPreview();
    pgUpdateMessageCount();
    pgUpdatePayloadStatus();
    pgUpdateAdvancedSummary();
    pgSetAdvanced(Boolean(snapshot.payloadRaw) || Boolean(snapshot.scheduleEnabled));

    pgState.selectedHistoryId = id;
    pgRenderHistory();
    pgRenderResponse(entry);

    pgFormAlert(
        'info',
        'Loaded a previous request. API keys are never stored, so paste your key before resending.'
    );

    document.getElementById('requestPanel').scrollIntoView({ block: 'start' });
}

/* ---------- events ---------- */

function pgBindEvents() {
    document.getElementById('pgForm').addEventListener('submit', pgSend);

    document.getElementById('methodSelect').addEventListener('change', () => {
        pgUpdateMethodUI();
        pgUpdateUrlPreview();
    });

    ['baseUrlInput', 'pathInput'].forEach((id) => {
        document.getElementById(id).addEventListener('input', pgUpdateUrlPreview);
    });

    document.getElementById('copyUrlBtn').addEventListener('click', (event) => {
        copyWithFeedback(event.currentTarget, pgCurrentUrl(), 'Copied');
    });

    document.getElementById('copyHeaderBtn').addEventListener('click', (event) => {
        copyWithFeedback(event.currentTarget, 'X-API-Key: YOUR_API_KEY', 'Copied');
    });

    document.getElementById('toggleKeyBtn').addEventListener('click', (event) => {
        const input = document.getElementById('apiKeyInput');
        const button = event.currentTarget;
        const showing = input.type === 'text';
        input.type = showing ? 'password' : 'text';
        button.textContent = showing ? 'Show' : 'Hide';
        button.setAttribute('aria-pressed', String(!showing));
        input.focus();
    });

    document.getElementById('savedKeySelect').addEventListener('change', (event) => {
        const option = event.target.selectedOptions[0];
        if (!event.target.value || !pgState.savedKey) {
            document.getElementById('savedKeyHint').textContent =
                'Secrets cannot be retrieved after creation.';
            return;
        }

        const status = String(pgState.savedKey.status || '').toUpperCase();
        document.getElementById('savedKeyHint').textContent =
            status === 'ACTIVE'
                ? `Paste the secret for “${pgState.savedKey.name || 'Default key'}” (${pgState.savedKey.prefix}…). It is never re-shown.`
                : `“${pgState.savedKey.name || 'Default key'}” is revoked — requests using it return 401.`;
        if (option) {
            document.getElementById('apiKeyInput').focus();
        }
    });

    document.querySelectorAll('input[name="channel"]').forEach((radio) => {
        radio.addEventListener('change', pgUpdateChannelUI);
    });

    document.getElementById('messageInput').addEventListener('input', pgUpdateMessageCount);

    document.getElementById('advancedToggle').addEventListener('click', (event) => {
        const open = event.currentTarget.getAttribute('aria-expanded') !== 'true';
        pgSetAdvanced(open);
    });

    document.getElementById('generateIdemBtn').addEventListener('click', () => {
        document.getElementById('idempotencyInput').value = pgNewIdempotencyKey();
        pgFormAlert(null);
    });

    document.getElementById('scheduleToggle').addEventListener('change', (event) => {
        const enabled = event.target.checked;
        document.getElementById('scheduleField').hidden = !enabled;
        if (enabled) {
            const input = document.getElementById('scheduleAt');
            if (!input.value) {
                const soon = new Date(Date.now() + 5 * 60000);
                soon.setSeconds(0, 0);
                input.value = pgToLocalInputValue(soon);
            }
            input.focus();
        }
        pgUpdateAdvancedSummary();
    });

    document.getElementById('scheduleAt').addEventListener('change', pgUpdateAdvancedSummary);
    document.getElementById('payloadInput').addEventListener('input', () => {
        pgUpdatePayloadStatus();
        pgUpdateAdvancedSummary();
    });

    document.getElementById('copyResponseBtn').addEventListener('click', (event) => {
        if (!pgState.lastCopyable) {
            return;
        }
        copyWithFeedback(event.currentTarget, pgState.lastCopyable, 'Copied');
    });

    document.getElementById('clearHistoryBtn').addEventListener('click', () => {
        pgState.history = [];
        pgState.selectedHistoryId = null;
        pgRenderHistory();
    });

    document.getElementById('clearFormBtn').addEventListener('click', () => {
        document.getElementById('recipientEmail').value = '';
        document.getElementById('recipientPhone').value = '';
        document.getElementById('subjectInput').value = '';
        document.getElementById('messageInput').value = '';
        document.getElementById('payloadInput').value = '';
        document.getElementById('scheduleToggle').checked = false;
        document.getElementById('scheduleField').hidden = true;
        document.getElementById('idempotencyInput').value = pgNewIdempotencyKey();
        pgUpdateMessageCount();
        pgUpdatePayloadStatus();
        pgUpdateAdvancedSummary();
        pgFormAlert(null);
    });

    const tbody = document.getElementById('historyBody');
    tbody.addEventListener('click', (event) => {
        const row = event.target.closest('tr[data-history-id]');
        if (row) {
            pgRestoreHistory(Number(row.dataset.historyId));
        }
    });
    tbody.addEventListener('keydown', (event) => {
        const row = event.target.closest('tr[data-history-id]');
        if (row && (event.key === 'Enter' || event.key === ' ')) {
            event.preventDefault();
            pgRestoreHistory(Number(row.dataset.historyId));
        }
    });
}
