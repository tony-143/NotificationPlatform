const API_KEY_STORAGE = STORAGE_KEYS.apiKey;

document.addEventListener('DOMContentLoaded', async () => {
    setPageLoading(true);
    const authenticated = await requireAuth('index.html?expired=1');
    if (!authenticated) {
        return;
    }

    document.getElementById('apiKeyInput').value = localStorage.getItem(API_KEY_STORAGE) || '';
    setPageLoading(false);
    loadNotifications();
});

document.getElementById('saveApiKeyBtn').addEventListener('click', () => {
    const input = document.getElementById('apiKeyInput');
    const apiKey = input.value.trim();

    if (!apiKey) {
        showApiKeyAlert('error', 'Enter an API key first.');
        input.focus();
        return;
    }

    localStorage.setItem(API_KEY_STORAGE, apiKey);
    showApiKeyAlert('success', 'API key saved in this browser.');
    loadNotifications();
});

function showApiKeyAlert(variant, message) {
    showAlert(document.getElementById('apiKeyAlert'), variant, message);
}

function getStoredUser() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEYS.user) || 'null');
    } catch {
        return null;
    }
}

function getActiveApiKey() {
    const input = document.getElementById('apiKeyInput');
    const typedKey = (input?.value || '').trim();
    if (typedKey) {
        return typedKey;
    }
    return (localStorage.getItem(API_KEY_STORAGE) || '').trim();
}

function appendVerifyLink(alertElement) {
    const user = getStoredUser();
    const email = user?.email || localStorage.getItem(STORAGE_KEYS.pendingEmail) || '';
    const link = document.createElement('a');
    link.className = 'link';
    link.href = `verify-email.html${email ? `?email=${encodeURIComponent(email)}` : ''}`;
    link.textContent = 'Verify your email →';
    alertElement.append(' ', link);
}

// Generate / rotate an API key (requires a valid session cookie + CSRF token).
async function generateApiKey(button) {
    const alertElement = document.getElementById('apiKeyAlert');
    hideAlert(alertElement);
    setButtonLoading(button, true, 'Generating…');

    try {
        const response = await authFetch(`${API_BASE_URL}/api/keys`, { method: 'POST' });
        const data = await readJson(response);

        if (response.ok && data?.apiKey) {
            document.getElementById('apiKeyValue').textContent = data.apiKey;
            document.getElementById('apiKeyPrefix').textContent = data.prefix || '—';
            document.getElementById('apiKeyCreated').textContent = formatDate(data.createdAt);
            document.getElementById('apiKeyResult').style.display = 'flex';

            document.getElementById('apiKeyInput').value = data.apiKey;
            localStorage.setItem(API_KEY_STORAGE, data.apiKey);

            showApiKeyAlert('success', data.message || 'API key generated and saved in this browser.');
            loadNotifications();
            return;
        }

        if (response.status === 401) {
            window.location.replace('index.html?expired=1');
            return;
        }

        if (response.status === 403) {
            showApiKeyAlert('error', data?.message || 'Verify your email before generating an API key.');
            appendVerifyLink(alertElement);
            return;
        }

        showApiKeyAlert('error', data?.message || 'Could not generate an API key.');
    } catch (error) {
        showApiKeyAlert('error', 'Unable to reach the server. Please try again.');
    } finally {
        setButtonLoading(button, false);
    }
}

// Copy API Key
async function copyApiKey() {
    const apiKeyElement = document.getElementById('apiKeyValue');
    const apiKey = apiKeyElement.textContent;
    if (!apiKey) {
        return;
    }

    const btn = document.getElementById('copyBtn');
    const originalText = btn.innerHTML;

    try {
        await navigator.clipboard.writeText(apiKey);
        btn.innerHTML = '<svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M14.25 6.75L7.5 13.5L3.75 9.75" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg> Copied!';
    } catch {
        const range = document.createRange();
        range.selectNodeContents(apiKeyElement);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        btn.innerHTML = '<svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M14.25 6.75L7.5 13.5L3.75 9.75" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg> Selected';
    }

    setTimeout(() => {
        btn.innerHTML = originalText;
    }, 2000);
}

