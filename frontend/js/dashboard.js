// Generate API Key
async function generateApiKey() {
    try {
        const response = await fetch(`${API_BASE_URL}/api/keys`, {
            method: 'POST',
            credentials: 'include'
        });
        
        if (response.ok) {
            const data = await response.json();
            document.getElementById('apiKeyValue').textContent = data.apiKey;
            document.getElementById('apiKeyPrefix').textContent = data.prefix;
            document.getElementById('apiKeyCreated').textContent = new Date(data.createdAt).toLocaleString();
            document.getElementById('apiKeyResult').style.display = 'flex';
        } else {
            const errorData = await response.json();
            alert('Error: ' + (errorData.message || 'Failed to generate API key'));
        }
    } catch (error) {
        alert('Network error. Please try again.');
    }
}

// Copy API Key
function copyApiKey() {
    const apiKey = document.getElementById('apiKeyValue').textContent;
    navigator.clipboard.writeText(apiKey).then(() => {
        const btn = document.getElementById('copyBtn');
        const originalText = btn.innerHTML;
        btn.innerHTML = '<svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M14.25 6.75L7.5 13.5L3.75 9.75" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg> Copied!';
        setTimeout(() => {
            btn.innerHTML = originalText;
        }, 2000);
    });
}

// Generate random idempotency key
function generateIdempotencyKey() {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let result = '';
    for (let i = 0; i < 32; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    document.getElementById('idempotencyKey').value = result;
}

// Send notification
document.getElementById('notificationForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const type = document.getElementById('notificationType').value;
    const recipient = document.getElementById('recipient').value;
    const message = document.getElementById('message').value;
    const payload = document.getElementById('payload').value;
    const idempotencyKey = document.getElementById('idempotencyKey').value;
    const resultDiv = document.getElementById('notificationResult');
    
    resultDiv.style.display = 'none';
    
    try {
        const response = await fetch(`${API_BASE_URL}/api/notifications`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': idempotencyKey
            },
            credentials: 'include',
            body: JSON.stringify({ 
                type, 
                recipient, 
                message, 
                payload: payload || null 
            })
        });
        
        const data = await response.json();
        
        if (response.ok) {
            resultDiv.innerHTML = `
                <div style="display: flex; align-items: center; gap: 0.5rem;">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                        <circle cx="10" cy="10" r="7.5" stroke="currentColor" stroke-width="1.5"/>
                        <path d="M6.667 10L8.75 12.083L13.333 7.917" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    <strong>Notification ${data.duplicate ? 'already submitted' : 'submitted'} successfully!</strong>
                </div>
                <div style="margin-top: 0.5rem; font-size: 0.875rem;">
                    Status: <strong>${data.notification.status}</strong> | ID: <strong>${data.notification.id}</strong>
                </div>
            `;
            resultDiv.style.background = 'var(--success-light)';
            resultDiv.style.color = 'var(--success)';
            resultDiv.style.borderColor = 'rgba(16, 185, 129, 0.3)';
            resultDiv.style.display = 'block';
            document.getElementById('notificationForm').reset();
        } else {
            resultDiv.innerHTML = `
                <div style="display: flex; align-items: center; gap: 0.5rem;">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                        <circle cx="10" cy="10" r="7.5" stroke="currentColor" stroke-width="1.5"/>
                        <path d="M10 6.667v3.333M10 13.333h.008" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                    </svg>
                    <strong>Error</strong>
                </div>
                <div style="margin-top: 0.5rem; font-size: 0.875rem;">
                    ${data.message || 'Failed to send notification'}
                </div>
            `;
            resultDiv.style.background = 'var(--error-light)';
            resultDiv.style.color = 'var(--error)';
            resultDiv.style.borderColor = 'rgba(239, 68, 68, 0.3)';
            resultDiv.style.display = 'block';
        }
    } catch (error) {
        resultDiv.innerHTML = `
            <div style="display: flex; align-items: center; gap: 0.5rem;">
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                    <circle cx="10" cy="10" r="7.5" stroke="currentColor" stroke-width="1.5"/>
                    <path d="M10 6.667v3.333M10 13.333h.008" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                </svg>
                <strong>Network Error</strong>
            </div>
            <div style="margin-top: 0.5rem; font-size: 0.875rem;">
                Please check your connection and try again.
            </div>
        `;
        resultDiv.style.background = 'var(--error-light)';
        resultDiv.style.color = 'var(--error)';
        resultDiv.style.borderColor = 'rgba(239, 68, 68, 0.3)';
        resultDiv.style.display = 'block';
    }
});

// Load notifications
async function loadNotifications() {
    const listDiv = document.getElementById('notificationsList');
    listDiv.innerHTML = '<p class="empty-state">Loading...</p>';
    
    try {
        const response = await fetch(`${API_BASE_URL}/api/notifications`, {
            credentials: 'include'
        });
        
        if (response.ok) {
            const notifications = await response.json();
            
            if (notifications.length === 0) {
                listDiv.innerHTML = '<p class="empty-state">No notifications yet</p>';
                return;
            }
            
            listDiv.innerHTML = notifications.map(notification => {
                const statusClass = getStatusClass(notification.status);
                const date = new Date(notification.createdAt).toLocaleString();
                
                return `
                    <div class="notification-item">
                        <div class="notification-item-header">
                            <div style="display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap;">
                                <span class="notification-type-badge">${notification.type}</span>
                                <span class="notification-status ${statusClass}">${notification.status}</span>
                            </div>
                            <span style="font-size: 0.75rem; color: var(--text-tertiary);">ID: ${notification.id}</span>
                        </div>
                        <div class="notification-recipient">
                            <strong>To:</strong> ${escapeHtml(notification.recipient)}
                        </div>
                        <div class="notification-message">
                            ${escapeHtml(notification.message)}
                        </div>
                        ${notification.statusMessage ? `<div style="font-size: 0.8125rem; color: var(--text-tertiary);"><strong>Status:</strong> ${escapeHtml(notification.statusMessage)}</div>` : ''}
                        <div class="notification-meta">
                            <span>Created: ${date}</span>
                            <span>Retries: ${notification.retryCount}</span>
                            ${notification.scheduledAt ? `<span>Scheduled: ${new Date(notification.scheduledAt).toLocaleString()}</span>` : ''}
                        </div>
                    </div>
                `;
            }).join('');
        } else {
            listDiv.innerHTML = '<p class="empty-state">Failed to load notifications</p>';
        }
    } catch (error) {
        listDiv.innerHTML = '<p class="empty-state">Network error</p>';
    }
}

function getStatusClass(status) {
    switch(status) {
        case 'SENT': return 'status-sent';
        case 'PENDING': 
        case 'PROCESSING': return 'status-processing';
        case 'RETRYING': return 'status-retrying';
        case 'FAILED':
        case 'PROVIDER_NOT_CONFIGURED':
        case 'CLOSED': return 'status-failed';
        default: return '';
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Auto-load notifications on page load
document.addEventListener('DOMContentLoaded', () => {
    loadNotifications();
});
