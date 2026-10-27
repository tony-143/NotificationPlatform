/* Shared API client + presentation helpers.
   Loaded after js/config.js and js/auth.js (uses authFetch for CSRF-safe writes). */

class ApiError extends Error {
    constructor(message, { status = 0, path = null } = {}) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.path = path;
    }

    get isNetwork() {
        return this.status === 0;
    }

    get isAuth() {
        return this.status === 401;
    }
}

async function apiRequest(path, options = {}) {
    let response;
    try {
        response = await authFetch(`${API_BASE_URL}${path}`, options);
    } catch (error) {
        throw new ApiError(
            'Cannot reach the API. Check that the backend is running, then retry.',
            { status: 0 }
        );
    }

    if (response.status === 204) {
        return null;
    }

    const raw = await response.text();
    let body = null;
    if (raw) {
        try {
            body = JSON.parse(raw);
        } catch {
            body = raw;
        }
    }

    if (!response.ok) {
        const message =
            body && typeof body === 'object' && typeof body.message === 'string'
                ? body.message
                : `Request failed with status ${response.status}`;
        throw new ApiError(message, { status: response.status, path });
    }

    return body;
}

const api = {
    get: (path) => apiRequest(path),
    post: (path, body) =>
        apiRequest(path, {
            method: 'POST',
            headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body)
        }),
    put: (path, body) =>
        apiRequest(path, {
            method: 'PUT',
            headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body)
        })
};

/* ---------- formatting ---------- */

function parseInstant(value) {
    if (!value) {
        return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
}

function formatCount(value) {
    if (value === null || value === undefined) {
        return '—';
    }
    return Number(value).toLocaleString();
}

function formatDateTime(value) {
    const date = parseInstant(value);
    if (!date) {
        return '—';
    }
    return date.toLocaleString(undefined, {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function formatDate(value) {
    const date = parseInstant(value);
    if (!date) {
        return '—';
    }
    return date.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
}

function formatRelative(value) {
    const date = parseInstant(value);
    if (!date) {
        return 'Never';
    }
    const diff = Date.now() - date.getTime();
    const future = diff < 0;
    const seconds = Math.abs(diff) / 1000;

    if (seconds < 45) {
        return future ? 'in a moment' : 'just now';
    }
    const units = [
        ['year', 31536000],
        ['month', 2592000],
        ['day', 86400],
        ['hour', 3600],
        ['minute', 60]
    ];
    for (const [unit, size] of units) {
        if (seconds >= size) {
            const amount = Math.floor(seconds / size);
            const label = `${amount} ${unit}${amount === 1 ? '' : 's'}`;
            return future ? `in ${label}` : `${label} ago`;
        }
    }
    return 'just now';
}

/* ---------- domain labels ---------- */

const STATUS_LABELS = {
    PENDING: 'Pending',
    PROCESSING: 'Processing',
    RETRYING: 'Retrying',
    SENT: 'Provider accepted',
    FAILED: 'Failed',
    CLOSED: 'Closed',
    PROVIDER_NOT_CONFIGURED: 'Provider not configured'
};

function statusLabel(status) {
    return STATUS_LABELS[status] || status || 'Unknown';
}

function statusBadge(status) {
    if (!status) {
        return '<span class="badge badge-neutral">Unknown</span>';
    }
    const key = String(status).toLowerCase();
    return `<span class="badge badge-${key}">${statusLabel(status)}</span>`;
}

function channelLabel(channel) {
    if (!channel) {
        return '—';
    }
    const key = String(channel).toLowerCase();
    if (key === 'email') {
        return 'Email';
    }
    if (key === 'sms') {
        return 'SMS';
    }
    return key.charAt(0).toUpperCase() + key.slice(1);
}

/* ---------- misc UI ---------- */

async function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        return true;
    }
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.append(area);
    area.select();
    const copied = document.execCommand('copy');
    area.remove();
    return copied;
}

async function copyWithFeedback(button, text, label = 'Copied') {
    const original = button.textContent;
    try {
        const ok = await copyText(text);
        button.textContent = ok ? label : 'Copy failed';
    } catch {
        button.textContent = 'Copy failed';
    }
    window.setTimeout(() => {
        button.textContent = original;
    }, 1600);
}

function startOfDayIso(value) {
    if (!value) {
        return null;
    }
    const date = new Date(`${value}T00:00:00`);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function endOfDayIso(value) {
    if (!value) {
        return null;
    }
    const date = new Date(`${value}T23:59:59.999`);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