// Generate random idempotency key
function generateIdempotencyKey() {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let result = '';
    for (let i = 0; i < 32; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    document.getElementById('idempotencyKey').value = result;
    return result;
}

// Send notification
document.getElementById('notificationForm').addEventListener('submit', async (e) => {
    e.preventDefault();

    const form = e.currentTarget;
    const sendButton = document.getElementById('sendNotificationBtn');
    const resultDiv = document.getElementById('notificationResult');

    const type = document.getElementById('notificationType').value;
    const recipient = document.getElementById('recipient').value.trim();
    const message = document.getElementById('message').value;
    const payload = document.getElementById('payload').value;

    let idempotencyKey = document.getElementById('idempotencyKey').value.trim();
    if (!idempotencyKey) {
        idempotencyKey = generateIdempotencyKey();
    }

    resultDiv.style.display = 'none';

    // Notification endpoints authenticate with X-API-Key, not the login cookie.
    const apiKey = getActiveApiKey();
    if (!apiKey) {
        renderNotificationResult(
            resultDiv,
            'error',
            errorHtml('API key required', 'Generate a key or paste one in the API Key Management card above.')
        );
        return;
    }

    let succeeded = false;
    setFormBusy(form, true);
    setButtonLoading(sendButton, true, 'Sending…');

    try {
        const response = await fetch(`${API_BASE_URL}/api/notifications`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': idempotencyKey,
                'X-API-Key': apiKey
            },
            credentials: 'include',
            body: JSON.stringify({
                type,
                recipient,
                message,
                payload: payload || null
            })
        });

        const data = await readJson(response);

        if (response.ok && data?.notification) {
            const notification = data.notification;
            renderNotificationResult(
                resultDiv,
                'success',
                `
                    <div style="display: flex; align-items: center; gap: 0.5rem;">
                        <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                            <circle cx="10" cy="10" r="7.5" stroke="currentColor" stroke-width="1.5"/>
                            <path d="M6.667 10L8.75 12.083L13.333 7.917" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                        <strong>Notification ${data.duplicate ? 'already submitted' : 'submitted'} successfully!</strong>
                    </div>
                    <div style="margin-top: 0.5rem; font-size: 0.875rem;">
                        Status: <strong>${escapeHtml(notification.status)}</strong> | ID: <strong>${escapeHtml(notification.id)}</strong>
                    </div>
                `
            );
            succeeded = true;
        } else if (response.status === 401) {
            renderNotificationResult(
                resultDiv,
                'error',
                errorHtml('Invalid API key', 'Generate a new key or paste a valid one in the API Key Management card.')
            );
        } else {
            renderNotificationResult(
                resultDiv,
                'error',
                errorHtml('Error', data?.message || 'Failed to send notification')
            );
        }
    } catch (error) {
        renderNotificationResult(
            resultDiv,
            'error',
            errorHtml('Network error', 'Please check your connection and try again.')
        );
    } finally {
        setButtonLoading(sendButton, false);
        setFormBusy(form, false);
        if (succeeded) {
            form.reset();
            generateIdempotencyKey();
        }
    }
});

function renderNotificationResult(resultDiv, variant, html) {
    resultDiv.innerHTML = html;
    if (variant === 'success') {
        resultDiv.style.background = 'var(--success-light)';
        resultDiv.style.color = 'var(--success)';
        resultDiv.style.borderColor = 'rgba(16, 185, 129, 0.3)';
    } else {
        resultDiv.style.background = 'var(--error-light)';
        resultDiv.style.color = 'var(--error)';
        resultDiv.style.borderColor = 'rgba(239, 68, 68, 0.3)';
    }
    resultDiv.style.display = 'block';
}

function errorHtml(title, message) {
    return `
        <div style="display: flex; align-items: center; gap: 0.5rem;">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                <circle cx="10" cy="10" r="7.5" stroke="currentColor" stroke-width="1.5"/>
                <path d="M10 6.667v3.333M10 13.333h.008" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            <strong>${escapeHtml(title)}</strong>
        </div>
        <div style="margin-top: 0.5rem; font-size: 0.875rem;">
            ${escapeHtml(message)}
        </div>
    `;
}

// Load notifications
async function loadNotifications(button) {
    const listDiv = document.getElementById('notificationsList');
    const apiKey = getActiveApiKey();

    if (!apiKey) {
        listDiv.innerHTML = '<p class="empty-state">Generate an API key or paste one above to load notifications.</p>';
        return;
    }

    if (button) {
        setButtonLoading(button, true, 'Refreshing…');
    }
    listDiv.setAttribute('aria-busy', 'true');
    renderNotificationSkeletons(listDiv);

    try {
        const response = await fetch(`${API_BASE_URL}/api/notifications`, {
            headers: {
                'X-API-Key': apiKey
            },
            credentials: 'include',
            cache: 'no-store'
        });

        const data = await readJson(response);

        if (response.ok && Array.isArray(data)) {
            if (data.length === 0) {
                listDiv.innerHTML = '<p class="empty-state">No notifications yet</p>';
            } else {
                listDiv.innerHTML = data.map(renderNotificationItem).join('');
            }
        } else if (response.status === 401) {
            listDiv.innerHTML = '<p class="empty-state">Invalid or expired API key. Generate a new key above.</p>';
        } else {
            listDiv.innerHTML = `<p class="empty-state">${escapeHtml(data?.message || 'Failed to load notifications')}</p>`;
        }
    } catch (error) {
        listDiv.innerHTML = '<p class="empty-state">Unable to reach the server. Please try again.</p>';
    } finally {
        listDiv.removeAttribute('aria-busy');
        if (button) {
            setButtonLoading(button, false);
        }
    }
}

function renderNotificationSkeletons(container) {
    container.innerHTML = Array.from({ length: 3 }).map(() => `
        <div class="notification-item skeleton-item" aria-hidden="true">
            <div class="skeleton-line skeleton-line-sm"></div>
            <div class="skeleton-line"></div>
            <div class="skeleton-line skeleton-line-lg"></div>
        </div>
    `).join('');
}

function renderNotificationItem(notification) {
    const statusClass = getStatusClass(notification.status);
    return `
        <div class="notification-item">
            <div class="notification-item-header">
                <div style="display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap;">
                    <span class="notification-type-badge">${escapeHtml(notification.type)}</span>
                    <span class="notification-status ${statusClass}">${escapeHtml(notification.status)}</span>
                </div>
                <span style="font-size: 0.75rem; color: var(--text-tertiary);">ID: ${escapeHtml(notification.id)}</span>
            </div>
            <div class="notification-recipient">
                <strong>To:</strong> ${escapeHtml(notification.recipient)}
            </div>
            <div class="notification-message">
                ${escapeHtml(notification.message)}
            </div>
            ${notification.statusMessage ? `<div style="font-size: 0.8125rem; color: var(--text-tertiary);"><strong>Status:</strong> ${escapeHtml(notification.statusMessage)}</div>` : ''}
            <div class="notification-meta">
                <span>Created: ${formatDate(notification.createdAt)}</span>
                <span>Retries: ${escapeHtml(notification.retryCount)}</span>
                ${notification.scheduledAt ? `<span>Scheduled: ${formatDate(notification.scheduledAt)}</span>` : ''}
            </div>
        </div>
    `;
}

function getStatusClass(status) {
    switch (String(status || '').toUpperCase()) {
        case 'SENT':
            return 'status-sent';
        case 'PENDING':
        case 'PROCESSING':
            return 'status-processing';
        case 'RETRYING':
            return 'status-retrying';
        case 'FAILED':
        case 'PROVIDER_NOT_CONFIGURED':
        case 'CLOSED':
            return 'status-failed';
        default:
            return '';
    }
}

function formatDate(value) {
    if (!value) {
        return '—';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
}
